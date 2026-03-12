package com.rkt.VisitorManagementSystem.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** Saves file under /files/visitors/{visitorId}/{kind}/<uuid>_<safeName> and returns the public URL path (starting with /files/...) */
    String saveVisitorFile(Long visitorId, String kind, MultipartFile file);

    /** Deletes a previously saved public URL path like /files/visitors/...; silently no-ops if missing. */
    void deleteByPublicUrl(String publicUrl);
}
