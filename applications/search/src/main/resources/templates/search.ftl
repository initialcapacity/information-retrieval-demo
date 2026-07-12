<#import "template.ftl" as layout>
<#-- @ftlvariable name="view" type="io.ic.starter.app.SearchView" -->
<@layout.layout active="search" title="Search - Retrieval Playground">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">live retrieval</span></p>
        <h2>Three ways to <em>search</em>, side by side</h2>
        <p class="lede">
            One query, three rankings: BM25, embeddings, and their RRF hybrid.
            Fixture queries mark Exact and Partial hits so you can see where each method succeeds and fails.
        </p>

        <form class="search-form" action="/" method="get">
            <input type="search" name="q" placeholder="Search the PostgreSQL manual..." value="${view.query()}" autofocus>
            <button type="submit" class="accent">Search</button>
        </form>
        <div class="hero-links">
            <span class="lbl">hero queries:</span>
            <#list view.heroQueries() as q>
                <a class="button secondary" href="/?q=${q?url('UTF-8')}">${q}</a>
            </#list>

            <span class="hero-sep" aria-hidden="true"></span>

            <div class="qpicker" id="qpicker">
                <button type="button" class="button qpicker-trigger" id="qpicker-trigger"
                        aria-haspopup="listbox" aria-expanded="false" aria-controls="qpicker-panel">
                    Pick a labelled query <span class="n">(${view.pickerTotal()})</span>
                    <svg class="caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                </button>

                <div class="qpanel" id="qpicker-panel" role="listbox" aria-label="Labelled queries by lean bucket">
                    <div class="qpanel-head">
                        <div class="title">
                            <span>Labelled queries</span>
                            <span class="meta">${view.pickerTotal()} queries &middot; grouped by lean</span>
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
                                    <button type="button" class="qitem" role="option" data-q="${item.query()?html}">
                                        <span class="q">${item.query()?html}</span>
                                        <span class="qcat">${item.category()?html}</span>
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
                        <span style="margin-left:auto;">selecting a query runs it</span>
                    </div>
                </div>
            </div>
        </div>
    </header>

    <#if view.submitted()>
        <div class="row" style="margin-bottom: var(--space-5); gap: var(--space-3);">
            <#if view.fixtureQuery()>
                <span class="badge signal dot">relevance labelled</span>
            <#else>
                <span class="badge">ad-hoc query - no relevance labels</span>
            </#if>
            <#if view.embeddingSource()??>
                <span class="badge">embedding: ${view.embeddingSource()}</span>
            </#if>
        </div>

        <#if view.error()??>
            <div class="card" style="border-left: 3px solid var(--warning);">
                <strong>Embeddings &amp; hybrid unavailable.</strong>
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

<script>
    (function () {
        var picker = document.getElementById('qpicker');
        if (!picker) return;
        var trigger = document.getElementById('qpicker-trigger');
        var filter = document.getElementById('qpicker-filter');

        function setOpen(open) {
            picker.classList.toggle('open', open);
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
