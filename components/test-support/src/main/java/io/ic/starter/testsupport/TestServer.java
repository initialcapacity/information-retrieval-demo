package io.ic.starter.testsupport;

import io.ic.starter.websupport.App;
import io.ic.starter.websupport.AppSetup;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Handler;

import java.util.ArrayList;
import java.util.List;

public class TestServer {
    public TestServer() {
        this(new ArrayList<>());
    }

    public TestServer(List<Response> responses) {
        this.responses = responses;
        this.app = new App(new TestServerSetup());
    }

    private final List<Call> receivedCalls = new ArrayList<>();
    private final List<Response> responses;
    private final App app;

    public List<Call> receivedCalls() {
        return receivedCalls;
    }

    public Call lastCall() {
        return receivedCalls.isEmpty() ? null : receivedCalls.getLast();
    }

    public record Response(String method, String path, int code, String body) {
        public Response(String method, String path, int code) {
            this(method, path, code, "");
        }

        public Response(String method, String path) {
            this(method, path, 200, "");
        }
    }

    public record Call(String method, String path, String body) {
    }

    public void start() {
        app.start(0);
        app.waitUntilStarted();
    }

    public String address() {
        return app.address("").toString();
    }

    public void stop() {
        app.stop();
    }

    public void add(Response response) {
        responses.add(response);
    }

    class TestServerSetup implements AppSetup {
        @Override
        public void configureServer(JavalinConfig javalinConfig) {
        }

        @Override
        public void configureEndpoints(Javalin javalin) {
            Handler handle = ctx -> {
                receivedCalls.add(new Call(ctx.method().name(), ctx.path(), ctx.body()));

                for (var response : responses) {
                    if (response.method().equals(ctx.method().name()) && response.path().equals(ctx.path())) {
                        ctx.status(response.code());
                        ctx.result(response.body());
                        return;
                    }
                }

                ctx.status(404);
                ctx.result("Not found");
            };
            javalin.get("*", handle);
            javalin.post("*", handle);
            javalin.put("*", handle);
            javalin.patch("*", handle);
            javalin.options("*", handle);
            javalin.head("*", handle);
            javalin.delete("*", handle);
        }
    }
}
