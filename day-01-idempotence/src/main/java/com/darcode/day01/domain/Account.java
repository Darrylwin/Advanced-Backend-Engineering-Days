package com.darcode.day01.domain;

import com.darcode.day01.exception.BusinessException;
import com.darcode.day01.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private Long id;

    @Column(nullable = false)
    private long balanceCents;

    protected Account() {
    }

    public Account(Long id, long balanceCents) {
        this.id = id;
        this.balanceCents = balanceCents;
    }

    /**
     * The invariant "a balance never goes negative" lives here, not in the service.
     */
    public void debit(long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("The amount to debit can't be negative");
        }
        if (balanceCents < amountCents) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds on account " + id);
        }
        balanceCents -= amountCents;
    }

    public Long getId() {
        return id;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}
