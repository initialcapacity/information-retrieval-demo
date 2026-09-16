package io.ic.starter.app;

import com.zaxxer.hikari.HikariDataSource;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Version;
import io.ic.starter.catalog.ChunksGateway;
import io.ic.starter.databasesupport.DataSourceFactory;
import io.ic.starter.databasesupport.HealthGateway;
import io.ic.starter.search.Bm25Gateway;
import io.ic.starter.search.RetrievalConfig;
import io.ic.starter.search.EmbeddingClient;
import io.ic.starter.search.EmbeddingGateway;
import io.ic.starter.search.HybridSearchService;
import io.ic.starter.search.OpenAiEmbeddingClient;
import io.ic.starter.starterenv.Environment;
import io.ic.starter.websupport.AppSetup;
import io.javalin.config.JavalinConfig;
import io.javalin.config.RoutesConfig;
import io.javalin.rendering.template.JavalinFreemarker;

public class StarterSetup implements AppSetup {
    private final Environment env;
    private HikariDataSource dataSource;

    public StarterSetup(Environment env) {
        this.env = env;
    }

    @Override
    public void configureServer(JavalinConfig javalinConfig) {
        var freemarkerConfig = freemarkerConfiguration();
        javalinConfig.fileRenderer(new JavalinFreemarker(freemarkerConfig));
        javalinConfig.staticFiles.add("/static");
    }

    static freemarker.template.Configuration freemarkerConfiguration() {
        var freemarkerConfig = new freemarker.template.Configuration(new Version(2, 3, 34));
        freemarkerConfig.setClassForTemplateLoading(JavalinFreemarker.class, "/templates");
        freemarkerConfig.setOutputEncoding("UTF-8");
        freemarkerConfig.setURLEscapingCharset("UTF-8");
        freemarkerConfig.setOutputFormat(HTMLOutputFormat.INSTANCE);
        freemarkerConfig.setAutoEscapingPolicy(
                freemarker.template.Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY);
        return freemarkerConfig;
    }

    @Override
    public void configureEndpoints(RoutesConfig routes) {
        // ef_search parity with the eval: the pgvector default (40) under-retrieves.
        dataSource = DataSourceFactory.create(env.databaseUrl(), 10, RetrievalConfig.CONNECTION_INIT_SQL);

        var bm25Gateway = new Bm25Gateway(dataSource);
        var embeddingGateway = new EmbeddingGateway(dataSource);
        var chunksGateway = new ChunksGateway(dataSource);
        var hybridService = new HybridSearchService(bm25Gateway, embeddingGateway);

        EmbeddingClient embeddingClient = (env.openAiApiKey() == null || env.openAiApiKey().isBlank())
                ? null
                : OpenAiEmbeddingClient.forInteractiveRequests(env.openAiApiKey());
        var embeddingResolver = new QueryEmbeddingResolver(embeddingClient);
        var fixtureIndex = new FixtureIndex();

        var searchService = new SearchService(
                bm25Gateway, embeddingGateway, hybridService, chunksGateway, embeddingResolver, fixtureIndex);
        var searchController = new SearchController(searchService);
        var evalController = new EvalController(new EvalReportLoader().load());
        var healthGateway = new HealthGateway(dataSource);
        var healthController = new HealthController(healthGateway::isDatabaseHealthy);

        routes.get("/", searchController::index);
        routes.get("/eval", evalController::index);
        routes.get("/health", healthController::index);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
