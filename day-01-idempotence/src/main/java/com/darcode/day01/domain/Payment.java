package com.darcode.day01.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(
                name = Payment.UNIQUE_IDEMPOTENCY_KEY,
                columnNames = "idempotency_key"
        )
)
public class Payment {

    public static final String UNIQUE_IDEMPOTENCY_KEY = "uq_payments_idempotency_key";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private long accountId;

    @Column(nullable = false, updatable = false)
    private long amountCents;

    @Column(nullable = false, updatable = false)
    private long balanceAfterCents;

    @Column(updatable = false, length = 100)
    private String idempotencyKey;

    @Column(updatable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public static Payment create(long accountId, long amountCents, long balanceAfterCents,
                                 String idempotencyKey, String requestFingerprint) {
        Payment payment = new Payment();
        payment.accountId = accountId;
        payment.amountCents = amountCents;
        payment.balanceAfterCents = balanceAfterCents;
        payment.idempotencyKey = idempotencyKey;
        payment.requestFingerprint = requestFingerprint;

        return payment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
