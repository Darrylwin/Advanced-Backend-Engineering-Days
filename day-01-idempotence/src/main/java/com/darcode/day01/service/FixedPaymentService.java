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

import java.util.Optional;

/**
 * Idempotent implementation, active under the "fixed" profile.
 */
@Service
@Profile("fixed")
public class FixedPaymentService implements PaymentService {

    private static final int MAX_KEY_LENGTH = 100;

    private final AccountRepository accounts;
    private final PaymentRepository payments;

    FixedPaymentService(AccountRepository accounts, PaymentRepository payments) {
        this.accounts = accounts;
        this.payments = payments;
    }

    @Override
    @Transactional
    public PaymentResult create(CreatePaymentCommand command) {
        String key = requireKey(command.idempotencyKey());
        String fingerprint = RequestFingerprint.of(command);

        // Locking the account first serializes concurrent duplicates: the second caller only
        // proceeds once the first has committed, and then finds that payment by key.
        Account account = accounts.findWithLockById(command.accountId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + command.accountId()));

        Optional<Payment> existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        account.debit(command.amountCents());
        Payment payment = payments.save(Payment.create(
                account.getId(), command.amountCents(), account.getBalanceCents(), key, fingerprint));

        return new PaymentResult(payment, false);
    }

    private static PaymentResult replay(Payment existing, String fingerPrint) {
        if (!fingerPrint.equals(existing.getRequestFingerprint())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request.");
        }
        return new PaymentResult(existing, true);
    }

    private static String requireKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "A valid Idempotency-Key is required (1 to " + MAX_KEY_LENGTH + " characters).");
        }
        return key;
    }
}
