package com.darcode.day02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("naive")
class NaiveWithdrawalTest extends AbstractWithdrawalApiTest {

    /** Documents the bug: this test passes because it asserts that debits really are lost. */
    @Test
    void concurrentWithdrawals_loseDebits_documentsTheBug() throws Exception {
        Outcome outcome = withdrawConcurrently();

        assertThat(outcome.accepted()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(lostDebits(outcome)).isPositive();
    }
}