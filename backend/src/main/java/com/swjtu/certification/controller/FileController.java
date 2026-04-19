package com.swjtu.certification.controller;

import com.swjtu.certification.entity.File;
import com.swjtu.certification.mapper.FileMapper;
import com.swjtu.certification.service.FileService;
import com.swjtu.certification.service.TaskService;
import com.swjtu.certification.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {
    private final FileService fileService;
    private final TaskService taskService;
    private final FileMapper fileMapper; // 用于权限校验

    // 文件格式限制
    private static final String[] ALLOWED_EXTENSIONS = {"pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "gif"};
    // 单文件大小限制：50MB
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /**
     * 上传单个文件
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long taskId,
            @RequestParam Long teacherId,
            @RequestParam(required = false) String itemCode) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 校验任务归属（确保该任务属于该教师）
            taskService.getTeacherTaskDetail(taskId, teacherId); // 无权访问会抛异常

            // 2. 校验文件
            if (file.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                result.put("code", 400);
                result.put("msg", "文件大小不能超过50MB");
                return ResponseEntity.badRequest().body(result);
            }
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            boolean allowed = false;
            for (String ext : ALLOWED_EXTENSIONS) {
                if (ext.equals(extension)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                result.put("code", 400);
                result.put("msg", "仅支持PDF、DOC/DOCX、EXCEL、图片格式");
                return ResponseEntity.badRequest().body(result);
            }

            // 3. 上传文件
            FileVO uploadFile = fileService.uploadFile(taskId, teacherId, file, itemCode);
            if (uploadFile == null) {
                result.put("code", 500);
                result.put("msg", "文件上传失败");
                return ResponseEntity.internalServerError().body(result);
            }

            result.put("code", 200);
            result.put("msg", "文件上传成功");
            result.put("data", uploadFile.getFileName());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 打印异常日志便于排查
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "上传失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 批量上传文件
     */
    @PostMapping("/batch-upload")
    public ResponseEntity<Map<String, Object>> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam Long taskId,
            @RequestParam Long teacherId,
            @RequestParam(required = false) String itemCode) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 校验任务归属
            taskService.getTeacherTaskDetail(taskId, teacherId);

            if (files == null || files.length == 0) {
                result.put("code", 400);
                result.put("msg", "请选择要上传的文件");
                return ResponseEntity.badRequest().body(result);
            }

            // 2. 批量上传（过滤不符合条件的文件）
            List<String> successFiles = fileService.batchUploadFiles(files, taskId, teacherId, itemCode,
                    ALLOWED_EXTENSIONS, MAX_FILE_SIZE);

            result.put("code", 200);
            result.put("msg", "批量上传完成，成功上传" + successFiles.size() + "个文件");
            result.put("data", successFiles);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "批量上传失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取任务的文件列表
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskFiles(
            @PathVariable Long taskId,
            @RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验任务归属
            taskService.getTeacherTaskDetail(taskId, teacherId);

            List<FileVO> fileList = fileService.getTaskFilesForTeacher(taskId, teacherId);
            result.put("code", 200);
            result.put("msg", "获取文件列表成功");
            result.put("data", fileList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "获取文件列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable Long fileId,
            @RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验文件归属及可删除性
            boolean canDelete = fileService.checkFileCanDelete(fileId, teacherId);
            if (!canDelete) {
                result.put("code", 400);
                result.put("msg", "文件不可删除：任务已截止/已审核/无权限");
                return ResponseEntity.badRequest().body(result);
            }

            fileService.deleteFile(fileId, teacherId);
            result.put("code", 200);
            result.put("msg", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "删除文件失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/{fileId}/download")
    public void downloadFile(
            @PathVariable Long fileId,
            @RequestParam Long teacherId,
            HttpServletResponse response) {
        try {
            // 1. 获取文件信息并校验归属
            File file = fileService.getFileById(fileId, teacherId);
            if (file == null) {
                response.setStatus(404);
                response.getWriter().write("文件不存在或无访问权限");
                return;
            }

            // 2. 设置响应头
            response.setContentType("application/octet-stream");

            String fileName = file.getName();

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
            response.setContentLengthLong(file.getFileSize());

            // 3. 写入文件流
            try (FileInputStream fis = new FileInputStream(file.getFilePath());
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setStatus(500);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @GetMapping("/admin/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskFilesForAdmin(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<FileVO> fileList = fileService.getTaskFiles(taskId);
            result.put("code", 200);
            result.put("msg", "获取文件列表成功");
            result.put("data", fileList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "获取文件列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/admin/{fileId}/download")
    public void downloadFileForAdmin(
            @PathVariable Long fileId,
            HttpServletResponse response) {
        try {
            com.swjtu.certification.entity.File fileEntity = fileMapper.selectById(fileId);
            if (fileEntity == null || !"UPLOADED".equals(fileEntity.getStatus())) {
                response.setStatus(404);
                response.getWriter().write("文件不存在或已被删除");
                return;
            }

            java.io.File physicalFile = new java.io.File(fileEntity.getFilePath());
            if (!physicalFile.exists()) {
                response.setStatus(404);
                response.getWriter().write("文件不存在");
                return;
            }

            response.setContentType("application/octet-stream");

            String fileName = fileEntity.getName();

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
            response.setContentLengthLong(physicalFile.length());

            try (FileInputStream fis = new FileInputStream(physicalFile);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setStatus(500);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
