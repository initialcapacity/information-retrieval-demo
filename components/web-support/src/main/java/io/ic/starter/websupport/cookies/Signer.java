package io.ic.starter.websupport.cookies;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

public class Signer {
    private final Mac signer;

    public Signer(String secret) {
        try {
            signer = Mac.getInstance("HmacSHA256");
            signer.init(new SecretKeySpec(base64StringToBytes(secret), "HmacSHA256"));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public String sign(String value) {
        String encodedValue = base64BytesToString(value.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = base64BytesToString(signer.doFinal(value.getBytes(StandardCharsets.UTF_8)));

        return encodedValue + "." + encodedSignature;
    }

    public Optional<String> validate(String signedValue) {
        try {
            String[] parts = signedValue.split("\\.", 2);
            if (parts.length != 2) {
                return Optional.empty();
            }

            byte[] signature = base64StringToBytes(parts[1]);
            byte[] value = base64StringToBytes(parts[0]);

            byte[] expectedSignature = signer.doFinal(value);
            if (!MessageDigest.isEqual(signature, expectedSignature)) {
                return Optional.empty();
            }

            return Optional.of(new String(value, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String base64BytesToString(byte[] valueBytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valueBytes);
    }

    private static byte[] base64StringToBytes(String encodedValue) {
        return Base64.getUrlDecoder().decode(encodedValue);
    }
}
