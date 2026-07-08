package t.ic.starter.websupport;

import freemarker.template.Version;
import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.rendering.template.JavalinFreemarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static io.ic.starter.websupport.FlashMessaging.addFlash;
import static io.ic.starter.websupport.FlashMessaging.renderWithFlash;
import static org.junit.jupiter.api.Assertions.*;

public class FlashMessagingTest {
    private final App app = new App(new AppSetup() {
        @Override
        public void configureServer(JavalinConfig javalinConfig) {
            var freemarkerConfig = new freemarker.template.Configuration(new Version(2, 3, 34));
            freemarkerConfig.setClassForTemplateLoading(JavalinFreemarker.class, "/templates");
            javalinConfig.fileRenderer(new JavalinFreemarker(freemarkerConfig));
        }

        @Override
        public void configureEndpoints(Javalin javalin) {
            javalin.get("/same-request", ctx -> {
                addFlash(ctx, "a message");
                addFlash(ctx, "another message");
                renderWithFlash(ctx, "show-flash.ftl");
            });
            javalin.get("/set", ctx -> {
                addFlash(ctx, "a message");
                addFlash(ctx, "another message");
                ctx.redirect("/read");
            });
            javalin.get("/read", ctx -> {
                renderWithFlash(ctx, "show-flash.ftl");
            });
            javalin.get("/read-with-other", ctx -> {
                renderWithFlash(ctx, "show-flash.ftl", Map.of("other", "some other message"));
            });
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
    void testFlash() throws IOException, InterruptedException {
        try (var client = httpClient()) {
            var request = HttpRequest.newBuilder(app.address("/same-request")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("a message"));
            assertTrue(response.body().contains("another message"));
        }
    }

    @Test
    void testFlash_NextRequest() throws IOException, InterruptedException {
        try (var client = httpClient()) {
            var setRequest = HttpRequest.newBuilder(app.address("/set")).GET().build();
            client.send(setRequest, HttpResponse.BodyHandlers.ofString());
            var readRequest = HttpRequest.newBuilder(app.address("/read")).GET().build();
            var response = client.send(readRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("a message"));
            assertTrue(response.body().contains("another message"));
        }
    }

    @Test
    void testFlash_NextRequestClears() throws IOException, InterruptedException {
        try (var client = httpClient()) {
            var setRequest = HttpRequest.newBuilder(app.address("/set")).GET().build();
            client.send(setRequest, HttpResponse.BodyHandlers.ofString());
            var readRequest = HttpRequest.newBuilder(app.address("/read")).GET().build();
            client.send(readRequest, HttpResponse.BodyHandlers.ofString());
            var response = client.send(readRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertFalse(response.body().contains("a message"));
            assertFalse(response.body().contains("another message"));
        }
    }

    @Test
    void testFlash_ExistingModel() throws IOException, InterruptedException {
        try (var client = httpClient()) {
            var setRequest = HttpRequest.newBuilder(app.address("/set")).GET().build();
            client.send(setRequest, HttpResponse.BodyHandlers.ofString());
            var readRequest = HttpRequest.newBuilder(app.address("/read-with-other")).GET().build();
            var response = client.send(readRequest, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("a message"));
            assertTrue(response.body().contains("another message"));
            assertTrue(response.body().contains("some other message"));
        }
    }

    HttpClient httpClient() {
        CookieManager cookieManager = new CookieManager();
        return HttpClient.newBuilder().cookieHandler(cookieManager).build();
    }
}
