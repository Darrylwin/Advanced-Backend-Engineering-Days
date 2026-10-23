package com.darcode.day02;

import static org.assertj.core.api.Assertions.assertThat;

import com.darcode.day02.domain.Account;
import com.darcode.day02.exception.ErrorCode;
import com.darcode.day02.repository.AccountRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// A 100 ms processing delay makes overlapping transactions certain, even on a slow machine.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "demo.processing-delay=100ms")
@Import(TestcontainersConfiguration.class)
abstract class AbstractWithdrawalApiTest {

    protected static final long ACCOUNT_ID = 1L;
    protected static final long INITIAL_BALANCE_CENTS = 100_000L;
    protected static final long AMOUNT_CENTS = 1_000L;
    protected static final int CONCURRENT_REQUESTS = 20;

    protected record Outcome(long accepted, long rejected, long other) {
    }

    // HTTP/1.1 forced: a plain local server has no use for an HTTP/2 upgrade attempt.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected AccountRepository accounts;

    @BeforeEach
    void seedAccount() {
        accounts.deleteAllInBatch();
        accounts.save(new Account(ACCOUNT_ID, INITIAL_BALANCE_CENTS));
    }

    @Test
    void insufficientFunds_isRejected_andBalanceIsUntouched() {
        HttpResponse<String> response = withdraw(ACCOUNT_ID, INITIAL_BALANCE_CENTS + 1);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains(ErrorCode.INSUFFICIENT_FUNDS.name());
        assertThat(balance()).isEqualTo(INITIAL_BALANCE_CENTS);
    }

    @Test
    void unknownAccount_isRejected() {
        HttpResponse<String> response = withdraw(999L, AMOUNT_CENTS);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains(ErrorCode.ACCOUNT_NOT_FOUND.name());
    }

    protected HttpResponse<String> withdraw(long accountId, long amountCents) {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/api/v1/accounts/" + accountId + "/withdrawals"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"amountCents\":" + amountCents + "}"))
                .build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the API", e);
        }
    }

    /** Releases all the withdrawals at the same moment and counts the responses by status. */
    protected Outcome withdrawConcurrently() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return withdraw(ACCOUNT_ID, AMOUNT_CENTS).statusCode();
                }));
            }
            startGate.countDown();

            long accepted = 0;
            long rejected = 0;
            long other = 0;
            for (Future<Integer> future : futures) {
                int status = future.get();
                if (status == 204) {
                    accepted++;
                } else if (status == 409) {
                    rejected++;
                } else {
                    other++;
                }
            }
            return new Outcome(accepted, rejected, other);
        }
    }

    protected long balance() {
        return accounts.findById(ACCOUNT_ID).orElseThrow().getBalanceCents();
    }

    /** Accepted debits that never reached the balance. Zero means the invariant holds. */
    protected long lostDebits(Outcome outcome) {
        long expected = INITIAL_BALANCE_CENTS - outcome.accepted() * AMOUNT_CENTS;
        return (balance() - expected) / AMOUNT_CENTS;
    }
}