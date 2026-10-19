package com.darcode.day01.service;

public interface PaymentService {
    PaymentResult create(CreatePaymentCommand command);
}
