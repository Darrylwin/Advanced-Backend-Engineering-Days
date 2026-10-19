package com.darcode.day01.service;

import org.springframework.stereotype.Service;

@Service
public interface PaymentService {
    PaymentResult create(CreatePaymentCommand command);
}
