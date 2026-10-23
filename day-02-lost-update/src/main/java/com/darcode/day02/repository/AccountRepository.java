package com.darcode.day02.repository;

import com.darcode.day02.domain.Account;
import jakarta.persistence.LockModeType;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Loads the account with a row lock (SELECT ... FOR UPDATE). Must run inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(Long id);

    /**
     * Conditional debit executed entirely by the database: no read, no gap between read and write.
     * Returns the number of updated rows (1, or 0 if the account is missing or the balance too low).
     * The only handwritten query of the project: a conditional "balance = balance - x" cannot be
     * expressed with a derived query.
     */
    @Modifying
    @Query("update Account a set a.balanceCents = a.balanceCents - :amount "
            + "where a.id = :id and a.balanceCents >= :amount")
    int debitIfSufficient(@Param("id") long id, @Param("amount") long amount);
}