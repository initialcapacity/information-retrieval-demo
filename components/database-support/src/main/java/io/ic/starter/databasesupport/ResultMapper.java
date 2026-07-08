package io.ic.starter.databasesupport;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultMapper<T> {
    T accept(ResultSet results) throws SQLException;
}
