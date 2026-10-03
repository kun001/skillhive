import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from worker import office


class OfficeTests(unittest.TestCase):
    def test_excel_cannot_invoke_renderer(self):
        with patch("worker.subprocess.run") as render:
            for extension in ("xls", "xlsx"):
                with self.assertRaisesRegex(ValueError, "Unsupported preview type"):
                    office(Path(f"source.{extension}"))
            render.assert_not_called()

    def test_page_limit_and_page_images(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.docx"
            source.touch()

            def generate(command, **kwargs):
                if command[0] == "soffice":
                    self.assertIn('"value": "1-5"', command[command.index("--convert-to") + 1])
                    source.with_suffix(".pdf").touch()
                else:
                    self.assertEqual(command[command.index("-l") + 1], "5")
                    for page in range(1, 6):
                        (root / f"page-{page}.jpg").touch()

            with patch("worker.subprocess.run", side_effect=generate):
                self.assertEqual(office(source), {"status": "READY", "kind": "OFFICE", "pageCount": 5, "pageLimit": 5})
            self.assertEqual(len(list(root.glob("[1-5].jpg"))), 5)


if __name__ == "__main__":
    unittest.main()