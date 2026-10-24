package com.darcode.day02.service;

import com.darcode.day02.domain.Account;
import com.darcode.day02.exception.BusinessException;
import com.darcode.day02.repository.AccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The bug: read, compute in memory, write back. Every transaction "succeeds", yet debits are lost. */
@Service
@Profile("naive")
public class NaiveWithdrawalService implements WithdrawalService {

    private final AccountRepository accounts;
    private final SimulatedProcessing processing;

    NaiveWithdrawalService(AccountRepository accounts, SimulatedProcessing processing) {
        this.accounts = accounts;
        this.processing = processing;
    }

    @Override
    @Transactional
    public void withdraw(long accountId, long amountCents) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> BusinessException.accountNotFound(accountId));

        processing.pause();

        // Dirty checking turns this into "UPDATE accounts SET balance_cents = <absolute value> WHERE id = ?".
        // The value was computed from a read that another transaction may have made obsolete.
        account.debit(amountCents);
    }
}
