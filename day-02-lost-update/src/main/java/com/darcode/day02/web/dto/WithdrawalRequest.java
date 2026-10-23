package com.darcode.day02.web.dto;

import jakarta.validation.constraints.Positive;

public record WithdrawalRequest(
        @Positive long amountCents
) {
}
