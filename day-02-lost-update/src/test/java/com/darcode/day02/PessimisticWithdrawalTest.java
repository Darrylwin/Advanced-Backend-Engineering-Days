package com.darcode.day02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("pessimistic")
class PessimisticWithdrawalTest extends AbstractWithdrawalApiTest {

    @Test
    void concurrentWithdrawals_areSerialized_andAllApplied() throws Exception {
        Outcome outcome = withdrawConcurrently();

        assertThat(outcome.accepted()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(lostDebits(outcome)).isZero();
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS - CONCURRENT_REQUESTS * AMOUNT_CENTS);
    }
}