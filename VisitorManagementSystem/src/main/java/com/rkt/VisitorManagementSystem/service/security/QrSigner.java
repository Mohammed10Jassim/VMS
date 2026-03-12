package com.rkt.VisitorManagementSystem.service.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public final class QrSigner {
    private QrSigner() {}

    /** Sign claims into compact token: base64url(payload).hex(hmac) */
    public static String sign(Map<String, String> claims, String secret, long ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        long ttl = ttlSeconds > 0 ? ttlSeconds : 1; // prevent zero/negatives
        long exp = now + ttl;

        StringJoiner sj = new StringJoiner("&");
        claims.forEach((k, v) -> sj.add(urlEncode(k) + "=" + urlEncode(v)));
        sj.add("iat=" + now);
        sj.add("exp=" + exp);
        String payload = sj.toString();

        String sigHex = hmacSha256Hex(payload, secret);
        return base64Url(payload) + "." + sigHex;
    }


    public static Map<String, String> verifyAndParse(String token, String secret) {
        Parsed p = parseAndVerify(token, secret);
        return p.map;
    }

    public static Map<String, Object> verify(String token, String secret) {
        Parsed p = parseAndVerify(token, secret);
        Map<String, Object> out = new LinkedHashMap<>();
        p.map.forEach(out::put);
        return out;
    }

    // ==== internals ====

    private static final class Parsed {
        final Map<String, String> map;
        Parsed(Map<String, String> map) { this.map = map; }
    }

    private static Parsed parseAndVerify(String token, String secret) {
        int dot = token.lastIndexOf('.');
        if (dot <= 0) throw new IllegalArgumentException("Invalid token");

        String payloadB64 = token.substring(0, dot);
        String sigHexGiven = token.substring(dot + 1);

        String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        String sigHexExpected = hmacSha256Hex(payload, secret);

        byte[] a = sigHexExpected.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] b = sigHexGiven.toLowerCase().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) throw new IllegalArgumentException("Bad signature");


        String[] parts = payload.split("&");
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String k = urlDecode(part.substring(0, eq));
                String v = urlDecode(part.substring(eq + 1));
                map.put(k, v);
            }
        }

        long now = Instant.now().getEpochSecond();
        long exp = Long.parseLong(map.getOrDefault("exp", "0"));
        if (now > exp) throw new IllegalArgumentException("Token expired");

        // (Optional) reject tokens with iat too far in the future (clock skew)
        long iat = Long.parseLong(map.getOrDefault("iat", String.valueOf(now)));
        if (iat - now > 300) { // >5 minutes in future
            throw new IllegalArgumentException("Token iat invalid");
        }

        return new Parsed(map);
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
