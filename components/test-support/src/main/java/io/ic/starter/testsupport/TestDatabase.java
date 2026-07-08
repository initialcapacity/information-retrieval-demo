package io.ic.starter.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import io.ic.starter.databasesupport.DataSourceFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.Random;

public class TestDatabase implements Closeable {
    private static final String SUPER_URL =
            "jdbc:postgresql://localhost:5433/postgres?user=postgres&password=postgres";

    private final HikariDataSource dataSource;
    private final String databaseName;

    private TestDatabase(HikariDataSource dataSource, String databaseName) {
        this.dataSource = dataSource;
        this.databaseName = databaseName;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public String databaseName() {
        return databaseName;
    }

    public TestDatabaseTemplate template() {
        return new TestDatabaseTemplate(dataSource);
    }

    public static TestDatabase create() {
        try (var superDataSource = DataSourceFactory.create(SUPER_URL, 1)) {
            var superDatabaseTemplate = new TestDatabaseTemplate(superDataSource);

            var testDatabaseName = "dubjug_test_" + new Random().nextInt(10_000_000);
            superDatabaseTemplate.execute("create database " + testDatabaseName + " template dubjug_test");

            HikariDataSource dataSource = DataSourceFactory
                    .create("jdbc:postgresql://localhost:5433/" + testDatabaseName + "?user=postgres&password=postgres", 10);
            return new TestDatabase(dataSource, testDatabaseName);
        }
    }

    @Override
    public void close() {
        dataSource.close();

        try (var superDataSource = DataSourceFactory.create(SUPER_URL, 1)) {
            var superDatabaseTemplate = new TestDatabaseTemplate(superDataSource);
            superDatabaseTemplate.execute("drop database " + databaseName);
        }
    }
}
