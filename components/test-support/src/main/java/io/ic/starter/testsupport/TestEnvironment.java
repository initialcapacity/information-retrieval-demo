package io.ic.starter.testsupport;

import io.ic.starter.starterenv.Environment;

public class TestEnvironment {
    public static Environment create(String databaseName) {
        return new Environment(
                "jdbc:postgresql://localhost:5433/" + databaseName + "?user=postgres&password=postgres",
                0,
                "some-cookie-secret",
                ""
        );
    }
}
