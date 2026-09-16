"""Parse PostgreSQL HTML docs into section-level chunks.

One chunk per page; pages longer than MAX_WORDS split at <h2>/<h3>
boundaries. Output: chunks.tsv (chunk_id, title, page, text).
"""
import argparse
import html
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

DOCS = Path("postgresql-18.1/doc/src/sgml/html")
MAX_WORDS = 900
MIN_WORDS = 40

SKIP_PAGES = {"index.html", "bookindex.html", "biblio.html"}


class Extractor(HTMLParser):
    """Collects text, recording h1/h2/h3 boundaries as section breaks."""

    def __init__(self):
        super().__init__()
        self.sections = []  # (heading, [text parts])
        self.current_heading = ""
        self.parts = []
        self.in_heading = 0
        self.heading_buf = []
        self.skip_depth = 0  # inside nav

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "div" and a.get("class", "") in ("navheader", "navfooter", "toc"):
            self.skip_depth += 1
        if self.skip_depth:
            return
        if tag in ("h1", "h2", "h3"):
            self._flush()
            self.in_heading += 1
        if tag in ("p", "li", "dt", "dd", "pre", "td"):
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag == "div" and self.skip_depth:
            self.skip_depth -= 1
            return
        if tag in ("h1", "h2", "h3") and self.in_heading:
            self.in_heading -= 1
            self.current_heading = " ".join("".join(self.heading_buf).split())
            self.heading_buf = []

    def handle_data(self, data):
        if self.skip_depth:
            return
        if self.in_heading:
            self.heading_buf.append(data)
        else:
            self.parts.append(data)

    def _flush(self):
        text = re.sub(r"\n{2,}", "\n", "".join(self.parts)).strip()
        if text:
            self.sections.append((self.current_heading, text))
        self.parts = []

    def result(self):
        self._flush()
        return self.sections


def norm(s):
    return " ".join(html.unescape(s).split())


def chunk_docs(docs, output):
    chunks = []
    for f in sorted(docs.glob("*.html")):
        if f.name in SKIP_PAGES or f.name.startswith("release-"):
            continue
        ex = Extractor()
        try:
            ex.feed(f.read_text(encoding="utf-8", errors="replace"))
        except Exception as e:
            print(f"parse failed {f.name}: {e}", file=sys.stderr)
            continue
        sections = [(h, norm(t)) for h, t in ex.result()]
        sections = [(h, t) for h, t in sections if len(t.split()) >= 5]
        if not sections:
            continue
        page_title = sections[0][0] or f.stem

        # greedily merge sections into chunks of <= MAX_WORDS
        def merge(secs):
            buf, buf_heads, buf_words = [], [], 0
            out = []

            def emit():
                nonlocal buf, buf_heads, buf_words
                if buf and buf_words >= MIN_WORDS:
                    head = buf_heads[0] or page_title
                    out.append((head, " ".join(buf)))
                buf, buf_heads, buf_words = [], [], 0

            for h, t in secs:
                w = len(t.split())
                if buf_words and buf_words + w > MAX_WORDS:
                    emit()
                buf.append(t)
                buf_heads.append(h)
                buf_words += w
            emit()
            return out

        emitted = merge(sections)

        for head, text in emitted:
            title = page_title if head == page_title else f"{page_title} — {head}"
            chunks.append((f.name, norm(title), text))

    if not chunks:
        raise ValueError(f"No document chunks found in {docs}; output was not changed")
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as out:
        out.write("chunk_id\ttitle\tpage\ttext\n")
        for i, (page, title, text) in enumerate(chunks, 1):
            text = text.replace("\t", " ").replace("\n", " ")
            title = title.replace("\t", " ")
            out.write(f"{i}\t{title}\t{page}\t{text}\n")

    words = sorted(len(c[2].split()) for c in chunks)
    print(f"chunks: {len(chunks)}")
    print(f"words/chunk p10={words[len(words)//10]} median={words[len(words)//2]} p90={words[9*len(words)//10]}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--docs", type=Path, default=DOCS)
    parser.add_argument("--output", type=Path, default=Path("chunks.tsv"))
    args = parser.parse_args()
    try:
        chunk_docs(args.docs, args.output)
    except ValueError as error:
        parser.exit(1, f"{error}\n")
