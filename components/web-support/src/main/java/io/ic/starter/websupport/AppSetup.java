package io.ic.starter.websupport;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;

import java.io.Closeable;
import java.io.IOException;

public interface AppSetup extends Closeable {
    void configureServer(JavalinConfig javalinConfig);

    void configureEndpoints(Javalin javalin);

    @Override
    default void close() throws IOException {
    }
}
