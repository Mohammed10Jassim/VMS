package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    @Value("${app.storage.root:}")
    private String storageRoot;

    private Path root() {
        if (!StringUtils.hasText(storageRoot)) {
            throw new IllegalStateException("app.storage.root is not configured");
        }
        return Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public String saveVisitorFile(Long visitorId, String kind, MultipartFile file) {
        try {
            String original = StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename().trim()
                    : "upload.bin";
            String safeName = original.replaceAll("[^A-Za-z0-9._-]", "_");
            String uuid = UUID.randomUUID().toString().replace("-", "");
            String relPath = "files/visitors/" + visitorId + "/" + kind + "/" + uuid + "_" + safeName;

            Path target = root().resolve(relPath).normalize();
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // Return public URL path (what you store in DB)
            return "/" + relPath.replace("\\", "/");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByPublicUrl(String publicUrl) {
        if (!StringUtils.hasText(publicUrl)) return;
        String clean = publicUrl.startsWith("/") ? publicUrl.substring(1) : publicUrl;
        Path p = root().resolve(clean).normalize();
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }
}
