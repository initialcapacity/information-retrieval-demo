<#import "template.ftl" as layout>
<#-- @ftlvariable name="report" type="io.ic.starter.eval.EvalReport" -->
<@layout.layout active="eval" title="Eval - Retrieval Playground">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">precision / recall / f-score</span></p>
        <h2>The hybrid scores <em>highest</em> overall</h2>
        <p class="lede">
            Macro-averaged over 56 fixture queries against the PostgreSQL manual at k=${report.k()},
            RRF k=${report.rrfK()}, hnsw.ef_search=${report.efSearch()}.
            Real numbers from the committed eval run.
        </p>
    </header>

    <#list report.modes() as mode>
        <div class="subsection">
            <div class="subsection-title">${mode.name()} <small>relevance binarization</small></div>
            <#if mode.name()?contains("Partial")>
                <p class="eval-note">Sections merely related to the query also count as relevant. The relevant set is larger, recall at k=${report.k()} has a lower ceiling, and the three methods score closer together.</p>
            <#else>
                <p class="eval-note">Only sections judged to answer the query count as relevant. It is the strict bar, and where the pattern is clearest: F1 rises from BM25 to embeddings to hybrid.</p>
            </#if>

            <p class="eval-caption">Overall quality per method: precision, recall, and F1 at k=${report.k()}, macro-averaged across every fixture query. The hybrid row is highlighted.</p>
            <div class="card" style="margin-bottom: var(--space-6);">
                <table class="table">
                    <thead>
                        <tr><th>method</th><th class="num">precision@${report.k()}</th><th class="num">recall@${report.k()}</th><th class="num">f1@${report.k()}</th></tr>
                    </thead>
                    <tbody>
                        <#list mode.overall() as m>
                            <tr<#if m.method() == "hybrid"> class="row-header"</#if>>
                                <td>${m.method()}</td>
                                <td class="num">${m.precision()?string["0.0000"]}</td>
                                <td class="num">${m.recall()?string["0.0000"]}</td>
                                <td class="num">${m.f1()?string["0.0000"]}</td>
                            </tr>
                        </#list>
                    </tbody>
                </table>
            </div>

            <p class="eval-caption">The same scores split by query type: exact-token queries, paraphrased queries, and queries with some of each. The balance between methods shifts across the rows; the highest F1 in each row is highlighted.</p>
            <div class="card">
                <table class="table">
                    <thead>
                        <tr>
                            <th>query type</th><th class="num">n</th>
                            <th class="num">bm25 f1</th><th class="num">embeddings f1</th><th class="num">hybrid f1</th>
                            <th class="num">bm25 recall</th><th class="num">embeddings recall</th><th class="num">hybrid recall</th>
                        </tr>
                    </thead>
                    <tbody>
                        <#list mode.buckets() as b>
                            <#assign fBm = b.metrics()[0].f1()>
                            <#assign fDn = b.metrics()[1].f1()>
                            <#assign fHy = b.metrics()[2].f1()>
                            <#assign fMax = fBm>
                            <#if fDn gt fMax><#assign fMax = fDn></#if>
                            <#if fHy gt fMax><#assign fMax = fHy></#if>
                            <tr>
                                <td>${b.lean()}</td>
                                <td class="num">${b.queryCount()}</td>
                                <td class="num<#if fBm gte fMax> win</#if>">${fBm?string["0.0000"]}</td>
                                <td class="num<#if fDn gte fMax> win</#if>">${fDn?string["0.0000"]}</td>
                                <td class="num<#if fHy gte fMax> win</#if>">${fHy?string["0.0000"]}</td>
                                <td class="num">${b.metrics()[0].recall()?string["0.0000"]}</td>
                                <td class="num">${b.metrics()[1].recall()?string["0.0000"]}</td>
                                <td class="num">${b.metrics()[2].recall()?string["0.0000"]}</td>
                            </tr>
                        </#list>
                    </tbody>
                </table>
            </div>
        </div>
    </#list>
</section>
</@layout.layout>
