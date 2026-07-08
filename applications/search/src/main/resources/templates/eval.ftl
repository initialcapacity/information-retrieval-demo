<#import "template.ftl" as layout>
<#-- @ftlvariable name="report" type="io.ic.starter.eval.EvalReport" -->
<@layout.layout active="eval" title="Eval - DubJUG">
<section>
    <header>
        <p class="eyebrow"><span class="eyebrow-inner">precision / recall / f-score</span></p>
        <h2>The F-score <em>climbs</em>: BM25 &rarr; dense &rarr; hybrid</h2>
        <p class="lede">
            Macro-averaged over 116 WANDS fixture queries at k=${report.k()},
            RRF k=${report.rrfK()}, hnsw.ef_search=${report.efSearch()}.
            Real numbers from the committed eval run.
        </p>
    </header>

    <#list report.modes() as mode>
        <div class="subsection">
            <div class="subsection-title">${mode.name()} <small>relevance binarization</small></div>

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

            <div class="card">
                <table class="table">
                    <thead>
                        <tr>
                            <th>lean bucket</th><th class="num">n</th>
                            <th class="num">bm25 f1</th><th class="num">dense f1</th><th class="num">hybrid f1</th>
                            <th class="num">bm25 recall</th><th class="num">dense recall</th><th class="num">hybrid recall</th>
                        </tr>
                    </thead>
                    <tbody>
                        <#list mode.buckets() as b>
                            <tr>
                                <td>${b.lean()}</td>
                                <td class="num">${b.queryCount()}</td>
                                <td class="num">${b.metrics()[0].f1()?string["0.0000"]}</td>
                                <td class="num">${b.metrics()[1].f1()?string["0.0000"]}</td>
                                <td class="num">${b.metrics()[2].f1()?string["0.0000"]}</td>
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
