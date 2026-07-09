<#macro layout active title="DubJUG Search">
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title}</title>
    <link rel="stylesheet" href="/css/reset.css">
    <link rel="stylesheet" href="/css/tokens.css">
    <link rel="stylesheet" href="/css/components.css">
    <script>
        /* Theme bootstrap - runs before paint. localStorage > prefers-color-scheme > light. */
        (function () {
            var theme = 'light';   /* light by default for projector legibility; toggle still honoured */
            try {
                var saved = localStorage.getItem('ic-theme');
                if (saved === 'dark' || saved === 'light') theme = saved;
            } catch (_) {}
            document.documentElement.dataset.theme = theme;
            document.documentElement.dataset.density = 'comfortable';
        })();
    </script>
    <style>
        .topbar {
            display: flex; align-items: center; justify-content: space-between;
            gap: var(--space-5); padding: var(--space-4) var(--space-8);
            border-bottom: 1px solid var(--border); background: var(--surface);
        }
        .topbar .brand { display: flex; align-items: baseline; gap: var(--space-3); }
        .topbar .brand .mark {
            font-family: var(--font-mono); font-weight: 700; color: var(--accent);
            letter-spacing: var(--tracking-wide);
        }
        .topbar .brand .name { font-family: var(--font-display); font-weight: 700; }
        .topbar nav { display: flex; gap: var(--space-2); align-items: center; }
        .topbar nav a {
            font-family: var(--font-mono); font-size: var(--fs-xs); text-transform: uppercase;
            letter-spacing: var(--tracking-wide); padding: var(--space-2) var(--space-4);
            border-radius: var(--radius-sm); color: var(--text-muted); border: 1px solid transparent;
        }
        .topbar nav a:hover { color: var(--text); background: var(--surface-sunken); }
        .topbar nav a.active { color: var(--signal); border-color: var(--border); background: var(--signal-bg); }
        .compare { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--space-6); align-items: start; }
        @media (max-width: 1000px) { .compare { grid-template-columns: 1fr; } }
        .compare .card { padding: var(--space-6); }
        .compare .subsection-title { font-size: var(--fs-md); }
        .result-list { display: flex; flex-direction: column; }
        .result-row {
            display: flex; gap: var(--space-4); padding: var(--space-4) var(--space-2);
            border-bottom: 1px solid var(--border-muted); align-items: flex-start;
        }
        .result-row .rank { font-family: var(--font-mono); color: var(--text-light); min-width: 1.6em; font-size: var(--fs-md); }
        .result-row .body { flex: 1; min-width: 0; }
        .result-row .name { font-size: var(--fs-lg); line-height: var(--lh-snug); }
        .result-row .cat { font-family: var(--font-mono); font-size: var(--fs-sm); color: var(--text-muted); text-transform: uppercase; letter-spacing: var(--tracking-wide); margin-top: var(--space-1); }
        .result-row.hit { background: color-mix(in srgb, var(--success) 9%, transparent); }
        .hero-links { display: flex; flex-wrap: wrap; gap: var(--space-4); margin-top: var(--space-5); align-items: center; margin-bottom: var(--space-4); }
        .hero-links .lbl { font-family: var(--font-mono); font-size: var(--fs-sm); text-transform: uppercase; letter-spacing: var(--tracking-wide); color: var(--text-muted); }
        .search-form { display: flex; gap: var(--space-4); flex-wrap: wrap; }
        .search-form input[type=search] { flex: 1; min-width: 320px; }
        .eval-note { font-size: var(--fs-md); line-height: var(--lh-snug); color: var(--text); max-width: 74ch; margin-bottom: var(--space-4); }
        .eval-caption { font-size: var(--fs-md); line-height: var(--lh-snug); color: var(--text-muted); max-width: 74ch; margin-bottom: var(--space-4); }
        /* Eval emphasis: fill the winning row/cell rather than just drawing a left bar. */
        .table .row-header td { background: color-mix(in srgb, var(--signal) 16%, var(--surface)); border-left: none; }
        .table .row-header td:first-child { border-left: 3px solid var(--signal); }
        .table td.win { background: color-mix(in srgb, var(--signal) 16%, var(--surface)); font-weight: 700; color: var(--text); }
        .hero-sep { width: 1px; align-self: stretch; background: var(--border); margin: 0 var(--space-2); }

        /* ---------- Grouped labelled-query picker ---------- */
        .qpicker { position: relative; }
        .qpicker-trigger { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong);
            font-family: var(--font-mono); font-weight: 600; gap: var(--space-3); }
        .qpicker-trigger:hover { background: var(--surface-sunken); border-color: var(--text-muted); }
        .qpicker.open .qpicker-trigger { border-color: var(--signal); box-shadow: 0 0 0 3px var(--focus-ring); background: var(--surface); }
        .qpicker-trigger .caret { width: 14px; height: 14px; color: var(--text-muted); transition: transform 140ms ease; }
        .qpicker.open .qpicker-trigger .caret { transform: rotate(180deg); color: var(--signal); }
        .qpicker-trigger .n { color: var(--signal); }

        .qpanel { position: absolute; top: calc(100% + 8px); left: 0; width: 520px; max-width: calc(100vw - 96px); z-index: 40;
            background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius-md);
            box-shadow: var(--shadow-lg); overflow: hidden; display: none; }
        .qpicker.open .qpanel { display: block; }

        .qpanel-head { padding: var(--space-4) var(--space-5); border-bottom: 1px solid var(--border); }
        .qpanel-head .title { font-family: var(--font-mono); font-size: var(--fs-xs); font-weight: 700; letter-spacing: var(--tracking-caps);
            text-transform: uppercase; color: var(--text); display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); }
        .qpanel-head .title .meta { color: var(--text-light); font-weight: 400; letter-spacing: var(--tracking-wide); }
        .qfilter { position: relative; margin-top: var(--space-3); }
        .qfilter input { width: 100%; min-width: 0; height: 34px; padding-left: 34px; font-size: var(--fs-sm); border-color: var(--border); }
        .qfilter svg { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); width: 15px; height: 15px; color: var(--text-light); pointer-events: none; }

        .qscroll { max-height: 430px; overflow-y: auto; overscroll-behavior: contain; }
        .qscroll::-webkit-scrollbar { width: 10px; }
        .qscroll::-webkit-scrollbar-thumb { background: var(--border-strong); border-radius: var(--radius-full); border: 3px solid var(--surface); }

        .qgroup-head { position: sticky; top: 0; z-index: 1; display: flex; align-items: center; gap: var(--space-3);
            padding: var(--space-3) var(--space-5); background: var(--surface-sunken); border-bottom: 1px solid var(--border);
            border-top: 1px solid var(--border); }
        .qgroup:first-child .qgroup-head { border-top: none; }
        .qtag { display: inline-flex; align-items: center; gap: 6px; font-family: var(--font-mono); font-size: var(--fs-2xs); font-weight: 700;
            letter-spacing: var(--tracking-caps); text-transform: uppercase; padding: 3px 8px; border-radius: var(--radius-xs); border: 1px solid; }
        .qtag::before { content: ""; width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
        .qtag.keyword { color: var(--signal); background: var(--signal-bg); border-color: color-mix(in srgb, var(--signal) 40%, transparent); }
        .qtag.semantic { color: var(--accent); background: var(--warning-bg); border-color: color-mix(in srgb, var(--accent) 40%, transparent); }
        .qtag.mixed { color: var(--navy-700); background: #eef1f6; border-color: color-mix(in srgb, var(--navy-700) 32%, transparent); }
        [data-theme="dark"] .qtag.mixed { color: var(--navy-200); background: var(--surface-raised); border-color: var(--border-strong); }
        .qgroup-head .desc { font-family: var(--font-sans); font-size: var(--fs-sm); color: var(--text-muted); }
        .qgroup-head .count { margin-left: auto; font-family: var(--font-mono); font-size: var(--fs-xs); font-weight: 700; color: var(--text-muted); }

        .qitem { display: flex; align-items: center; gap: var(--space-4); width: 100%; text-align: left; padding: var(--space-3) var(--space-5);
            background: transparent; border: none; border-bottom: 1px solid var(--border-muted); border-radius: 0; height: auto;
            font-weight: 400; color: var(--text); cursor: pointer; transition: background 100ms; }
        .qitem:hover, .qitem:focus-visible { background: var(--signal-bg); outline: none; }
        .qitem .q { font-family: var(--font-sans); font-size: var(--fs-md); color: var(--text); flex: 1; min-width: 0;
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .qitem .qcat { font-family: var(--font-mono); font-size: var(--fs-2xs); text-transform: uppercase; letter-spacing: var(--tracking-wide);
            color: var(--text-light); flex-shrink: 0; max-width: 180px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .qitem .qn { font-family: var(--font-mono); font-size: var(--fs-2xs); color: var(--text-muted); flex-shrink: 0; min-width: 64px; text-align: right; }
        .qitem .qn b { color: var(--success); font-weight: 700; }

        .qpanel-foot { padding: var(--space-3) var(--space-5); border-top: 1px solid var(--border); background: var(--surface-sunken);
            font-family: var(--font-mono); font-size: var(--fs-2xs); color: var(--text-muted); display: flex; gap: var(--space-4); align-items: center; flex-wrap: wrap; }
        .qpanel-foot .k { display: inline-flex; align-items: center; gap: 6px; }
        .qpanel-foot .k::before { content: ""; width: 7px; height: 7px; border-radius: 50%; }
        .qpanel-foot .k.keyword::before { background: var(--signal); }
        .qpanel-foot .k.semantic::before { background: var(--accent); }
        .qpanel-foot .k.mixed::before { background: var(--navy-700); }
    </style>
</head>
<body>
<header class="topbar">
    <div class="brand">
        <span class="mark">[ic]</span>
        <span class="name">Retrieval Playground</span>
    </div>
    <nav>
        <a href="/" <#if active=="search">class="active"</#if>>Search</a>
        <a href="/eval" <#if active=="eval">class="active"</#if>>Eval</a>
        <button class="theme-toggle" type="button" id="theme-toggle" aria-label="Switch theme">
            <svg class="moon" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
            <svg class="sun" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>
            <span>light</span>
        </button>
    </nav>
</header>
<main>
    <#nested />
</main>
<script>
    (function () {
        var btn = document.getElementById('theme-toggle');
        if (!btn) return;
        var label = btn.querySelector('span');
        function sync() {
            var t = document.documentElement.dataset.theme || 'light';
            if (label) label.textContent = t;
            btn.setAttribute('aria-label', 'Switch to ' + (t === 'light' ? 'dark' : 'light') + ' mode');
        }
        sync();
        btn.addEventListener('click', function () {
            var next = (document.documentElement.dataset.theme === 'dark') ? 'light' : 'dark';
            document.documentElement.dataset.theme = next;
            try { localStorage.setItem('ic-theme', next); } catch (_) {}
            sync();
        });
    })();
</script>
</body>
</html>
</#macro>
