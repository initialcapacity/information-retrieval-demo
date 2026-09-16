<#import "template.ftl" as layout>
<#-- @ftlvariable name="view" type="io.ic.starter.app.SearchView" -->
<@layout.layout active="search" title="Search - Retrieval Playground">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">PostgreSQL manual</span></p>
        <h2>Compare <em>search results</em></h2>
        <p class="lede">
            Search with BM25, embeddings, and a hybrid that combines both rankings using reciprocal rank fusion (RRF).
            For labeled queries, Exact marks sections judged to answer the question; Partial marks related sections.
        </p>

        <form class="search-form" action="/" method="get">
            <input type="search" name="q" aria-label="Search the PostgreSQL manual" placeholder="Search the PostgreSQL manual..." value="${view.query()}" autofocus>
            <button type="submit" class="accent">Search</button>
        </form>
        <div class="hero-links">
            <span class="lbl">Try a query:</span>
            <#list view.heroQueries() as q>
                <a class="button secondary" href="/?q=${q?url('UTF-8')}">${q}</a>
            </#list>

            <span class="hero-sep" aria-hidden="true"></span>

            <div class="qpicker" id="qpicker">
                <button type="button" class="button qpicker-trigger" id="qpicker-trigger"
                        aria-haspopup="listbox" aria-expanded="false" aria-controls="qpicker-panel">
                    Browse labeled queries <span class="n">(${view.pickerTotal()})</span>
                    <svg class="caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                </button>

                <div class="qpanel" id="qpicker-panel" role="listbox" aria-label="Labeled queries by query type">
                    <div class="qpanel-head">
                        <div class="title">
                            <span>Labeled queries</span>
                            <span class="meta">${view.pickerTotal()} queries by type</span>
                        </div>
                        <div class="qfilter">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/></svg>
                            <input type="text" id="qpicker-filter" placeholder="Filter queries…" aria-label="Filter queries" autocomplete="off">
                        </div>
                    </div>

                    <div class="qscroll">
                        <#list view.pickerGroups() as g>
                            <div class="qgroup">
                                <div class="qgroup-head">
                                    <span class="qtag ${g.lean()}">${g.label()}</span>
                                    <span class="desc">${g.desc()}</span>
                                    <span class="count">${g.count()}</span>
                                </div>
                                <#list g.queries() as item>
                                    <button type="button" class="qitem" role="option" data-q="${item.query()}">
                                        <span class="q">${item.query()}</span>
                                        <span class="qcat">${item.category()}</span>
                                        <span class="qn"><b>${item.exact()}</b> exact</span>
                                    </button>
                                </#list>
                            </div>
                        </#list>
                    </div>

                    <div class="qpanel-foot">
                        <span class="k keyword">Keyword</span>
                        <span class="k semantic">Semantic</span>
                        <span class="k mixed">Mixed</span>
                        <span style="margin-left:auto;">Choose a query to search</span>
                    </div>
                </div>
            </div>
        </div>
    </header>

    <#if view.submitted()>
        <div class="row" style="margin-bottom: var(--space-5); gap: var(--space-3);">
            <#if view.fixtureQuery()>
                <span class="badge signal dot">Labeled query</span>
            <#else>
                <span class="badge">No relevance labels for this query</span>
            </#if>
            <#if view.embeddingSource()??>
                <span class="badge">embedding: ${view.embeddingSource()}<#if view.timing()?? && view.timing().embedding()??> &middot; ${view.timing().embedding()}</#if></span>
            </#if>
            <#if view.timing()??>
                <span class="badge">server total: ${view.timing().total()}</span>
            </#if>
        </div>

        <#if view.error()??>
            <div class="card" style="border-left: 3px solid var(--warning);">
                <strong>${view.errorTitle()}</strong>
                <p style="color: var(--text-muted); margin-top: var(--space-2);">${view.error()}</p>
            </div>
        </#if>

        <div class="compare">
            <#list view.columns() as column>
                <div class="card">
                    <div class="subsection-title" style="margin-bottom: var(--space-4);">
                        ${column.method()}
                        <small>${column.subtitle()}</small>
                        <span class="ms" title="Time spent running this search on the server">${column.latency()}</span>
                    </div>
                    <#if column.results()?size == 0>
                        <p style="color: var(--text-muted); font-size: var(--fs-sm);">No matching sections.</p>
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

        <#if view.timing()??>
            <p class="latency-note">
                Times are measured on the server for this request.
                BM25 and embeddings each return up to ${view.timing().displayK()} results.
                Hybrid combines up to ${view.timing().candidateDepth()} results from each method, then shows the top ${view.timing().displayK()}.
                Queries without a cached embedding need an API call; its time is shown separately.
                The first search can be slower while Java and the database connections warm up. Run it again to compare.
            </p>
        </#if>
    </#if>
</section>

<script>
    (function () {
        var picker = document.getElementById('qpicker');
        if (!picker) return;
        var trigger = document.getElementById('qpicker-trigger');
        var filter = document.getElementById('qpicker-filter');

        function setOpen(open) {
            picker.classList.toggle('open', open);
            if (!open && picker.contains(document.activeElement)) trigger.focus();
            trigger.setAttribute('aria-expanded', String(open));
            if (open && filter) {
                setTimeout(function () { filter.focus(); }, 0);
            }
        }

        trigger.addEventListener('click', function (e) {
            e.stopPropagation();
            setOpen(!picker.classList.contains('open'));
        });
        document.addEventListener('click', function (e) {
            if (!picker.contains(e.target)) setOpen(false);
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') setOpen(false);
        });

        picker.querySelectorAll('.qitem').forEach(function (item) {
            item.addEventListener('click', function () {
                window.location.href = '/?q=' + encodeURIComponent(item.getAttribute('data-q'));
            });
        });

        if (filter) {
            filter.addEventListener('input', function () {
                var term = this.value.trim().toLowerCase();
                picker.querySelectorAll('.qgroup').forEach(function (group) {
                    var visible = 0;
                    group.querySelectorAll('.qitem').forEach(function (it) {
                        var cat = it.querySelector('.qcat');
                        var hay = (it.getAttribute('data-q') + ' ' + (cat ? cat.textContent : '')).toLowerCase();
                        var show = hay.indexOf(term) >= 0;
                        it.style.display = show ? '' : 'none';
                        if (show) visible++;
                    });
                    group.style.display = visible ? '' : 'none';
                });
            });
        }
    })();
</script>
</@layout.layout>
