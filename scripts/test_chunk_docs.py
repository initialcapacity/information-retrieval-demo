import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from chunk_docs import chunk_docs


class ChunkDocsTest(unittest.TestCase):
    def test_writes_header_and_preserves_all_chunks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docs = root / "docs"
            docs.mkdir()
            for name in ("first", "second"):
                (docs / f"{name}.html").write_text(
                    f"<h1>{name}</h1><p>" + "sample text " * 30 + "</p>", encoding="utf-8")
            output = root / "output" / "chunks.tsv"
            with contextlib.redirect_stdout(io.StringIO()):
                chunk_docs(docs, output)
            rows = output.read_text().splitlines()
            self.assertEqual("chunk_id\ttitle\tpage\ttext", rows[0])
            self.assertEqual(["1", "2"], [row.split("\t")[0] for row in rows[1:]])
            self.assertTrue(all(len(row.split("\t")) == 4 for row in rows))

    def test_missing_or_empty_input_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "chunks.tsv"
            output.write_text("keep existing corpus")
            for docs in (root / "missing", root):
                with self.assertRaisesRegex(ValueError, "No document chunks"):
                    chunk_docs(docs, output)
                self.assertEqual("keep existing corpus", output.read_text())


if __name__ == "__main__":
    unittest.main()
