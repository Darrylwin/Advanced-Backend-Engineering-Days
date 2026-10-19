package com.darcode.day01.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Digest of what the client asked for. It lets the server detect an idempotency key reused
 * with a different request, which is almost certainly a client bug.
 */
public class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(CreatePaymentCommand command) {
        String canonical = command.accountId() + ":" + command.amountCents();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
