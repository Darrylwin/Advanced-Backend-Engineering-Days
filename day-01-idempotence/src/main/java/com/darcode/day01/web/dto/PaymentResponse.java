package com.darcode.day01.web.dto;

import com.darcode.day01.domain.Payment;

import java.time.Instant;

public record PaymentResponse(
        long paymentId, long accountId, long amountCents,
        long balanceAfterCents, Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccountId(),
                payment.getAmountCents(),
                payment.getBalanceAfterCents(),
                payment.getCreatedAt()
        );
    }
}
