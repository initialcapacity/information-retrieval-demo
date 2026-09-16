<#import "template.ftl" as layout>
<#-- @ftlvariable name="report" type="io.ic.starter.eval.EvalReport" -->
<@layout.layout active="eval" title="Evaluation - Retrieval Playground">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">Evaluation</span></p>
        <h2>Compare <em>retrieval quality</em></h2>
        <p class="lede">
            Scores for the top ${report.k()} results across ${report.modes()[0].overall()[0].queryCount()} labeled queries against the PostgreSQL manual.
            These are saved results; opening this page does not run a new evaluation.
        </p>
    </header>

    <p class="eval-note">
        Settings: RRF k=${report.rrfK()}, candidate depth=${report.candidateDepth()}, hnsw.ef_search=${report.efSearch()}.
    </p>

    <#if report.provenance()??>
        <p class="eval-note">
            Saved: ${report.provenance().generatedAt()} &middot;
            ${report.provenance().chunkCount()?c} chunks &middot;
            ${report.provenance().embeddingModel()} (${report.provenance().embeddingDimensions()?c} dimensions).
            To compare runs, check the input and ranking fingerprints in eval-results.json.
        </p>
    </#if>

    <#list report.modes() as mode>
        <div class="subsection">
            <div class="subsection-title">${mode.name()} <small>what counts as relevant</small></div>
            <#if mode.name()?contains("Partial")>
                <p class="eval-note">Related sections count too, even if they don't answer the question. With more sections counted as relevant, the top ${report.k()} results can cover a smaller share of them.</p>
            <#else>
                <p class="eval-note">Only sections judged to answer the question count as relevant.</p>
            </#if>

            <p class="eval-caption">Precision, recall, and F1 are calculated for each query at k=${report.k()}, then averaged across all queries (macro averages). F1 is averaged separately, so it won't necessarily match the F1 calculated from the displayed precision and recall. The hybrid row is highlighted.</p>
            <div class="card table-scroll" style="margin-bottom: var(--space-6);" tabindex="0" role="region" aria-label="${mode.name()} overall metrics">
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

            <p class="eval-caption">Scores by query type: keyword queries use terms from the manual, semantic queries describe the problem in other words, and mixed queries do both. Each row highlights the highest F1.</p>
            <div class="card table-scroll" tabindex="0" role="region" aria-label="${mode.name()} metrics by query type">
                <table class="table">
                    <thead>
                        <tr>
                            <th>query type</th><th class="num">queries</th>
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
