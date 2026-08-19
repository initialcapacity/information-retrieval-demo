package io.ic.starter.app;

import io.javalin.http.Context;

import java.util.function.BooleanSupplier;

public class HealthController {
    private final BooleanSupplier healthy;

    public HealthController(BooleanSupplier healthy) {
        this.healthy = healthy;
    }

    public void index(Context ctx) {
        boolean isHealthy = healthy.getAsBoolean();
        ctx.status(isHealthy ? 200 : 503).result(isHealthy ? "ok" : "unhealthy");
    }
}
