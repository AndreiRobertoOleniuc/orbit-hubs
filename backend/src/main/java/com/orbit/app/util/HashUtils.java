package com.orbit.app.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared hashing utilities for content dedup and idempotency keys.
 */
public final class HashUtils {

    private HashUtils() {}

    /**
     * Compute the SHA-256 hash of a text string and return it as a hex string.
     */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compute the SHA-256 hash of raw bytes and return it as a hex string.
     */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}