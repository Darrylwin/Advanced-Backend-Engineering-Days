package com.darcode.day01;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.darcode.day01.domain.Account;
import com.darcode.day01.repository.AccountRepository;
import com.darcode.day01.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class AbstractPaymentApiTest {

    protected static final long ACCOUNT_ID = 1L;
    protected static final long INITIAL_BALANCE_CENTS = 100_000L;
    protected static final long AMOUNT_CENTS = 10_000L;
    protected static final String PAYMENTS_PATH = "/api/v1/payments";
    protected static final String PAYLOAD = "{\"accountId\":1,\"amountCents\":10000}";

    @Autowired
    protected TestRestTemplate http;

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

    protected ResponseEntity<String> post(String idempotencyKey, String body) {
        return http.postForEntity(PAYMENTS_PATH, new HttpEntity<>(body, headers(idempotencyKey)), String.class);
    }

    /**
     * Fires two identical requests released at the same moment, like a double click.
     */
    protected List<ResponseEntity<String>> postTwiceConcurrently(String idempotencyKey, String body)
            throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return post(idempotencyKey, body);
                }));
            }

            startGate.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get());
            }
            return responses;

        } finally {
            pool.shutdown();
        }
    }

    protected long balance() {
        return accounts.findById(ACCOUNT_ID).orElseThrow().getBalanceCents();
    }

    private static HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }
}
