package com.swjtu.certification.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotNull;

@Data
public class FileUploadDTO {
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    private String charset = "UTF-8";
}
