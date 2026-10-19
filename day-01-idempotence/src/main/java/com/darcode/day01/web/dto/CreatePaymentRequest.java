package com.darcode.day01.web.dto;

import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
        @Positive long accountId,
        @Positive long amountCents
) {
}
