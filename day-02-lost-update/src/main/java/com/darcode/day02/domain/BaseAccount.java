package com.darcode.day02.domain;

import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * State and rules shared by both mappings of the "accounts" table.
 * The invariant "a balance never goes negative" lives here, once.
 */
@MappedSuperclass
public abstract class BaseAccount {

    @Id
    private Long id;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    protected BaseAccount() {
        // Required by JPA.
    }

    protected BaseAccount(Long id, long balanceCents) {
        this.id = id;
        this.balanceCents = balanceCents;
    }

    public void debit(long amountCents) {
        if (amountCents <= 0)
            throw new IllegalArgumentException("The amount to debit must be positive");

        if (balanceCents < amountCents)
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds on account " + id);

        balanceCents -= amountCents;
    }

    public Long getId() {
        return id;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}