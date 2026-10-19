package com.darcode.day01.service;

public record CreatePaymentCommand(
        long accountId,
        long amountCents,
        String idempotencyKey
) {
}
