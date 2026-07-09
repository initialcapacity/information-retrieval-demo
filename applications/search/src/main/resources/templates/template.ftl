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
        .hero-links { display: flex; flex-wrap: wrap; gap: var(--space-4); margin-top: var(--space-5); align-items: center; }
        .hero-links .lbl { font-family: var(--font-mono); font-size: var(--fs-sm); text-transform: uppercase; letter-spacing: var(--tracking-wide); color: var(--text-muted); }
        .search-form { display: flex; gap: var(--space-4); flex-wrap: wrap; }
        .search-form input[type=search] { flex: 1; min-width: 320px; }
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
