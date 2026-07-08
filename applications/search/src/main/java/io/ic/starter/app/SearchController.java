package io.ic.starter.app;

import io.javalin.http.Context;

import java.util.Map;

public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    public void index(Context ctx) {
        String query = ctx.queryParam("q");
        SearchView view = (query == null) ? searchService.blank() : searchService.search(query);
        ctx.render("search.ftl", Map.of("view", view, "active", "search"));
    }
}
