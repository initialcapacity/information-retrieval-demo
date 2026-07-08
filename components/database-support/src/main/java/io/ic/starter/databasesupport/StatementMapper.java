package io.ic.starter.databasesupport;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface StatementMapper<T> {
    T accept(PreparedStatement statement) throws SQLException;
}
