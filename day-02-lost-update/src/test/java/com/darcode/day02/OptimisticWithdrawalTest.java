package com.darcode.day02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("optimistic")
class OptimisticWithdrawalTest extends AbstractWithdrawalApiTest {

    @Test
    void concurrentWithdrawals_loseNothing_butSomeAreRejectedUnderContention() throws Exception {
        Outcome outcome = withdrawConcurrently();

        assertThat(lostDebits(outcome)).isZero();
        assertThat(outcome.other()).isZero();
        assertThat(outcome.accepted() + outcome.rejected()).isEqualTo(CONCURRENT_REQUESTS);
        assertThat(outcome.rejected()).isPositive();
    }
}