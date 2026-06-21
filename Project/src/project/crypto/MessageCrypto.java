package project.crypto;

import homework.Publication;
import homework.Subscription;
import homework.SubscriptionCondition;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class MessageCrypto {

    private static final long QUANTIZATION = 1_000_000L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String FIELD_SEPARATOR = ":";

    private final byte[] hmacKey;
    private final long opeSlope;
    private final long opeIntercept;

    public MessageCrypto(String passphrase) {
        byte[] digest = sha256(passphrase);
        this.hmacKey = digest;
        this.opeSlope = Math.floorMod(readLong(digest, 0), 1000L) + 1L;
        this.opeIntercept = Math.floorMod(readLong(digest, 8), 1_000_000L);
    }

    public boolean isTextField(String field) {
        return field.equals("company") || field.equals("date");
    }

    public String equalityToken(String field, String value) {
        byte[] mac = hmac(field + FIELD_SEPARATOR + value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac).substring(0, 16);
    }

    public long orderToken(double value) {
        long scaled = Math.round(value * QUANTIZATION);
        return opeSlope * scaled + opeIntercept;
    }

    public Publication encrypt(Publication publication) {
        return new Publication(
                equalityToken("company", publication.getCompany()),
                orderToken(publication.getValue()),
                orderToken(publication.getDrop()),
                orderToken(publication.getVariation()),
                equalityToken("date", publication.getDate()));
    }

    public Subscription encrypt(Subscription subscription) {
        List<SubscriptionCondition> encrypted = new ArrayList<>();
        for (SubscriptionCondition condition : subscription.getConditions()) {
            String field = condition.getFieldName();
            String operator = condition.getOperator();
            String rendered = condition.getRenderedValue();
            if (isTextField(field)) {
                encrypted.add(SubscriptionCondition.text(
                        field, operator, equalityToken(field, stripQuotes(rendered))));
            } else {
                long code = orderToken(Double.parseDouble(rendered));
                encrypted.add(SubscriptionCondition.number(field, operator, code, 0));
            }
        }
        return new Subscription(encrypted);
    }

    private byte[] hmac(String input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC failure", exception);
        }
    }

    private static byte[] sha256(String passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static long readLong(byte[] bytes, int offset) {
        long value = 0L;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (bytes[offset + index] & 0xFFL);
        }
        return value;
    }

    private static String stripQuotes(String rendered) {
        if (rendered.length() >= 2
                && rendered.charAt(0) == '"'
                && rendered.charAt(rendered.length() - 1) == '"') {
            return rendered.substring(1, rendered.length() - 1);
        }
        return rendered;
    }
}
