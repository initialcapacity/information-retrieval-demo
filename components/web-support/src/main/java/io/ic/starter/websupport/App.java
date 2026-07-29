package io.ic.starter.websupport;

import io.javalin.Javalin;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class App {
    private final Javalin javalin;
    private final AppSetup setup;
    CompletableFuture<Void> startedFuture = new CompletableFuture<>();

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(App.class);

    public App(AppSetup setup) {
        this.setup = setup;
        javalin = Javalin.create(javalinConfig -> {
            javalinConfig.startup.showJavalinBanner = false;
            javalinConfig.events.serverStarted(() -> startedFuture.complete(null));
            this.setup.configureServer(javalinConfig);
            this.setup.configureEndpoints(javalinConfig.routes);
        });
    }

    public void start(int port) {
        Thread.ofVirtual().start(() -> javalin.start(port));
    }

    public void startAndBlock(int port) {
        javalin.start(port);
    }

    public void waitUntilStarted() {
        startedFuture.join();
    }

    public URI address(String path) {
        return URI.create("http://localhost:" + javalin.port() + path);
    }

    public void stop() {
        javalin.stop();
        try {
            setup.close();
        } catch (IOException e) {
            logger.error("Error closing app configuration: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
