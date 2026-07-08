package t.ic.starter.websupport.cookies;

import io.ic.starter.websupport.cookies.Signer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignerTest {
    @Test
    void testSign() {
        var signer = new Signer("some-secret-that-is-reeeeeaaaaaaaaaalllllllly-long");
        var signedValue = signer.sign("test-value");

        assertTrue(signedValue.contains("."));

        var result = signer.validate(signedValue);

        assertEquals("test-value", result.orElse(null));
    }

    @Test
    void testValidate() {
        var signer = new Signer("some-secret-that-is-reeeeeaaaaaaaaaalllllllly-long");
        var signedValue = signer.sign("test-value");

        assertEquals(Optional.empty(), signer.validate(signedValue.substring(1, signedValue.length() - 1)));
        assertEquals(Optional.empty(), signer.validate("a" + signedValue.substring(1, signedValue.length() - 1)));
        assertEquals(Optional.empty(), signer.validate("something"));
    }
}
