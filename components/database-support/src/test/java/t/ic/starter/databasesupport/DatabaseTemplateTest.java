package t.ic.starter.databasesupport;

import io.ic.starter.databasesupport.DatabaseTemplate;
import io.ic.starter.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseTemplateTest {
    private final TestDatabase testDb = TestDatabase.create();
    private final DatabaseTemplate template = new DatabaseTemplate(testDb.dataSource());

    @BeforeEach
    void setUp() {
        template.execute("drop table if exists numbers");
        template.execute("create table numbers as select generate_series(1, 5) as value");
    }

    @AfterEach
    void tearDown() {
        testDb.close();
    }

    @Test
    void testExecute() {
        assertEquals(-1, template.execute("select * from numbers"));
        assertEquals(-1, template.execute("select * from numbers where value = ?", statement -> statement.setInt(1, 1)));
    }

    @Test
    void testQuery() {
        var result = template.query(
                "select 'hello' as message",
                rs -> rs.getString("message")
        );
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void testQuery_notFound() {
        var result = template.query(
                "select * from numbers where value = 42",
                rs -> rs.getString("value")
        );
        assertFalse(result.isPresent());
    }

    @Test
    void testQuery_withParameters() {
        var result = template.query(
                "select ? as message",
                statement -> statement.setString(1, "hello"),
                rs -> rs.getString("message")
        );
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void testQueryList() {
        var result = template.queryList(
                "select concat('hello', sequence.value) as message from generate_series(1, 5) as sequence(value)",
                rs -> rs.getString("message")
        );
        assertEquals(List.of("hello1", "hello2", "hello3", "hello4", "hello5"), result);
    }

    @Test
    void testQueryList_withParameters() {
        var result = template.queryList(
                "select concat(?, sequence.value) as message from generate_series(1, 5) as sequence(value)",
                statement -> statement.setString(1, "hello"),
                rs -> rs.getString("message")
        );
        assertEquals(List.of("hello1", "hello2", "hello3", "hello4", "hello5"), result);
    }

    @Test
    void testInTransaction() {
        var result = template.inTransaction(connection -> {
            template.execute("create temporary table temp_numbers(value int) on commit drop", connection);
            assertEquals(5, template.execute("insert into temp_numbers values (101), (102), (103), (104), (105)", connection));
            return template.queryList("select value from temp_numbers", rs -> rs.getInt("value"), connection);
        });

        assertEquals(List.of(101, 102, 103, 104, 105), result);

        assertThrows(RuntimeException.class, () -> template.execute("select * from  temp_numbers"), "Transaction was not committed");
    }

    @Test
    void testInTransaction_Rollback() {
        assertThrows(RuntimeException.class, () -> template.inTransaction(connection -> {
            assertEquals(5, template.execute("insert into numbers values (101), (102), (103), (104), (105)", connection));
            return template.execute("this is a bad statement");
        }));

        var result = template.queryList("select * from numbers", rs -> rs.getInt("value"));

        assertEquals(List.of(1, 2, 3, 4, 5), result);
        assertThrowsExactly(RuntimeException.class, () -> template.execute("select * from  temp_numbers"), "Transaction was not committed");
    }
}
