package com.darcode.day01;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import com.darcode.day01.domain.Account;
import com.darcode.day01.repository.AccountRepository;
import com.darcode.day01.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class AbstractPaymentApiTest {

    protected static final long ACCOUNT_ID = 1L;
    protected static final long INITIAL_BALANCE_CENTS = 100_000L;
    protected static final long AMOUNT_CENTS = 10_000L;
    protected static final String PAYMENTS_PATH = "/api/v1/payments";
    protected static final String PAYLOAD = "{\"accountId\":1,\"amountCents\":10000}";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected AccountRepository accounts;

    @Autowired
    protected PaymentRepository payments;

    @BeforeEach
    void seedAccount() {
        payments.deleteAllInBatch();
        accounts.deleteAllInBatch();
        accounts.save(new Account(ACCOUNT_ID, INITIAL_BALANCE_CENTS));
    }

    protected HttpResponse<String> post(String idempotencyKey, String body) {
        HttpRequest.Builder request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + PAYMENTS_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }

        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the API", e);
        }
    }

    /**
     * Fires two identical requests released at the same moment, like a double click.
     */
    protected List<HttpResponse<String>> postTwiceConcurrently(String idempotencyKey, String body)
            throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return post(idempotencyKey, body);
                }));
            }

            startGate.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get());
            }
            return responses;

        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    protected long balance() {
        return accounts.findById(ACCOUNT_ID).orElseThrow().getBalanceCents();
    }
}
