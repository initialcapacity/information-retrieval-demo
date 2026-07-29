package io.ic.starter.websupport;

import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;

import java.io.Closeable;
import java.io.IOException;

public interface AppSetup extends Closeable {
    void configureServer(JavalinConfig javalinConfig);

    void configureEndpoints(RoutesConfig routes);

    @Override
    default void close() throws IOException {
    }
}
