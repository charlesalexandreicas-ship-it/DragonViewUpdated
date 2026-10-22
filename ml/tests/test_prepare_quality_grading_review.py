"""Safety checks for the reviewed quality-grading dataset intake."""

from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from PIL import Image

from ml import prepare_quality_grading_review as intake


class QualityGradingReviewTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.fresh = self.root / "Fresh Dragon Fruit" / "Class A and B"
        self.defect = self.root / "Defect Dragon Fruit" / "Class C"
        self.fresh.mkdir(parents=True)
        self.defect.mkdir(parents=True)
        Image.new("RGB", (24, 24), "red").save(self.fresh / "fresh1.jpg")
        Image.new("RGB", (24, 24), "red").save(self.fresh / "fresh2.jpg")
        Image.new("RGB", (24, 24), "purple").save(self.defect / "defect1.jpg")

    def manifest(self, rows: list[dict[str, str]]) -> Path:
        path = self.root / "review_manifest.csv"
        with path.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=intake.FIELDS)
            writer.writeheader()
            writer.writerows(rows)
        return path

    def test_scan_flags_exact_duplicate_without_assigning_grade(self) -> None:
        rows, summary = intake.scan(self.root)
        self.assertEqual(summary["total_images"], 3)
        self.assertEqual(summary["exact_duplicate_copies"], 1)
        self.assertEqual(rows[1]["review_decision"], "exclude_duplicate")
        self.assertTrue(all(not row["final_label"] for row in rows))

    def test_import_requires_explicit_grade_and_fruit_identity(self) -> None:
        rows, _ = intake.scan(self.root)
        rows[0]["review_decision"] = "approved_training"
        rows[0]["final_label"] = "Class A"
        manifest = self.manifest(rows)
        with self.assertRaisesRegex(ValueError, "physical_fruit_id"):
            intake.import_reviewed(manifest)

    def test_import_copies_only_reviewed_training_example(self) -> None:
        rows, _ = intake.scan(self.root)
        rows[0].update(
            review_decision="approved_training",
            final_label="Class A",
            physical_fruit_id="fresh_fruit_1",
        )
        manifest = self.manifest(rows)
        raw_root = self.root / "raw"
        with patch.object(intake, "RAW_ROOT", raw_root):
            intake.import_reviewed(manifest)
            intake.import_reviewed(manifest)
        target = raw_root / "class_a" / "quality_grading_fresh_training_only" / "fresh1.jpg"
        self.assertTrue(target.is_file())
        self.assertEqual(list(raw_root.rglob("*.jpg")), [target])


if __name__ == "__main__":
    unittest.main()
