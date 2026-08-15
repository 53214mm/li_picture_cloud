package com.li.lipicturecloud.infrastructure.airuntime;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 内存字节的 {@link MultipartFile} 适配：把融合暂存字节交给现有文件上传管线
 * （复用 getSize/getOriginalFilename/transferTo，不引入测试依赖）。
 */
final class InMemoryMultipartFile implements MultipartFile {

    private final String name;
    private final String originalFilename;
    private final String contentType;
    private final byte[] bytes;

    InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
        this.name = name;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.bytes = bytes.clone();
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return bytes.clone();
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(@NonNull File destination) throws IOException {
        Files.write(destination.toPath(), bytes);
    }
}
