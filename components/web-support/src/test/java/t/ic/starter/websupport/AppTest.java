package t.ic.starter.websupport;

import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTest {
    static class TestSetup implements AppSetup {
        @Override
        public void configureServer(JavalinConfig javalinConfig) {
        }

        @Override
        public void configureEndpoints(Javalin javalin) {
            javalin.get("/", ctx -> ctx.result("Test App"));
        }
    }

    private final App app = new App(new TestSetup());

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
    void testIndex() throws IOException, InterruptedException {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(app.address("/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("Test App", response.body());
        }
    }
}
