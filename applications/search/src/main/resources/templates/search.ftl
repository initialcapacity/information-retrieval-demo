<#import "template.ftl" as layout>
<#-- @ftlvariable name="view" type="io.ic.starter.app.SearchView" -->
<@layout.layout active="search" title="Search - DubJUG">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">live retrieval</span></p>
        <h2>Three ways to <em>search</em>, side by side</h2>
        <p class="lede">
            One query, three rankings: lexical BM25, dense embeddings, and their RRF hybrid.
            Fixture queries mark Exact and Partial hits so you can see where each method wins and misses.
        </p>

        <form class="search-form" action="/" method="get">
            <input type="search" name="q" placeholder="Search the catalogue..." value="${view.query()}" autofocus>
            <button type="submit" class="accent">Search</button>
        </form>
        <div class="hero-links">
            <span class="lbl">hero queries:</span>
            <#list view.heroQueries() as q>
                <a class="button secondary sm" href="/?q=${q?url('UTF-8')}">${q}</a>
            </#list>
        </div>
    </header>

    <#if view.submitted()>
        <div class="row" style="margin-bottom: var(--space-5); gap: var(--space-3);">
            <#if view.fixtureQuery()>
                <span class="badge signal dot">fixture query - relevance labelled</span>
            <#else>
                <span class="badge">ad-hoc query - no relevance labels</span>
            </#if>
            <#if view.embeddingSource()??>
                <span class="badge">embedding: ${view.embeddingSource()}</span>
            </#if>
        </div>

        <#if view.error()??>
            <div class="card" style="border-left: 3px solid var(--warning);">
                <strong>Dense &amp; hybrid unavailable.</strong>
                <p style="color: var(--text-muted); margin-top: var(--space-2);">${view.error()}</p>
            </div>
        </#if>

        <div class="compare">
            <#list view.columns() as column>
                <div class="card">
                    <div class="subsection-title" style="margin-bottom: var(--space-4);">
                        ${column.method()}
                        <small>${column.subtitle()}</small>
                    </div>
                    <#if column.results()?size == 0>
                        <p style="color: var(--text-muted); font-size: var(--fs-sm);">No results.</p>
                    <#else>
                        <div class="result-list">
                            <#list column.results() as r>
                                <div class="result-row<#if r.relevant()> hit</#if>">
                                    <span class="rank">${r.rank()}</span>
                                    <div class="body">
                                        <div class="name">${r.name()}</div>
                                        <div class="cat">${r.category()}</div>
                                    </div>
                                    <#if r.relevance()??>
                                        <#if r.relevance() == "Exact">
                                            <span class="badge success">Exact</span>
                                        <#else>
                                            <span class="badge warning">Partial</span>
                                        </#if>
                                    </#if>
                                </div>
                            </#list>
                        </div>
                    </#if>
                </div>
            </#list>
        </div>
    </#if>
</section>
</@layout.layout>
