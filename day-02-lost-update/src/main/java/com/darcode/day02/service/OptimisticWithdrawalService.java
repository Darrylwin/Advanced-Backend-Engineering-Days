package com.darcode.day02.service;

import com.darcode.day02.config.DemoProperties;
import com.darcode.day02.domain.VersionedAccount;
import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.exception.ErrorCode;
import com.darcode.day02.repository.VersionedAccountRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;

/**
 * No lock: each attempt reads the account with its version and writes only if the version is
 * unchanged. A conflict means another transaction committed first, so the whole attempt
 * (read included) is replayed in a fresh transaction.
 */
@Service
@Profile("optimistic")
public class OptimisticWithdrawalService implements WithdrawalService {

    private final VersionedAccountRepository accounts;
    private final SimulatedProcessing processing;
    private final TransactionTemplate transaction;
    private final int maxAttempts;

    OptimisticWithdrawalService(
            VersionedAccountRepository accounts,
            SimulatedProcessing processing,
            TransactionTemplate transaction,
            DemoProperties properties
    ) {
        this.accounts = accounts;
        this.processing = processing;
        this.transaction = transaction;
        this.maxAttempts = properties.optimisticMaxAttempts();
    }

    // Deliberately NOT @Transactional: each attempt must run in its own transaction. A call to a
    // @Transactional method of this same class would bypass the Spring proxy and open none.
    @Override
    public void withdraw(long accountId, long amountCents) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                transaction.executeWithoutResult(status -> debitOne(accountId, amountCents));
                return;
            } catch (OptimisticLockException conflict) {
                backOff();
            }
        }
        throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                "The account was modified concurrently " + maxAttempts + " times in a row. Retry later.");
    }

    private void debitOne(long accountId, long amountCents) {
        VersionedAccount account = accounts.findById(accountId)
                .orElseThrow(() -> BusinessException.accountNotFound(accountId));

        processing.pause();

        // At commit: UPDATE accounts SET balance_cents = ?, version = ? WHERE id = ? AND version = ?
        // If that updates 0 rows, Hibernate raises an optimistic locking failure.
        account.debit(amountCents);
    }

    /** Random pause so that colliding callers do not retry in lockstep and collide again. */
    private void backOff() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off");
        }
    }
}
