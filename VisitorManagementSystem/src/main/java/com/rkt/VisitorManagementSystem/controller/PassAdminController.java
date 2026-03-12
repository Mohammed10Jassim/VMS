// src/main/java/com/rkt/VisitorManagementSystem/controller/PassAdminController.java
package com.rkt.VisitorManagementSystem.controller;

import com.rkt.VisitorManagementSystem.dto.responseDto.PassResponseDto;
import com.rkt.VisitorManagementSystem.entity.PassEntity;
import com.rkt.VisitorManagementSystem.exception.customException.ResourceNotFoundException;
import com.rkt.VisitorManagementSystem.repository.PassRepository;
import com.rkt.VisitorManagementSystem.service.PassService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rkt/passes")
@CrossOrigin(origins = "*")
public class PassAdminController {

    private final PassRepository passRepository;
    private final PassService passService;

    @Value("${app.office.timezone:Asia/Kolkata}")
    private String officeZoneId;

    @Value("${app.office.close-hour:20}") // 20:00 (8PM) local office time
    private int officeCloseHour;


    @PreAuthorize("hasRole('HR_MANAGER')")
    @PatchMapping("/{passId}/admin/extend-checkout")
    public ResponseEntity<?> extendCheckout(
            @PathVariable Long passId,
            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime requestedCheckoutAtLocal,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "updatedByName", required = false) String updatedByName,
            @RequestParam(value = "updatedByUserId", required = false) Long updatedByUserId
    ) {
        PassEntity pass = passRepository.findById(passId)
                .orElseThrow(() -> new ResourceNotFoundException("Pass not found: " + passId));

        if (pass.getCheckoutAt() != null) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "ALREADY_CHECKED_OUT",
                    "message", "Cannot extend checkout after the visitor has already checked out.",
                    "checkedOutAt", pass.getCheckoutAt().toString()
            ));
        }

        ZoneId officeZone = ZoneId.of(officeZoneId);

        // Determine visit day in office zone (prefer check-in; else issued-at; else "today" in office zone)
        Instant anchorUtc = pass.getCheckinAt() != null ? pass.getCheckinAt()
                : (pass.getIssuedAt() != null ? pass.getIssuedAt() : Instant.now());
        LocalDate visitDay = anchorUtc.atZone(officeZone).toLocalDate();

        // Office closing cutoff for that visit day (local office time)
        ZonedDateTime cutoffZdt = ZonedDateTime.of(visitDay, LocalTime.of(officeCloseHour, 0), officeZone);

        // Convert requested "to" (local office) to ZonedDateTime and then Instant
        ZonedDateTime requestedZdt = requestedCheckoutAtLocal.atZone(officeZone);

        if (requestedZdt.isAfter(cutoffZdt)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CHECKOUT_TIME_AFTER_OFFICE_CLOSE",
                    "message", "Requested checkout time exceeds office closing time for the visit day.",
                    "allowedUntil", cutoffZdt.toString()
            ));
        }

        pass.setUpdatedCheckoutAt(requestedZdt.toInstant());  // store as Instant (UTC)
        pass.setCheckoutUpdateReason(reason);
        pass.setCheckoutUpdatedByName(updatedByName);
        pass.setCheckoutUpdatedByUserId(updatedByUserId);
        pass.setCheckoutUpdatedAt(Instant.now());

        passRepository.save(pass);

        return ResponseEntity.ok(Map.of(
                "message", "Checkout window extended",
                "passId", pass.getId(),
                "updatedCheckoutAt", pass.getUpdatedCheckoutAt(),   // Instant
                "cutoff", cutoffZdt.toInstant()                     // Instant for consistency
        ));
    }

    @PreAuthorize("hasRole('HR_MANAGER')")
    @GetMapping
    public ResponseEntity<Page<PassResponseDto>> getAllPasses(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Pageable mappedPageable = mapSortFields(pageable);

        return ResponseEntity.ok(passService.getAllPassDetails(mappedPageable));
    }


    private Pageable mapSortFields(Pageable pageable) {

        Map<String, String> sortMapping = Map.ofEntries(
                Map.entry("passId", "id"),
                Map.entry("visitorName", "visitor.visitorName"),
                Map.entry("visitStatus", "visitor.visitStatus"),
                Map.entry("passStatus", "status"),
                Map.entry("issuedAt", "issuedAt"),
                Map.entry("checkinDeadline", "checkinDeadline"),
                Map.entry("checkinAt", "checkinAt"),
                Map.entry("checkoutAt", "checkoutAt"),
                Map.entry("updatedCheckoutAt", "updatedCheckoutAt"),
                Map.entry("createdAt", "createdAt"),
                Map.entry("gateNo", "gateNo")
        );

        List<Sort.Order> mappedOrders = new ArrayList<>();

        for (Sort.Order order : pageable.getSort()) {

            String mappedField = sortMapping.get(order.getProperty());

            if (mappedField != null) {
                mappedOrders.add(new Sort.Order(order.getDirection(), mappedField));
            }
        }

        Sort finalSort = mappedOrders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(mappedOrders);

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                finalSort
        );
    }






}
