package com.rkt.VisitorManagementSystem.serviceImpl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.rkt.VisitorManagementSystem.dto.responseDto.PassResponseDto;
import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.mapper.PassMapper;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.PassService;
import com.rkt.VisitorManagementSystem.service.security.QrSigner;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PassServiceImpl implements PassService {

    private final VisitorRepository visitorRepository;
    private final PassRepository passRepository;

    @Value("${app.storage.root:}")
    private String storageRoot;

    @Value("${app.pass.default-validity-minutes:120}")
    private int defaultValidityMinutes;

    @Value("${app.pass.gate-no:GATE-1}")
    private String defaultGateNo;

    @Value("${app.company.name:Company}")
    private String companyName;

    @Value("${app.company.address:}")
    private String companyAddress;

    @Value("${app.company.logo-path:}")
    private String logoPath;

    @Value("${app.pass.scan-base-url}")
    private String scanBaseUrl;

    @Value("${app.pass.qr-secret}")
    private String qrSecret;

    @Value("${app.pass.qr-ttl-seconds:7200}")
    private long qrTtlSeconds;

    private static final PDType1Font FONT_BOLD    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Override
    @Transactional(readOnly = true)
    public byte[] generateVisitorPassPdf(Long visitorId, Integer validityMinutes, String gateNo) {
        VisitorEntity v = visitorRepository.findById(visitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + visitorId));

        PassEntity pass = passRepository.findByVisitor_Id(visitorId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found for visitor id: " + visitorId));

        int mins = (validityMinutes != null && validityMinutes > 0) ? validityMinutes : defaultValidityMinutes;
        String gate = (gateNo != null && !gateNo.isBlank()) ? gateNo :
                (pass.getGateNo() != null ? pass.getGateNo() : defaultGateNo);

        // Use persisted instants; compute fallback deadline from issuedAt if needed
        Instant issuedAtUtc = pass.getIssuedAt() != null ? pass.getIssuedAt() : Instant.now();
        Instant deadlineUtc = pass.getCheckinDeadline() != null
                ? pass.getCheckinDeadline()
                : issuedAtUtc.plus(Duration.ofMinutes(mins));

        // Choose which zone to display times in (office/system)
        ZoneId displayZone = ZoneId.systemDefault();
        String issuedAtText = DT.format(ZonedDateTime.ofInstant(issuedAtUtc, displayZone));
        String deadlineText = DT.format(ZonedDateTime.ofInstant(deadlineUtc, displayZone));

        // Signed scan URL
        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("passId", String.valueOf(pass.getId()));
        claims.put("visitorId", String.valueOf(v.getId()));
        claims.put("nonce", pass.getQrNonce());
        claims.put("issuedAtMs", String.valueOf(issuedAtUtc.toEpochMilli()));
        claims.put("validUntilMs", String.valueOf(deadlineUtc.toEpochMilli()));
        claims.put("gate", gate);

//        String token = QrSigner.sign(claims, qrSecret, qrTtlSeconds);
//        String qrUrl = scanBaseUrl + "?token=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);

        String qrUrl = scanBaseUrl + "/view?passId=" + pass.getId();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDRectangle media = page.getMediaBox();
            float margin = 36f;
            float x = margin, y = media.getHeight() - margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // Header
                float headerH = 110f;
                cs.setNonStrokingColor(new Color(20, 60, 120));
                cs.addRect(0, media.getHeight() - headerH, media.getWidth(), headerH);
                cs.fill();

                // Logo
                float logoMaxW = 90f, logoMaxH = 90f;
                float logoDrawW = 0, logoDrawH = 0;
                float headerTop = media.getHeight();
                float headerBottom = headerTop - headerH;

                BufferedImage logo = tryLoadLogo();
                if (logo != null) {
                    float[] wh = scaleToFit(logo.getWidth(), logo.getHeight(), logoMaxW, logoMaxH);
                    logoDrawW = wh[0];
                    logoDrawH = wh[1];
                    PDImageXObject logoX = LosslessFactory.createFromImage(doc, logo);
                    float logoXPos = margin;
                    float logoYPos = headerTop - (headerH + logoDrawH) / 2f;
                    cs.drawImage(logoX, logoXPos, logoYPos, logoDrawW, logoDrawH);
                }



                // Header text
                float textLeft = margin + (logoDrawW > 0 ? logoDrawW + 14f : 0f);
                float textRight = media.getWidth() - margin;
                float textWidth = textRight - textLeft;

                cs.beginText();
                cs.setNonStrokingColor(Color.WHITE);
                cs.setFont(FONT_BOLD, 20);
                cs.newLineAtOffset(textLeft, headerTop - 36);
                cs.showText(companyName + " — Visitor Pass");
                cs.endText();

                if (companyAddress != null && !companyAddress.isBlank()) {
                    cs.setNonStrokingColor(Color.WHITE);
                    cs.setFont(FONT_REGULAR, 10);
                    float startY = headerTop - 56;
                    drawWrappedText(cs, companyAddress, textLeft, startY, textWidth, 12f);
                }

                // Body
                y = headerBottom - 20f;
                float qrSize = 180f;
                float photoW = 220f, photoH = 260f;

                // Photo — load from BLOB (imageBlob) if present
                BufferedImage photo = tryLoadImageFromBlob(v.getImageBlob());
                if (photo != null) {
                    PDImageXObject phX = LosslessFactory.createFromImage(doc, photo);
                    cs.drawImage(phX, x, y - photoH, photoW, photoH);
                } else {
                    cs.setStrokingColor(Color.BLACK);
                    cs.addRect(x, y - photoH, photoW, photoH);
                    cs.stroke();
                }

                // QR (signed URL)
                BufferedImage qrImg = createQr(qrUrl, 260);
                PDImageXObject qrX = LosslessFactory.createFromImage(doc, qrImg);
                cs.drawImage(qrX, media.getWidth() - margin - qrSize, y - qrSize, qrSize, qrSize);

                // Details under photo
                float detailsTop = y - photoH - 16f;
                float detailsLeft = x;
                float labelW = 140f;
                float rowH = 18f;

                cs.setNonStrokingColor(Color.BLACK);
                detailsTop = writeRow(cs, "Visitor Name:", safe(v.getVisitorName()), detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Visit Purpose:", safe(String.valueOf(v.getVisitPurpose())), detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Company:", safe(v.getCompany()), detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Host (To Meet):", v.getPersonWhomToMeet() != null ? safe(v.getPersonWhomToMeet().getUserName()) : "-", detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Department:", v.getDepartment() != null ? safe(v.getDepartment().getName()) : "-", detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Date of Visit:", v.getDateOfVisiting() != null ? v.getDateOfVisiting().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "-", detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Issued At:", issuedAtText, detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Check-in Deadline:", deadlineText, detailsLeft, detailsTop, rowH, labelW);
                detailsTop = writeRow(cs, "Gate No:", gate, detailsLeft, detailsTop, rowH, labelW);

                // ID proof thumbnail if ID proof is an image (load from blob)
                float idThumbH = 70f, idThumbW = 180f;
                BufferedImage idp = null;
                if (v.getIdProofBlob() != null && v.getIdProofContentType() != null && v.getIdProofContentType().toLowerCase().startsWith("image/")) {
                    idp = tryLoadImageFromBlob(v.getIdProofBlob());
                }
                if (idp != null) {
                    PDImageXObject idX = LosslessFactory.createFromImage(doc, idp);
                    cs.drawImage(idX, detailsLeft, detailsTop - idThumbH - 12, idThumbW, idThumbH);
                }

                // Footer
                float footerH = 44f;
                cs.setNonStrokingColor(new Color(240, 240, 240));
                cs.addRect(0, 0, media.getWidth(), footerH);
                cs.fill();

                cs.setNonStrokingColor(new Color(80, 80, 80));
                cs.beginText();
                cs.setFont(FONT_REGULAR, 9);
                String foot = "Carry a valid government ID. Present this pass at security. QR must be scanned for check-in and check-out.";
                float footW = FONT_REGULAR.getStringWidth(foot) / 1000 * 9;
                cs.newLineAtOffset((media.getWidth() - footW) / 2f, 16);
                cs.showText(foot);
                cs.endText();
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate pass PDF: " + ex.getMessage(), ex);
        }
    }


    @Transactional(readOnly = true)
    public Page<PassResponseDto> getAllPassDetails(Pageable pageable) {

        Page<PassEntity> page = passRepository.findAllWithVisitor(pageable);
        return page.map(PassMapper::toPassResponseDto);

    }


    private float[] scaleToFit(float w, float h, float maxW, float maxH) {
        if (w <= 0 || h <= 0) return new float[]{0f, 0f};      // nothing to draw
        if (maxW <= 0 || maxH <= 0) return new float[]{w, h};   // no constraints

        float scale = Math.min(maxW / w, maxH / h);
        // ensure we don't upscale beyond original size (optional — remove Math.min(...) if upscaling OK)
        // scale = Math.min(scale, 1.0f);

        float scaledW = w * scale;
        float scaledH = h * scale;
        return new float[]{scaledW, scaledH};
    }


    private float writeRow(PDPageContentStream cs, String label, String value, float x, float topY, float rowH, float labelW) throws Exception {
        float y = topY - rowH;
        cs.beginText();
        cs.setFont(FONT_BOLD, 12);
        cs.newLineAtOffset(x, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(FONT_REGULAR, 12);
        cs.newLineAtOffset(x + labelW + 6, y);
        cs.showText(value != null ? value : "-");
        cs.endText();
        return y;
    }

    private void drawWrappedText(PDPageContentStream cs, String text, float startX, float startY, float maxWidth, float lineHeight) throws Exception {
        float y = startY;
        String[] words = text.replace("\n", " ").split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String trial = (line.length() == 0) ? w : (line + " " + w);
            float tw = FONT_REGULAR.getStringWidth(trial) / 1000 * 10;
            if (tw > maxWidth && line.length() > 0) {
                cs.beginText();
                cs.newLineAtOffset(startX, y);
                cs.showText(line.toString());
                cs.endText();
                y -= lineHeight;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) {
            cs.beginText();
            cs.newLineAtOffset(startX, y);
            cs.showText(line.toString());
            cs.endText();
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }

    private java.awt.image.BufferedImage createQr(String text, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int xx = 0; xx < size; xx++) {
            for (int yy = 0; yy < size; yy++) {
                img.setRGB(xx, yy, matrix.get(xx, yy) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return img;
    }

    // Resolve a path (kept for logo support) — returns null if storageRoot or input absent
    private Path resolvePath(String urlPath) {
        if (urlPath == null || urlPath.isBlank()) return null;
        if (storageRoot == null || storageRoot.isBlank()) return null;
        String clean = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        return Path.of(storageRoot, clean);
    }

    // Unused for visitor blobs, but kept to support other code paths
    private BufferedImage tryLoadImage(Path p) {
        try {
            if (p != null && Files.exists(p)) {
                return ImageIO.read(p.toFile());
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Load image directly from in-memory BLOB
    private BufferedImage tryLoadImageFromBlob(byte[] blob) {
        if (blob == null || blob.length == 0) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(blob)) {
            return ImageIO.read(bis);
        } catch (Exception ignored) {
            return null;
        }
    }

    // replace existing tryLoadLogo() with this implementation
    private BufferedImage tryLoadLogo() {
        try {
            if (logoPath == null || logoPath.isBlank()) return null;

            // --- Inline Base64 (new) ---
            // support "base64:<base64data...>" or full data URI "data:image/png;base64,<base64...>"
            String lp = logoPath.trim();
            if (lp.startsWith("base64:") || lp.startsWith("data:")) {
                String base64;
                if (lp.startsWith("base64:")) {
                    base64 = lp.substring("base64:".length());
                } else {
                    // data URI
                    int comma = lp.indexOf(',');
                    if (comma > 0 && comma < lp.length() - 1) base64 = lp.substring(comma + 1);
                    else base64 = null;
                }
                if (base64 != null && !base64.isBlank()) {
                    try (var bis = new ByteArrayInputStream(java.util.Base64.getDecoder().decode(base64))) {
                        return ImageIO.read(bis);
                    } catch (Exception ignored) { /* fallthrough to other attempts */ }
                }
            }

            // --- classpath: prefix (existing) ---
            if (lp.startsWith("classpath:")) {
                String cp = lp.substring("classpath:".length());
                ClassPathResource res = new ClassPathResource(cp.startsWith("/") ? cp.substring(1) : cp);
                if (res.exists()) {
                    try (var is = res.getInputStream()) {
                        return ImageIO.read(is);
                    }
                }
                return null;
            }

            // --- filesystem path (existing) ---
            Path p = Paths.get(lp).toAbsolutePath().normalize();
            if (Files.exists(p)) return ImageIO.read(p.toFile());

            // --- fallback: classpath without prefix (existing) ---
            String cp = lp.startsWith("/") ? lp.substring(1) : lp;
            ClassPathResource res = new ClassPathResource(cp);
            if (res.exists()) {
                try (var is = res.getInputStream()) {
                    return ImageIO.read(is);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

}
