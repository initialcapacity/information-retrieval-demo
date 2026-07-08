import io.ic.starter.databasesupport.DataSourceFactory;
import org.flywaydb.core.Flyway;

void main() {
    var dataSource = DataSourceFactory.create(System.getenv("DATABASE_URL"));
    Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate();
}
