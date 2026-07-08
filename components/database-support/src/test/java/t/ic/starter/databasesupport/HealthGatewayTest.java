package t.ic.starter.databasesupport;

import io.ic.starter.databasesupport.HealthGateway;
import io.ic.starter.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthGatewayTest {
    private final TestDatabase testDb = TestDatabase.create();
    private final HealthGateway gateway = new HealthGateway(testDb.dataSource());

    @AfterEach
    void tearDown() {
        testDb.close();
    }

    @Test
    void testIsDatabaseHealthy() {
        assertTrue(gateway.isDatabaseHealthy());
    }
}
