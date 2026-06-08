package com.pfe.back.infra.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time HMAC-SHA256 verification for webhook payloads.
 */
public final class HmacVerifier {

    private HmacVerifier() {}

    public static boolean verify(byte[] body, String headerSignature, String secret) {
        if (headerSignature == null || secret == null) return false;
        String expected = "sha256=" + computeHex(body, secret);
        return constantTimeEquals(expected.getBytes(StandardCharsets.UTF_8),
                headerSignature.getBytes(StandardCharsets.UTF_8));
    }

    public static String computeHex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(sig.length * 2);
            for (byte b : sig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}
