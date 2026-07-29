package t.ic.starter.websupport.cookies;

import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.ic.starter.websupport.cookies.SignedCookie;
import io.ic.starter.websupport.cookies.Signer;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class SignedCookieTest {
    private final SignedCookie signedCookie = new SignedCookie("some-cookie", new Signer("some-secret-that-is-reeeeeaaaaaaaaaalllllllly-long"));
    private final App app = new App(new AppSetup() {
        @Override
        public void configureServer(JavalinConfig javalinConfig) {
        }

        @Override
        public void configureEndpoints(RoutesConfig routes) {
            routes.get("/set", ctx -> {
                var value = ctx.queryParam("value");
                signedCookie.set(ctx, value);
            });
            routes.get("/get", ctx -> {
                var value = signedCookie.get(ctx);
                ctx.result(value.orElse(""));
            });
            routes.get("/clear", signedCookie::clear);
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
    void testCookies() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var setRequest = HttpRequest.newBuilder(app.address("/set?value=hello")).GET().build();
            var setResponse = client.send(setRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, setResponse.statusCode());

            var cookiesString = setResponse.headers().firstValue("Set-Cookie").orElse("");
            var someCookie = cookiesString.split(";")[0] + ";";
            assertNotEquals("some-cookie=hello;", someCookie);

            var getRequest = HttpRequest
                    .newBuilder(app.address("/get"))
                    .header("Cookie", cookiesString)
                    .GET()
                    .build();
            var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode());
            assertEquals("hello", getResponse.body());
        }
    }

    @Test
    void testCookies_tampered() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var getRequest = HttpRequest
                    .newBuilder(app.address("/get"))
                    .header("Cookie", "set-cookie=hello;")
                    .GET()
                    .build();
            var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResponse.statusCode());
            assertEquals("", getResponse.body());
        }
    }

    @Test
    void testCookies_clear() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var setRequest = HttpRequest.newBuilder(app.address("/set?value=hello")).GET().build();
            var setResponse = client.send(setRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, setResponse.statusCode());
            var cookiesString = setResponse.headers().firstValue("Set-Cookie").orElse("");
            var someCookie = cookiesString.split(";")[0] + ";";
            assertNotEquals("some-cookie=hello;", someCookie);

            var clearRequest = HttpRequest
                    .newBuilder(app.address("/clear"))
                    .GET()
                    .header("Cookie", someCookie)
                    .build();
            var clearResponse = client.send(clearRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, clearResponse.statusCode());
            var clearedCookiesString = clearResponse.headers().firstValue("Set-Cookie").orElse("");
            assertTrue(clearedCookiesString.startsWith("some-cookie=;"));
        }
    }
}
