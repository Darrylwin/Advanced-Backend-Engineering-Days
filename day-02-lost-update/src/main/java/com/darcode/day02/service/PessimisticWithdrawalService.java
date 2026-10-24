package com.darcode.day02.service;

import com.darcode.day02.domain.Account;
import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.repository.AccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("pessimistic")
public class PessimisticWithdrawalService implements WithdrawalService {

    private final AccountRepository accounts;
    private final SimulatedProcessing processing;

    PessimisticWithdrawalService(AccountRepository accounts, SimulatedProcessing processing) {
        this.accounts = accounts;
        this.processing = processing;
    }

    @Override
    @Transactional
    public void withdraw(long accountId, long amountCents) {
        // SELECT ... FOR UPDATE: the lock is held until this transaction ends, and the row returned
        // is the latest committed version, even if we had to wait for another transaction.
        Account account = accounts.findWithLockById(accountId)
                .orElseThrow(() -> BusinessException.accountNotFound(accountId));

        processing.pause();
        account.debit(amountCents);
    }
}
