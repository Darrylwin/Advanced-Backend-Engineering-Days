package com.darcode.day02.service;

import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.exception.ErrorCode;
import com.darcode.day02.repository.AccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database does the arithmetic: there is no read, hence no gap in which another transaction
 * could slip in. The business rule (sufficient funds) moves into the WHERE clause.
 */
@Service
@Profile("atomic")
public class AtomicWithdrawalService implements WithdrawalService {

    private final AccountRepository accounts;

    AtomicWithdrawalService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void withdraw(long accountId, long amountCents) {
        if (accounts.debitIfSufficient(accountId, amountCents) == 1) {
            return;
        }
        // Zero updated : tell the caller why.
        if (!accounts.existsById(accountId)) {
            throw BusinessException.accountNotFound(accountId);
        }
        throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Insufficient funds on account " + accountId);
    }
}
