"""Prepare the downloaded quality-grading images for human ground-truth review.

The source dataset has only Fresh/Defect labels. Those labels do not establish
DragonView's A/B grades or its edible/juice-grade C definition. This command
records image integrity and duplicates, then creates contact sheets and a CSV
whose final labels and physical-fruit IDs must be reviewed before import.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = Path(
    r"C:\Users\Charlesalexandre\Documents\ExtractedDataset"
    r"\Dragon Fruit Quality Grading Dataset\Original Dataset"
)
DEFAULT_OUTPUT = PROJECT_ROOT / "ml" / "outputs" / "quality_grading_review"
RAW_ROOT = PROJECT_ROOT / "ml" / "datasets" / "dragon_fruit_quality" / "raw"
SOURCE_FOLDERS = (
    ("fresh", Path("Fresh Dragon Fruit") / "Class A and B", "Class A or Class B"),
    ("defect", Path("Defect Dragon Fruit") / "Class C", "Class C candidate or reject"),
)
FIELDS = (
    "source_pool",
    "source_hint",
    "source_path",
    "filename",
    "sha256",
    "width",
    "height",
    "exact_duplicate_of",
    "review_decision",
    "final_label",
    "physical_fruit_id",
    "review_notes",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def scan(source_root: Path) -> tuple[list[dict[str, str]], dict[str, object]]:
    rows: list[dict[str, str]] = []
    first_by_hash: dict[str, str] = {}
    counts: Counter[str] = Counter()
    for pool, relative_dir, hint in SOURCE_FOLDERS:
        directory = source_root / relative_dir
        if not directory.is_dir():
            raise ValueError(f"Source folder not found: {directory}")
        paths = sorted(directory.glob("*.jpg"))
        if not paths:
            raise ValueError(f"No JPEG images found in: {directory}")
        for path in paths:
            with Image.open(path) as opened:
                opened.verify()
            with Image.open(path) as opened:
                width, height = ImageOps.exif_transpose(opened).size
            digest = sha256(path)
            prior = first_by_hash.get(digest, "")
            if not prior:
                first_by_hash[digest] = str(path.resolve())
            rows.append(
                {
                    "source_pool": pool,
                    "source_hint": hint,
                    "source_path": str(path.resolve()),
                    "filename": path.name,
                    "sha256": digest,
                    "width": str(width),
                    "height": str(height),
                    "exact_duplicate_of": prior,
                    "review_decision": "exclude_duplicate" if prior else "pending",
                    "final_label": "",
                    "physical_fruit_id": "",
                    "review_notes": "",
                }
            )
            counts[pool] += 1
            if prior:
                counts[f"{pool}_duplicate_copies"] += 1
    summary = {
        "source_root": str(source_root.resolve()),
        "total_images": len(rows),
        "fresh_images": counts["fresh"],
        "defect_images": counts["defect"],
        "exact_duplicate_copies": sum(bool(row["exact_duplicate_of"]) for row in rows),
        "reviewed_labels": 0,
        "training_ready": False,
        "reason": (
            "Fresh is not split into A/B ground truth. Defect includes visible severe "
            "deterioration; Class C suitability and physical-fruit identity need review."
        ),
    }
    return rows, summary


def contact_sheets(rows: list[dict[str, str]], output_dir: Path) -> None:
    columns, rows_per_sheet = 8, 8
    tile_width, tile_height = 180, 165
    page_size = columns * rows_per_sheet
    for pool, _, _ in SOURCE_FOLDERS:
        pool_rows = [row for row in rows if row["source_pool"] == pool]
        for page_index, start in enumerate(range(0, len(pool_rows), page_size), start=1):
            page = pool_rows[start : start + page_size]
            sheet = Image.new(
                "RGB", (columns * tile_width, rows_per_sheet * tile_height), "white"
            )
            for index, row in enumerate(page):
                with Image.open(row["source_path"]) as opened:
                    thumbnail = ImageOps.contain(
                        ImageOps.exif_transpose(opened).convert("RGB"),
                        (tile_width, tile_height - 22),
                    )
                tile = Image.new("RGB", (tile_width, tile_height), "white")
                tile.paste(
                    thumbnail,
                    ((tile_width - thumbnail.width) // 2, (tile_height - 22 - thumbnail.height) // 2),
                )
                ImageDraw.Draw(tile).text(
                    (4, tile_height - 18), Path(row["filename"]).stem[-4:], fill="black"
                )
                sheet.paste(tile, ((index % columns) * tile_width, (index // columns) * tile_height))
            sheet.save(output_dir / f"{pool}_{page_index:02d}.jpg", quality=88)


def import_reviewed(manifest: Path) -> int:
    """Copy explicitly reviewed examples into training-only groups.

    Their source dataset has no verified independent fruit/session IDs, so these
    copies are never used for validation or a final test claim.
    """
    if not manifest.is_file():
        raise ValueError(f"Review manifest not found: {manifest}")
    with manifest.open("r", newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if not set(FIELDS).issubset(reader.fieldnames or []):
            raise ValueError("Review manifest is missing required columns")
        rows = list(reader)

    approved = [row for row in rows if row["review_decision"] == "approved_training"]
    if not approved:
        raise ValueError(
            "No reviewed images are marked approved_training. "
            "Assign final_label and physical_fruit_id after reviewing the source photos."
        )

    assignments: dict[str, str] = {}
    destinations: list[tuple[Path, Path, str]] = []
    for row in approved:
        label = row["final_label"].strip()
        pool = row["source_pool"]
        group = row["physical_fruit_id"].strip()
        valid_labels = {"fresh": {"Class A", "Class B"}, "defect": {"Class C"}}
        if label not in valid_labels.get(pool, set()):
            raise ValueError(f"Invalid reviewed label for {row['filename']}: {label!r}")
        if not group:
            raise ValueError(f"Missing physical_fruit_id for {row['filename']}")
        if row["exact_duplicate_of"]:
            raise ValueError(f"Exact duplicate cannot be imported: {row['filename']}")
        prior_label = assignments.setdefault(group, label)
        if prior_label != label:
            raise ValueError(f"Physical fruit group {group!r} has conflicting labels")
        source = Path(row["source_path"])
        if not source.is_file() or sha256(source) != row["sha256"]:
            raise ValueError(f"Source file changed or is missing: {source}")
        class_name = label.lower().replace(" ", "_")
        target = RAW_ROOT / class_name / f"quality_grading_{pool}_training_only" / source.name
        if target.exists() and sha256(target) != row["sha256"]:
            raise ValueError(f"Conflicting destination file: {target}")
        destinations.append((source, target, row["sha256"]))

    copied = 0
    for source, target, _ in destinations:
        if not target.exists():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            copied += 1
    print(f"Approved examples: {len(destinations)}; new training-only copies: {copied}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--import-reviewed",
        action="store_true",
        help="Copy only rows explicitly marked approved_training after ground-truth review.",
    )
    args = parser.parse_args()
    manifest = args.output_dir / "review_manifest.csv"
    if args.import_reviewed:
        try:
            return import_reviewed(manifest)
        except ValueError as error:
            parser.error(str(error))
    if manifest.exists():
        raise SystemExit(
            f"Review manifest already exists: {manifest}. "
            "Preserving any reviewer edits; choose a new --output-dir to regenerate."
        )
    rows, summary = scan(args.source_root)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    with manifest.open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    contact_sheets(rows, args.output_dir)
    (args.output_dir / "audit_summary.json").write_text(
        json.dumps(summary, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, indent=2))
    print(f"Review manifest: {manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
