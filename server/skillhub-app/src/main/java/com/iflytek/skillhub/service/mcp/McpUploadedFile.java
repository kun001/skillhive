package com.iflytek.skillhub.service.mcp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** Bounded uploaded bytes adapted to the existing archive validation pipeline. */
record McpUploadedFile(String filename, byte[] bytes) implements MultipartFile {
    public String getName() { return "file"; }
    public String getOriginalFilename() { return filename; }
    public String getContentType() { return "application/zip"; }
    public boolean isEmpty() { return bytes.length == 0; }
    public long getSize() { return bytes.length; }
    public byte[] getBytes() { return bytes; }
    public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
}
