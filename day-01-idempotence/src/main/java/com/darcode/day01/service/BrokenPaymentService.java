package com.darcode.day01.service;

import com.darcode.day01.domain.Account;
import com.darcode.day01.domain.Payment;
import com.darcode.day01.exception.BusinessException;
import com.darcode.day01.exception.ErrorCode;
import com.darcode.day01.repository.AccountRepository;
import com.darcode.day01.repository.PaymentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately naive implementation, active only under the "broken" profile.
 * Every request is treated as a brand-new payment: the Idempotency-Key header is ignored.
 */
@Service
@Profile("broken")
public class BrokenPaymentService implements PaymentService {

    private final AccountRepository accounts;
    private final PaymentRepository payments;

    BrokenPaymentService(AccountRepository accounts, PaymentRepository payments) {
        this.accounts = accounts;
        this.payments = payments;
    }

    @Override
    @Transactional
    public PaymentResult create(CreatePaymentCommand command) {
        Account account = accounts.findWithLockById(command.accountId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + command.accountId()
                ));

        account.debit(command.amountCents());
        Payment payment = payments.save(Payment.create(
                account.getId(), command.amountCents(), account.getBalanceCents(), null, null
        ));

        return new PaymentResult(payment, false);
    }
}
