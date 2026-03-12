package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:}")
    private String from;

    /**
     * Send a PDF attachment (visitor pass). Uses multipart message so attachment is supported.
     */
    @Async("mailTaskExecutor")
    @Override
    public void sendPassPdf(String toEmail, String subject, String body, byte[] pdfBytes, String fileName) {
        if (!mailEnabled) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart = true because we attach a file
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setTo(toEmail);

            // Set From (if configured). Use InternetAddress to avoid common bad-from issues.
            if (from != null && !from.isBlank()) {
                try {
                    helper.setFrom(new InternetAddress(from));
                } catch (Exception e) {
                    helper.setFrom(from);
                }
            }

            helper.setSubject(subject != null ? subject : "");
            String plain = (body == null) ? "" : body.replaceAll("\\<.*?\\>", "");
            helper.setText(plain, body != null ? body : plain);

            if (pdfBytes != null && pdfBytes.length > 0) {
                try {
                    helper.addAttachment(fileName != null ? fileName : "pass.pdf",
                            new ByteArrayResource(pdfBytes), "application/pdf");
                } catch (Exception ex) {
                    helper.addAttachment(fileName != null ? fileName : "pass.pdf", new ByteArrayResource(pdfBytes));
                }
            }

            message.setHeader("X-Mailer", "VisitorManagementSystem");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email send failed (sendPassPdf): " + e.getMessage());
        }
    }

    /**
     * Send a simple (multipart alternative) email with plain text + HTML alternative.
     */
    @Async("mailTaskExecutor")
    @Override
    public void sendSimple(String toEmail, String subject, String htmlBody, String plainText) {
        if (!mailEnabled) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true to allow alternative (plain + html)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setTo(toEmail);

            if (from != null && !from.isBlank()) {
                try {
                    helper.setFrom(new InternetAddress(from));
                } catch (Exception e) {
                    helper.setFrom(from);
                }
            }

            // Reply-To
            try {
                if (from != null && !from.isBlank()) {
                    helper.setReplyTo(new InternetAddress(from));
                }
            } catch (Exception ignored) {}

            helper.setSubject(subject != null ? subject : "");

            String plain = (plainText != null && !plainText.isBlank()) ? plainText : stripHtml(htmlBody);
            String html = (htmlBody != null && !htmlBody.isBlank()) ? htmlBody : plain;

            helper.setText(plain, html);

            message.setHeader("X-Mailer", "VisitorManagementSystem");
            try {
                if (from != null && !from.isBlank()) {
                    message.setHeader("List-Unsubscribe", "<mailto:" + from + ">");
                }
            } catch (Exception ignored) {}

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email send failed (sendSimple): " + e.getMessage());
        }
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("\\<.*?\\>", "").replaceAll("&nbsp;", " ").trim();
    }
}
