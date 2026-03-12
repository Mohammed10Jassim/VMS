package com.rkt.VisitorManagementSystem.service;

public interface MailService {
    void sendPassPdf(String toEmail, String subject, String body, byte[] pdfBytes, String fileName);

    /**
     * Send a simple HTML + plain-text email (multipart/alternative).
     * Both htmlBody and plainText may be provided; if plainText is null we will generate a simple fallback.
     */
    void sendSimple(String toEmail, String subject, String htmlBody, String plainText);
}
