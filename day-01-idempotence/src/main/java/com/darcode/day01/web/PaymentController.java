package com.darcode.day01.web;

import com.darcode.day01.service.CreatePaymentCommand;
import com.darcode.day01.service.PaymentResult;
import com.darcode.day01.service.PaymentService;
import com.darcode.day01.web.dto.CreatePaymentRequest;
import com.darcode.day01.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        PaymentResult result = paymentService.create(
                new CreatePaymentCommand(request.accountId(), request.amountCents(), idempotencyKey));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }

        return response.body(PaymentResponse.from(result.payment()));
    }
}
