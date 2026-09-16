package io.ic.starter.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed fields avoid ambiguous concatenations when hashing database rows. */
final class Fingerprint {
    private final MessageDigest digest;

    Fingerprint() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    void add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    String finish() {
        return HexFormat.of().formatHex(digest.digest());
    }

    static String file(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            return stream(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String stream(InputStream input) throws IOException {
        var hash = new Fingerprint();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            hash.digest.update(buffer, 0, count);
        }
        return hash.finish();
    }
}
