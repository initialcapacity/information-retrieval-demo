package io.ic.starter.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;
import java.util.Optional;

import static io.ic.starter.testsupport.CookieSupport.cookieSigner;
import static io.ic.starter.testsupport.CookieSupport.parseCookie;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Assertions {
    public static void assertJsonEquals(String expected, String actual, String message) {
        var mapper = new ObjectMapper();

        try {
            var expectedJson = mapper.readTree(expected);
            var actualJson = mapper.readTree(actual);

            assertEquals(expectedJson, actualJson, message);
        } catch (Exception e) {
            if (message == null) {
                fail("Failed to parse JSON: " + e.getMessage());
            } else {
                fail("Failed to parse JSON: " + e.getMessage() + " (" + message + ")");
            }
        }
    }

    public static void assertJsonEquals(String expected, String actual) {
        assertJsonEquals(expected, actual, null);
    }

    public static <T> T assertPresent(Optional<T> optional, String message) {
        if (optional.isEmpty()) {
            if (message == null) {
                fail("Expected value to be present");
            } else {
                fail("Expected value to be present: " + message);
            }
        }
        return optional.get();
    }

    public static <T> T assertPresent(Optional<T> optional) {
        return assertPresent(optional, null);
    }

    public static void assertCookieEquals(HttpResponse<?> response, String name, String expectedCookieValue) {
        var cookie = assertPresent(parseCookie(response, name), "Cookie " + name + " was expected to be present but was not found");
        String value = assertPresent(cookieSigner().validate(cookie), "Cookie " + name + " was not signed properly");
        assertEquals(expectedCookieValue, value);
    }
}
