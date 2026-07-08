package io.ic.starter.databasesupport;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface ParameterSetter {
    void accept(PreparedStatement statement) throws SQLException;
}
