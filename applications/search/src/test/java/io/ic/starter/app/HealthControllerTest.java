package io.ic.starter.app;

import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {
    @Test
    void returnsServiceUnavailableWhenTheDependencyIsUnhealthy() throws Exception {
        var controller = new HealthController(() -> false);
        var app = new App(new AppSetup() {
            @Override
            public void configureServer(JavalinConfig config) {
            }

            @Override
            public void configureEndpoints(RoutesConfig routes) {
                routes.get("/health", controller::index);
            }
        });
        app.start(0);
        app.waitUntilStarted();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(
                    HttpRequest.newBuilder(app.address("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(503, response.statusCode());
            assertEquals("unhealthy", response.body());
        } finally {
            app.stop();
        }
    }
}
