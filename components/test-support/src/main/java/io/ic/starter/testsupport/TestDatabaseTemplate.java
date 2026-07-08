package io.ic.starter.testsupport;

import io.ic.starter.databasesupport.DatabaseTemplate;

import javax.sql.DataSource;

public class TestDatabaseTemplate extends DatabaseTemplate {
    protected TestDatabaseTemplate(DataSource dataSource) {
        super(dataSource);
    }

    public void clear() {
        execute("truncate products");
    }
}
