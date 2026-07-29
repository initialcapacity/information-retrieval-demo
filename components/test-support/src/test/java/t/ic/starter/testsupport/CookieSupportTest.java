package t.ic.starter.testsupport;

import io.ic.starter.testsupport.CookieSupport;
import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.ic.starter.websupport.cookies.Signer;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CookieSupportTest {
    private final Signer signer = CookieSupport.cookieSigner();
    private final App app = new App(new AppSetup() {
        @Override
        public void configureServer(JavalinConfig javalinConfig) {
        }

        @Override
        public void configureEndpoints(RoutesConfig routes) {
            routes.get("/set-cookie", ctx -> {
                ctx.cookie("starter-session", signer.sign("session-payload"));
                ctx.cookie("other-cookie", "abc");
            });
            routes.get("/no-cookies", ctx -> ctx.result("ok"));
        }
    });

    @BeforeEach
    void setUp() {
        app.start(0);
        app.waitUntilStarted();
    }

    @AfterEach
    void tearDown() {
        app.stop();
    }

    @Test
    void testParseCookie() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder(app.address("/set-cookie")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            var sessionCookie = CookieSupport.parseCookie(response, "starter-session").orElseThrow();
            assertEquals("session-payload", signer.validate(sessionCookie).orElseThrow());
        }
    }

    @Test
    void testParseCookie_missing() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder(app.address("/set-cookie")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertTrue(CookieSupport.parseCookie(response, "not-present").isEmpty());
        }
    }

    @Test
    void testParseCookie_noSetCookieHeaders() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder(app.address("/no-cookies")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            assertTrue(CookieSupport.parseCookie(response, "starter-session").isEmpty());
        }
    }

    @Test
    void testCookie() {
        assertEquals("starter-session=value;", CookieSupport.cookie("starter-session", "value"));
    }

    @Test
    void testCookie_withAnyNameAndValue() {
        assertEquals("name=some-value;", CookieSupport.cookie("name", "some-value"));
    }

    @Test
    void testRequestWithCookie() {
        var uri = URI.create("http://localhost/path");
        var request = CookieSupport.requestWithCookie(uri, "starter-session=abc;").GET().build();

        assertEquals("starter-session=abc;", request.headers().firstValue("Cookie").orElseThrow());
    }

    @Test
    void testRequestWithCookie_multipleCookies() {
        var uri = URI.create("http://localhost/path");
        var request = CookieSupport.requestWithCookie(uri, "one=a; two=b;").GET().build();

        assertEquals("one=a; two=b;", request.headers().firstValue("Cookie").orElseThrow());
    }
}
