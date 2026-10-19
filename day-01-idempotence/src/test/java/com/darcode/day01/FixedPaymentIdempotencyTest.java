package com.darcode.day01;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("fixed")
class FixedPaymentIdempotencyTest extends AbstractPaymentApiTest {

    @Test
    void doubleClick_isChargedOnce_andBothCallersReceiveTheSamePayment() throws Exception {
        List<ResponseEntity<String>> responses = postTwiceConcurrently("key-1", PAYLOAD);

        assertThat(responses).extracting(r -> r.getStatusCode().value()).containsOnly(201);
        assertThat(responses.get(0).getBody()).isEqualTo(responses.get(1).getBody());
        assertThat(responses)
                .extracting(r -> r.getHeaders().toSingleValueMap().containsKey("Idempotent-Replayed"))
                .containsExactlyInAnyOrder(true, false);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS - AMOUNT_CENTS);
    }

    @Test
    void sameKeyWithDifferentPayload_isRejected() {
        post("key-2", PAYLOAD);
        ResponseEntity<String> second = post("key-2", "{\"accountId\":1,\"amountCents\":20000}");

        assertThat(second.getStatusCode().value()).isEqualTo(422);
        assertThat(second.getBody()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void missingKey_isRejected() {
        ResponseEntity<String> response = post(null, PAYLOAD);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(payments.count()).isZero();
    }
}