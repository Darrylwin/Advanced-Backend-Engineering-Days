package com.darcode.day02.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Mapping WITHOUT a version column: nothing protects a read-modify-write from a concurrent writer.
 * Used by the naive, pessimistic and atomic strategies.
 */
@Entity
@Table(name = "accounts")
public class Account extends BaseAccount {

    protected Account() {
    }

    public Account(Long id, long balanceCents) {
        super(id, balanceCents);
    }
}
