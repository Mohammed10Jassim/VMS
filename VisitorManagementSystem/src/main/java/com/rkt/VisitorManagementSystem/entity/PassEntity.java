package com.rkt.VisitorManagementSystem.entity;

import com.rkt.VisitorManagementSystem.entity.enums.PassStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "passes",
        indexes = {
                @Index(name = "idx_passes_visitor", columnList = "visitor_id"),
                @Index(name = "idx_passes_qr_nonce", columnList = "qr_nonce"),
                @Index(name = "idx_passes_visitor_status", columnList = "visitor_id,status")
        }
)
@Getter @Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class PassEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "visitor_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pass_visitor"))
    private VisitorEntity visitor;

    /** All Instants are stored in UTC (hibernate.jdbc.time_zone=UTC already set). */
    @Column(name = "issued_at", nullable = false, columnDefinition = "datetime(3)")
    private Instant issuedAt;

    @Column(name = "checkin_deadline", nullable = false, columnDefinition = "datetime(3)")
    private Instant checkinDeadline;

    @Column(name = "checkin_at", columnDefinition = "datetime(3)")
    private Instant checkinAt;                // nullable: set when scanned-in

    @Column(name = "checkout_at", columnDefinition = "datetime(3)")
    private Instant checkoutAt;               // nullable: set when scanned-out

    // ---- Admin override fields ----
    @Column(name = "updated_checkout_at", columnDefinition = "datetime(3)")
    private Instant updatedCheckoutAt;        // nullable: new latest checkout (<= office close)

    @Column(name = "checkout_update_reason", length = 500)
    private String checkoutUpdateReason;

    @Column(name = "checkout_updated_by_user_id")
    private Long checkoutUpdatedByUserId;

    @Column(name = "checkout_updated_by_name", length = 120)
    private String checkoutUpdatedByName;

    @Column(name = "checkout_updated_at", columnDefinition = "datetime(3)")
    private Instant checkoutUpdatedAt;        // nullable: when admin updated

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PassStatus status;                // ISSUED, EXPIRED, REVOKED

    @Column(name = "gate_no", length = 40, nullable = false)
    private String gateNo;

    @Column(name = "qr_nonce", length = 64, nullable = false)
    private String qrNonce;

    // ---- Auditing (optional but useful) ----
    @Column(name = "created_at", updatable = false, columnDefinition = "datetime(3)")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "datetime(3)")
    private Instant updatedAt;

    // ====== Lifecycle hooks: millisecond precision & audit ======
    @PrePersist
    private void onCreate() {
        this.createdAt = nowTruncated();
        this.updatedAt = this.createdAt;

        // standardize precision on all instants
        issuedAt           = t(issuedAt);
        checkinDeadline    = t(checkinDeadline);
        checkinAt          = t(checkinAt);
        checkoutAt         = t(checkoutAt);
        updatedCheckoutAt  = t(updatedCheckoutAt);
        checkoutUpdatedAt  = t(checkoutUpdatedAt);
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = nowTruncated();

        issuedAt           = t(issuedAt);
        checkinDeadline    = t(checkinDeadline);
        checkinAt          = t(checkinAt);
        checkoutAt         = t(checkoutAt);
        updatedCheckoutAt  = t(updatedCheckoutAt);
        checkoutUpdatedAt  = t(checkoutUpdatedAt);
    }

    private static Instant t(Instant i) {
        return i == null ? null : i.truncatedTo(ChronoUnit.MILLIS);
    }

    private static Instant nowTruncated() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // ====== Temporal sanity checks (service-layer rules, but guard here too) ======
    @AssertTrue(message = "issued_at must be set")
    private boolean _issuedNotNull() { return issuedAt != null; }

    @AssertTrue(message = "checkin_deadline must be after or equal to issued_at")
    private boolean _deadlineAfterIssue() {
        return issuedAt == null || checkinDeadline == null || !checkinDeadline.isBefore(issuedAt);
    }

    @AssertTrue(message = "check-in cannot be before issued_at")
    private boolean _checkinAfterIssue() {
        return issuedAt == null || checkinAt == null || !checkinAt.isBefore(issuedAt);
    }

    @AssertTrue(message = "checkout cannot be before check-in")
    private boolean _checkoutAfterCheckin() {
        return checkinAt == null || checkoutAt == null || !checkoutAt.isBefore(checkinAt);
    }

    @AssertTrue(message = "updated checkout cannot be earlier than checkout")
    private boolean _updatedCheckoutAfterCheckout() {
        return checkoutAt == null || updatedCheckoutAt == null || !updatedCheckoutAt.isBefore(checkoutAt);
    }
}
