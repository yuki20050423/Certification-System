package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.entity.File;
import com.swjtu.certification.entity.Task;
import com.swjtu.certification.mapper.FileMapper;
import com.swjtu.certification.mapper.TaskMapper;
import com.swjtu.certification.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileService {
    private final FileMapper fileMapper;
    private final TaskMapper taskMapper;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.max-size:52428800}")
    private Long maxFileSize;

    /**
     * 上传单个文件
     */
    @Transactional
    public FileVO uploadFile(Long taskId, Long userId, MultipartFile multipartFile) throws IOException {
        // 1. 校验任务状态
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getTeacherId().equals(userId)) {
            throw new RuntimeException("无权上传该任务的文件");
        }
        if (LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new RuntimeException("任务已截止，无法上传文件");
        }
        if (!"PENDING_UPLOAD".equals(task.getStatus()) && !"NEED_REVISION".equals(task.getStatus())) {
            throw new RuntimeException("当前任务状态不允许上传文件");
        }

        // 2. 文件基础校验（已在Controller层做，此处二次确认）
        if (multipartFile.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        if (multipartFile.getSize() > maxFileSize) {
            throw new RuntimeException("文件大小不能超过" + (maxFileSize / 1024 / 1024) + "MB");
        }
        String originalFilename = multipartFile.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = List.of("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "gif");
        if (!allowedExtensions.contains(extension)) {
            throw new RuntimeException("不支持的文件类型，仅支持：PDF、DOC、EXCEL、图片");
        }

        // 3. 决定存储子目录
        String subDir = "others";
        if ("doc".equals(extension) || "docx".equals(extension)) {
            subDir = "word";
        } else if ("csv".equals(extension)) {
            subDir = "csv";
        } else if ("pdf".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension) ||
                "png".equals(extension) || "gif".equals(extension)) {
            subDir = "images";
        } else if ("xls".equals(extension) || "xlsx".equals(extension)) {
            subDir = "excel";
        }

        // 4. 构建绝对路径并确保目录存在
        Path basePath = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path uploadDir = basePath.resolve(subDir);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String uniqueFilename = UUID.randomUUID() + "." + extension;
        Path filePath = uploadDir.resolve(uniqueFilename);

        // 5. 保存物理文件
        multipartFile.transferTo(filePath.toFile());

        // 6. 保存数据库记录
        File file = new File();
        file.setTaskId(taskId);
        file.setFileName(originalFilename);
        file.setOriginalName(originalFilename);
        file.setFilePath(filePath.toString());
        file.setFileSize(multipartFile.getSize());
        file.setFileType(multipartFile.getContentType());
        file.setFileExtension(extension);
        file.setUploadTime(LocalDateTime.now());
        file.setUploadUserId(userId);
        file.setStatus("UPLOADED");
        fileMapper.insert(file);

        return convertToVO(file);
    }

    /**
     * 批量上传文件（过滤不合规文件）
     */
    public List<String> batchUploadFiles(MultipartFile[] files, Long taskId, Long teacherId,
                                         String[] allowedExts, long maxSize) {
        List<String> successFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                // 过滤不符合条件的文件
                if (file.isEmpty() || file.getSize() > maxSize) {
                    continue;
                }
                String ext = getFileExtension(file.getOriginalFilename()).toLowerCase();
                boolean allowed = false;
                for (String e : allowedExts) {
                    if (e.equals(ext)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    continue;
                }
                FileVO uploaded = uploadFile(taskId, teacherId, file);
                if (uploaded != null) {
                    successFiles.add(uploaded.getFileName());
                }
            } catch (Exception e) {
                // 单个文件上传失败不影响其他文件，但打印日志
                e.printStackTrace();
            }
        }
        return successFiles;
    }

    /**
     * 获取任务的文件列表
     */
    public List<FileVO> getTaskFiles(Long taskId) {
        LambdaQueryWrapper<File> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(File::getTaskId, taskId)
                .eq(File::getStatus, "UPLOADED")
                .orderByDesc(File::getUploadTime);
        return fileMapper.selectList(wrapper).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 删除文件
     */
    @Transactional
    public void deleteFile(Long fileId, Long userId) throws IOException {
        File file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        if (!file.getUploadUserId().equals(userId)) {
            throw new RuntimeException("无权删除该文件");
        }

        // 检查任务状态是否允许删除
        Task task = taskMapper.selectById(file.getTaskId());
        if (task == null) {
            throw new RuntimeException("关联任务不存在");
        }
        if (LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new RuntimeException("任务已截止，无法删除文件");
        }
        if (!"PENDING_UPLOAD".equals(task.getStatus()) && !"NEED_REVISION".equals(task.getStatus())) {
            throw new RuntimeException("当前任务状态不允许删除文件");
        }

        // 删除物理文件
        Path filePath = Paths.get(file.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }

        // 逻辑删除（更新状态）
        file.setStatus("DELETED");
        fileMapper.updateById(file);
    }

    /**
     * 检查文件是否可删除
     */
    public boolean checkFileCanDelete(Long fileId, Long teacherId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || !file.getUploadUserId().equals(teacherId)) {
            return false;
        }
        Task task = taskMapper.selectById(file.getTaskId());
        if (task == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(task.getDeadline())) {
            return false;
        }
        return "PENDING_UPLOAD".equals(task.getStatus()) || "NEED_REVISION".equals(task.getStatus());
    }

    /**
     * 获取文件实体（下载用，校验归属）
     */
    public File getFileById(Long fileId, Long teacherId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || !file.getUploadUserId().equals(teacherId)) {
            return null;
        }
        return file;
    }

    /**
     * 获取文件信息（不校验权限，供其他模块使用）
     */
    public FileVO getFileInfo(Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        return convertToVO(file);
    }

    /**
     * 获取文件物理路径（供下载）
     */
    public java.io.File getFileForDownload(Long fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || !"UPLOADED".equals(file.getStatus())) {
            throw new RuntimeException("文件不存在或已被删除");
        }
        return new java.io.File(file.getFilePath());
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
    }

    private String formatFileSize(Long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    private FileVO convertToVO(File file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setTaskId(file.getTaskId());
        vo.setFileName(file.getFileName());
        vo.setOriginalName(file.getOriginalName());
        vo.setFileSize(file.getFileSize());
        vo.setFileSizeFormatted(formatFileSize(file.getFileSize()));
        vo.setFileType(file.getFileType());
        vo.setFileExtension(file.getFileExtension());
        vo.setUploadTime(file.getUploadTime());
        vo.setStatus(file.getStatus());
        vo.setDownloadUrl("/api/files/" + file.getId() + "/download");
        return vo;
    }
}

