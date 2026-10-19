package com.darcode.day01.service;

import com.darcode.day01.domain.Payment;

public record PaymentResult(
        Payment payment,
        boolean replayed
) {
}
