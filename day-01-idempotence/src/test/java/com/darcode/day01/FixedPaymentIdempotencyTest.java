package com.darcode.day01;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("fixed")
class FixedPaymentIdempotencyTest extends AbstractPaymentApiTest {

    @Test
    void doubleClick_isChargedOnce_andBothCallersReceiveTheSamePayment() throws Exception {
        List<HttpResponse<String>> responses = postTwiceConcurrently("key-1", PAYLOAD);

        assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(201);
        assertThat(responses.get(0).body()).isEqualTo(responses.get(1).body());
        assertThat(responses)
                .extracting(r -> r.headers().firstValue("Idempotent-Replayed").isPresent())
                .containsExactlyInAnyOrder(true, false);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS - AMOUNT_CENTS);
    }

    @Test
    void sameKeyWithDifferentPayload_isRejected() {
        post("key-2", PAYLOAD);
        HttpResponse<String> second = post("key-2", "{\"accountId\":1,\"amountCents\":20000}");

        assertThat(second.statusCode()).isEqualTo(422);
        assertThat(second.body()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(payments.count()).isEqualTo(1);
    }

    @Test
    void missingKey_isRejected() {
        HttpResponse<String> response = post(null, PAYLOAD);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(payments.count()).isZero();
    }
}