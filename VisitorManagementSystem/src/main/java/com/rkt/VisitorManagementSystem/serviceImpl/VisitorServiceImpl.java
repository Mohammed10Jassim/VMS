package com.rkt.VisitorManagementSystem.serviceImpl;

import com.rkt.VisitorManagementSystem.dto.VisitorDto;
import com.rkt.VisitorManagementSystem.dto.responseDto.VisitorResponseDto;
import com.rkt.VisitorManagementSystem.entity.DepartmentEntity;
import com.rkt.VisitorManagementSystem.entity.UserEntity;
import com.rkt.VisitorManagementSystem.entity.VisitorEntity;
import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import com.rkt.VisitorManagementSystem.entity.enums.VisitPurpose;
import com.rkt.VisitorManagementSystem.entity.enums.VisitStatus;
import com.rkt.VisitorManagementSystem.exception.customException.BadRequestException;
import com.rkt.VisitorManagementSystem.exception.customException.EntityInUseException;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.exception.customException.RoleDepartmentMismatchException;
import com.rkt.VisitorManagementSystem.mapper.VisitorMapper;
import com.rkt.VisitorManagementSystem.repository.DepartmentRepository;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.repository.UserRepository;
import com.rkt.VisitorManagementSystem.repository.VisitorRepository;
import com.rkt.VisitorManagementSystem.service.MailService;
import com.rkt.VisitorManagementSystem.service.VisitorService;
import com.rkt.VisitorManagementSystem.spec.VisitorSpecifications;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VisitorServiceImpl implements VisitorService {

    private final VisitorRepository visitorRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final VisitorMapper visitorMapper;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final PassRepository passRepository;

    // Backend URL used in approval emails (must be reachable from recipient)
    @Value("${app.mail.approval-backend-url:http://192.168.0.224:8099/rkt/visitors/respond}")
    private String approvalBackendUrl;

    @Value("${app.backend.respond-redirect-url:http://192.168.0.224:8099/rkt/visitors/respond-redirect}")
    private String backendRespondRedirectUrl;

    @Value("${app.mail.approval-base-url:http://192.168.0.224:8099/rkt/visitors/respond}")
    private String approvalBaseUrl;

    @Value("${app.mail.initial-approval-ttl-hours:4}")
    private long initialApprovalTtlHours; // default 4 hours

    // ------------ Read methods ------------------------------------

    @Override
    public List<VisitorResponseDto> findAll() {
        return visitorRepository.findAll().stream()
                .map(visitorMapper::toResponse)
                .toList();
    }

    @Override
    public VisitorResponseDto get(Long id) {
        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));
        return visitorMapper.toResponse(e);
    }

    // entity-returning find (for blob-including paths)
    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public VisitorEntity findEntityById(Long id) {
        return visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));
    }

    @Override
    @Transactional
    public Page<VisitorResponseDto> searchVisitorsNew(
            Pageable pageable,
            String q,
            String visitPurpose,
            Long departmentId,
            Long hostUserId,
            String visitStatus,
            LocalDate fromDate,
            LocalDate toDate) {

        VisitPurpose vp = null;
        VisitStatus vs = null;

        if (visitPurpose != null && !visitPurpose.isBlank()) {
            vp = VisitPurpose.valueOf(visitPurpose.toUpperCase());
        }

        if (visitStatus != null && !visitStatus.isBlank()) {
            vs = VisitStatus.valueOf(visitStatus.toUpperCase());
        }

        Page<VisitorEntity> page = visitorRepository.searchVisitors(
                q,
                vp,
                departmentId,
                hostUserId,
                vs,
                fromDate,
                toDate,
                pageable
        );

        return page.map(visitorMapper::toResponse);
    }

    // ------------ Create / update / delete ------------------------

    @Override
    public VisitorResponseDto create(VisitorDto dto) {
        return create(dto, null, null);
    }

    @Override
    public VisitorResponseDto create(VisitorDto dto, MultipartFile image, MultipartFile idProof) {
        // load host
        UserEntity host = userRepository.findById(dto.getPersonWhomToMeetId())
                .orElseThrow(() -> new ResourceNotFoundException("Host user not found: " + dto.getPersonWhomToMeetId()));

        DepartmentEntity dept = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));

        if (host.getDepartment() == null || !host.getDepartment().getId().equals(dept.getId())) {
            throw new RoleDepartmentMismatchException("Host user's department does not match the provided department");
        }

        // map dto -> entity
        VisitorEntity entity = visitorMapper.toEntity(dto, host, dept);

        // ensure default status
        if (entity.getVisitStatus() == null) {
            entity.setVisitStatus(VisitStatus.PENDING);
        }

        // handle files
        if (image != null && !image.isEmpty()) {
            validateImage(image);
            setPhotoFromMultipart(entity, image);
        }
        if (idProof != null && !idProof.isEmpty()) {
            validateIdProof(idProof);
            setIdProofFromMultipart(entity, idProof);
        }

        VisitorEntity saved = visitorRepository.save(entity);

        // NEW: generate approval single-use key (raw) and persist only hash + expiry + approver id
        try {
            final String rawApprovalKey = generateRawKey(); // url-safe base64, no padding
            final String approvalKeyHash = passwordEncoder.encode(rawApprovalKey);
            final Instant expiry = Instant.now().plus(Duration.ofHours(initialApprovalTtlHours)); // adjust TTL as needed

            saved.setApprovalKeyHash(approvalKeyHash);
            saved.setApprovalKeyExpiry(expiry);
            saved.setApprovalForUserId(host.getId());
            saved = visitorRepository.save(saved); // persist approval metadata

            // Build approve/reject URLs including the raw key (URL-encoded)
            final Long savedVisitorId = saved.getId();
            String backendBase = approvalBackendUrl;
            if (backendBase == null || backendBase.isBlank()) {
                backendBase = (backendRespondRedirectUrl != null && !backendRespondRedirectUrl.isBlank())
                        ? backendRespondRedirectUrl
                        : approvalBaseUrl;
            }

            final String keyParam = URLEncoder.encode(rawApprovalKey, StandardCharsets.UTF_8);
            final String hostIdParam = URLEncoder.encode(String.valueOf(host.getId()), StandardCharsets.UTF_8);

            final String approveUrl = backendBase + (backendBase.contains("?") ? "&" : "?")
                    + "visitorId=" + URLEncoder.encode(String.valueOf(savedVisitorId), StandardCharsets.UTF_8)
                    + "&action=approve"
                    + "&key=" + keyParam
                    + "&hostId=" + hostIdParam;

            final String rejectUrl = backendBase + (backendBase.contains("?") ? "&" : "?")
                    + "visitorId=" + URLEncoder.encode(String.valueOf(savedVisitorId), StandardCharsets.UTF_8)
                    + "&action=reject"
                    + "&key=" + keyParam
                    + "&hostId=" + hostIdParam;

            String subject = String.format("Visitor invitation: %s — Approve or Reject", saved.getVisitorName());

            String plain = new StringBuilder()
                    .append("Hello ").append(host.getUserName() != null ? host.getUserName() : "Host").append(",\n\n")
                    .append("A visitor has been registered who will meet you:\n")
                    .append("  Visitor: ").append(saved.getVisitorName()).append("\n")
                    .append("  Purpose: ").append(saved.getVisitPurpose()).append("\n")
                    .append("  Date: ").append(saved.getDateOfVisiting() != null ? saved.getDateOfVisiting().toString() : "-").append("\n\n")
                    .append("Note: Clicking Approve/Reject will request a confirmation email to be sent to your mailbox. ")
                    .append("The action will complete only after you confirm from your email.\n\n")
                    .append("Approve: ").append(approveUrl).append("\n")
                    .append("Reject:  ").append(rejectUrl).append("\n\n")
                    .append("If you have trouble, contact Security Desk.")
                    .toString();

            // HTML body
            StringBuilder html = new StringBuilder();
            html.append("<html><body style=\"font-family:Arial,Helvetica,sans-serif;line-height:1.4\">");
            html.append("<p>Hello ").append(escapeHtml(host.getUserName() != null ? host.getUserName() : "Host")).append(",</p>");
            html.append("<p>A visitor has been registered who will meet you:</p>");
            html.append("<ul>");
            html.append("<li><strong>Visitor:</strong> ").append(escapeHtml(saved.getVisitorName())).append("</li>");
            html.append("<li><strong>Purpose:</strong> ").append(escapeHtml(String.valueOf(saved.getVisitPurpose()))).append("</li>");
            html.append("<li><strong>Date:</strong> ").append(saved.getDateOfVisiting() != null ? saved.getDateOfVisiting().toString() : "-").append("</li>");
            html.append("</ul>");
            html.append("<p><strong>Note:</strong> Clicking Approve/Reject will <em>request</em> a confirmation email to your mailbox. The action will complete only after you confirm from your email.</p>");
            html.append("<p>Please choose an action:</p>");
            html.append("<div style=\"display:flex;gap:12px;\">");
            html.append("<a href=\"").append(approveUrl).append("\" ")
                    .append("style=\"background-color:#28a745;color:white;padding:12px 20px;text-decoration:none;border-radius:6px;display:inline-block;\">")
                    .append("Approve Visit")
                    .append("</a>");
            html.append("<a href=\"").append(rejectUrl).append("\" ")
                    .append("style=\"background-color:#dc3545;color:white;padding:12px 20px;text-decoration:none;border-radius:6px;display:inline-block;\">")
                    .append("Reject Visit")
                    .append("</a>");
            html.append("</div>");
            html.append("<p>If you have issues, open the app or contact Security Desk.</p>");
            html.append("</body></html>");

            // send email (plain + html). mailService.sendSimple should build multipart
            mailService.sendSimple(host.getEmail(), subject, html.toString(), plain);

            // Do NOT log rawApprovalKey anywhere (security)
        } catch (Exception ex) {
            // Non-fatal: log/send alert but don't stop the create flow
            log.warn("Non-fatal: could not generate approval key or send approval email for visitor {}: {}", saved.getId(), ex.getMessage());
        }

        return visitorMapper.toResponse(saved);
    }

    // helper: generate raw approval key
    private static final int KEY_BYTES = 24; // 24 bytes -> ~32 chars url-safe

    private String generateRawKey() {
        byte[] b = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    @Override
    @Transactional
    public VisitorResponseDto update(Long id, VisitorDto dto) {
        VisitorEntity existing = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        log.info("Incoming DTO -> phone={}, vehicle={}, date={}",
                dto.getPhone(),
                dto.getVehicleNumber(),
                dto.getDateOfVisiting());

        // If client included a date and it differs from the stored one -> reject
        if (dto.getDateOfVisiting() != null && !dto.getDateOfVisiting().equals(existing.getDateOfVisiting())) {
            throw new BadRequestException(String.format(
                    "Visit date is immutable and cannot be changed. Current dateOfVisiting: %s",
                    existing.getDateOfVisiting()
            ));
        }

        // copy permitted updatable fields only (do NOT modify dateOfVisiting)
        if (dto.getVisitorName() != null) existing.setVisitorName(dto.getVisitorName());
        if (dto.getCompany() != null) existing.setCompany(dto.getCompany());
        if (dto.getVisitPurpose() != null) existing.setVisitPurpose(dto.getVisitPurpose());
        if (dto.getEmail() != null) existing.setEmail(dto.getEmail());
        if (dto.getPhone() != null) existing.setPhone(dto.getPhone());
        if (dto.getVehicleNumber() != null) existing.setVehicleNumber(dto.getVehicleNumber());

        UserEntity newHost = null;
        DepartmentEntity newDept = null;

        if (dto.getPersonWhomToMeetId() != null) {
            newHost = userRepository.findById(dto.getPersonWhomToMeetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Host user not found: " + dto.getPersonWhomToMeetId()));
        }
        if (dto.getDepartmentId() != null) {
            newDept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + dto.getDepartmentId()));
        }

        if (newHost != null && newDept != null) {
            if (newHost.getDepartment() == null || !newHost.getDepartment().getId().equals(newDept.getId())) {
                throw new RoleDepartmentMismatchException("Host user's department does not match the provided department");
            }
            existing.setPersonWhomToMeet(newHost);
            existing.setDepartment(newDept);
        } else if (newHost != null) {
            if (newHost.getDepartment() == null) {
                throw new RoleDepartmentMismatchException("Host user is not linked to any department");
            }
            existing.setPersonWhomToMeet(newHost);
            existing.setDepartment(newHost.getDepartment());
        } else if (newDept != null) {
            UserEntity currentHost = existing.getPersonWhomToMeet();
            if (currentHost != null && currentHost.getDepartment() != null
                    && !currentHost.getDepartment().getId().equals(newDept.getId())) {
                throw new RoleDepartmentMismatchException("Provided department doesn't match current host's department");
            }
            existing.setDepartment(newDept);
        }

        VisitorEntity saved = visitorRepository.save(existing);
        return visitorMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        VisitorEntity existing = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));
        try {
            passRepository.deleteByVisitor_Id(id);
            visitorRepository.delete(existing);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Failed to delete visitor {} due to integrity constraints: {}", id, ex.getMessage());
            throw new EntityInUseException("Visitor is referenced by other records and cannot be deleted");
        }
    }

    // ------------ simple queries ---------------------------------

    @Override
    public List<VisitorResponseDto> findByVisitDate(LocalDate dateOfVisiting) {
        return visitorRepository.findAllByDateOfVisitingOrderByVisitorNameAsc(dateOfVisiting).stream()
                .map(visitorMapper::toResponse)
                .toList();
    }

    @Override
    public List<VisitorResponseDto> findByHost(Long hostUserId) {
        return visitorRepository.findAllByPersonWhomToMeet_IdOrderByDateOfVisitingDesc(hostUserId).stream()
                .map(visitorMapper::toResponse)
                .toList();
    }

    // ------------ file handling ----------------------------------

    @Override
    public VisitorResponseDto uploadImage(Long id, MultipartFile file) {
        return replaceImage(id, file);
    }

    @Override
    public VisitorResponseDto replaceImage(Long id, MultipartFile file) {
        validateImage(file);
        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        setPhotoFromMultipart(e, file);
        return visitorMapper.toResponse(visitorRepository.save(e));
    }

    @Override
    public VisitorResponseDto deleteImage(Long id) {
        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        e.setImageBlob(null);
        e.setImageContentType(null);
        e.setImageSizeBytes(null);

        return visitorMapper.toResponse(visitorRepository.save(e));
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Only image uploads are allowed");
        }
    }

    private static void setPhotoFromMultipart(VisitorEntity e, MultipartFile image) {
        try {
            e.setImageBlob(image.getBytes());
            e.setImageContentType(image.getContentType());
            e.setImageSizeBytes(image.getSize());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read image file", ex);
        }
    }

    @Override
    public VisitorResponseDto uploadIdProof(Long id, MultipartFile file) {
        return replaceIdProof(id, file);
    }

    @Override
    public VisitorResponseDto replaceIdProof(Long id, MultipartFile file) {
        validateIdProof(file);
        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        setIdProofFromMultipart(e, file);
        return visitorMapper.toResponse(visitorRepository.save(e));
    }

    @Override
    public VisitorResponseDto deleteIdProof(Long id) {
        VisitorEntity e = visitorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visitor not found with id: " + id));

        e.setIdProofBlob(null);
        e.setIdProofContentType(null);
        e.setIdProofSizeBytes(null);

        return visitorMapper.toResponse(visitorRepository.save(e));
    }

    private static void validateIdProof(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        String ct = file.getContentType();
        if (ct == null) throw new IllegalArgumentException("Unsupported file type");
        String low = ct.toLowerCase();
        boolean ok = low.startsWith("image/") || low.equals("application/pdf");
        if (!ok) throw new IllegalArgumentException("Only image (jpg/png/jpeg) or PDF is allowed");
    }

    private static void setIdProofFromMultipart(VisitorEntity e, MultipartFile idProof) {
        try {
            e.setIdProofBlob(idProof.getBytes());
            e.setIdProofContentType(idProof.getContentType());
            e.setIdProofSizeBytes(idProof.getSize());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read ID proof file", ex);
        }
    }

    // ------------ paging/search ----------------------------------
//
//    @Override
//    @Transactional(Transactional.TxType.SUPPORTS)
//    public Page<VisitorEntity> searchEntities(Pageable pageable,
//                                              String q,
//                                              String visitPurpose,
//                                              Long departmentId,
//                                              Long hostUserId,
//                                              //LocalDate dateOfVisiting,
//                                              LocalDate fromDate,
//                                              LocalDate toDate,
//                                              String visitStatus) {
//        Specification<VisitorEntity> spec = buildSpec(q, visitPurpose, departmentId, hostUserId, fromDate, toDate, visitStatus);
//        Specification<VisitorEntity> distinctSpec = (root, query, cb) -> {
//            query.distinct(true);
//            return cb.conjunction();
//        };
//        spec = VisitorSpecifications.combine(spec, distinctSpec);
//        return visitorRepository.findAll(spec, pageable);
//    }

//    @Override
//    @Transactional(Transactional.TxType.SUPPORTS)
//    public Page<VisitorEntity> searchEntities(Pageable pageable,
//                                              String q,
//                                              String visitPurpose,
//                                              Long departmentId,
//                                              Long hostUserId,
//                                              LocalDate fromDate,
//                                              LocalDate toDate,
//                                              String visitStatus) {
//
//        if (fromDate != null || toDate != null) {
//
//            return visitorRepository.filterByVisitDateRange(fromDate, toDate, pageable);
//        }
//
//        return visitorRepository.findAll(pageable);
//    }
//


//    @Override
//    @Transactional(Transactional.TxType.SUPPORTS)
//    public Page<VisitorResponseDto> search(Pageable pageable,
//                                           String q,
//                                           String visitPurpose,
//                                           Long departmentId,
//                                           Long hostUserId,
//                                           LocalDate fromDate,
//                                           LocalDate toDate,
//                                           String visitStatus) {
//
//        Page<VisitorEntity> page;
//
//        if (fromDate != null || toDate != null) {
//
//            page = visitorRepository.filterByVisitDateRange(fromDate, toDate, pageable);
//
//        } else {
//
//            page = visitorRepository.findAll(pageable);
//        }
//
//        return page.map(visitorMapper::toResponse);
//    }
    /**
     * Build the shared Specification for searches (used by both DTO and entity search).
     */



//    private Specification<VisitorEntity> buildSpec(
//            String q,
//            String visitPurpose,
//            Long departmentId,
//            Long hostUserId,
//            LocalDate fromDate,
//            LocalDate toDate,
//            String visitStatus) {
//
//        Specification<VisitorEntity> spec = null;
//
//        spec = VisitorSpecifications.combine(spec, VisitorSpecifications.freeText(q));
//
//        VisitPurpose vp = null;
//        if (visitPurpose != null && !visitPurpose.isBlank()) {
//            try {
//                vp = VisitPurpose.valueOf(visitPurpose.trim().toUpperCase());
//            } catch (Exception ignored) {}
//        }
//
//        spec = VisitorSpecifications.combine(spec, VisitorSpecifications.visitPurpose(vp));
//        spec = VisitorSpecifications.combine(spec, VisitorSpecifications.departmentId(departmentId));
//        spec = VisitorSpecifications.combine(spec, VisitorSpecifications.hostUserId(hostUserId));
//        spec = VisitorSpecifications.combine(spec, VisitorSpecifications.dateRange(fromDate, toDate));
//
//        if (visitStatus != null && !visitStatus.isBlank()) {
//
//            String s = visitStatus.trim().toUpperCase();
//
//            try {
//                VisitStatus vs = VisitStatus.valueOf(s);
//                spec = VisitorSpecifications.combine(spec, VisitorSpecifications.visitStatus(vs));
//            } catch (Exception e) {
//
//                try {
//                    PassStatus ps = PassStatus.valueOf(s);
//                    spec = VisitorSpecifications.combine(spec, VisitorSpecifications.passStatus(ps));
//                } catch (Exception ignored) {}
//            }
//        }
//
//        return spec;
//    }
    private static String escapeHtml(String in) {
        if (in == null) return "";
        return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }
}
