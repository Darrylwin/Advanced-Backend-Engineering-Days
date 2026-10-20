package com.darcode.day01;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("broken")
class BrokenPaymentTest extends AbstractPaymentApiTest {

    /** Documents the bug: this test passes because it asserts the double charge actually happens. */
    @Test
    void doubleClick_isChargedTwice_documentsTheBug() throws Exception {
        postTwiceConcurrently("key-1", PAYLOAD);

        assertThat(payments.count()).isEqualTo(2);
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS - 2 * AMOUNT_CENTS);
    }
}
