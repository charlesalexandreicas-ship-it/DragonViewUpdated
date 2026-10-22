"""Build an isolated, reproducible three-class *prototype* dataset.

The downloaded Fresh/Defect collection has no verified A/B labels or physical
fruit IDs. Selected images are therefore training-only. Four conservative
Defect sequences are separate provisional groups for a pipeline demonstration;
this is not a final independent-fruit evaluation.
"""

from __future__ import annotations

import csv
import hashlib
import json
import shutil
from collections import Counter, defaultdict
from pathlib import Path

import train_classifier as trainer


ML_DIR = Path(__file__).resolve().parent
SELECTION = ML_DIR / "preliminary_quality_selection.json"
SOURCE_MANIFEST = ML_DIR / "outputs" / "quality_grading_review" / "review_manifest.csv"
OUTPUT = ML_DIR / "outputs" / "preliminary_three_class"
RAW = OUTPUT / "dataset" / "raw"


def build() -> dict[str, object]:
    selection_digest = hashlib.sha256(SELECTION.read_bytes()).hexdigest()
    if RAW.exists():
        summary_path = OUTPUT / "dataset_summary.json"
        if summary_path.is_file():
            existing = json.loads(summary_path.read_text(encoding="utf-8"))
            if existing.get("selection_sha256") == selection_digest:
                return existing
        raise ValueError(f"Prototype dataset exists with a different selection; refusing to overwrite: {RAW}")
    selection = json.loads(SELECTION.read_text(encoding="utf-8"))
    with SOURCE_MANIFEST.open(newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))
    by_pool: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_pool[row["source_pool"]].append(row)

    original, _ = trainer.load_examples()
    b_groups: dict[str, list[trainer.Example]] = defaultdict(list)
    for record in original:
        if record.class_name == "class_b":
            b_groups[record.capture_group].append(record)
    # Select complete capture groups, distributed across the pre-existing
    # source batches. Never split a burst to hit an exact image target.
    b_selected: list[trainer.Example] = []
    ordered_b = sorted(b_groups, key=lambda name: ("training_only" in name, name))
    for name in ordered_b:
        b_selected.extend(b_groups[name])
        if len(b_selected) >= 400:
            break
    chosen = [record for record in original if record.class_name != "class_b"] + b_selected

    copied: list[dict[str, str]] = []
    seen: dict[str, str] = {}
    duplicates = 0

    def copy(source: Path, label: str, relative: Path, group: str, origin: str) -> None:
        nonlocal duplicates
        digest = trainer.file_hash(source)
        prior = seen.get(digest)
        if prior:
            if prior != label:
                raise ValueError(f"Conflicting class labels for an exact image: {source}")
            duplicates += 1
            return
        seen[digest] = label
        target = RAW / label / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        copied.append({
            "class": label, "source_path": str(source.resolve()),
            "dataset_path": str(target.resolve()), "capture_group": group,
            "sha256": digest, "origin": origin,
        })

    for record in chosen:
        relative = record.path.relative_to(trainer.RAW_DATASET_DIR / record.class_name)
        copy(record.path, record.class_name, relative, record.capture_group, "existing")

    for label, field in (("class_a", "fresh_class_a_ids"), ("class_b", "fresh_class_b_ids")):
        for image_id in selection[field]:
            row = by_pool["fresh"][image_id - 1]
            source = Path(row["source_path"])
            if not source.stem.endswith(f"{image_id:04d}"):
                raise ValueError(f"Manifest image number does not match selection: {source}")
            copy(source, label, Path("fresh_prototype_training_only") / source.name,
                 f"{label}/fresh_prototype_training_only/single_source_session",
                 "visual_prototype_training_only")

    for group_number, (group_name, limits) in enumerate(selection["defect_class_c_groups"].items(), 1):
        start, end = limits
        if start < 1 or end > len(by_pool["defect"]) or start > end:
            raise ValueError(f"Invalid defect selection range: {group_name}")
        for image_id in range(start, end + 1):
            row = by_pool["defect"][image_id - 1]
            source = Path(row["source_path"])
            if not source.stem.endswith(f"{image_id:04d}"):
                raise ValueError(f"Manifest image number does not match selection: {source}")
            name = f"IMG_20260925_10{group_number:04d}_BURST{image_id:04d}.jpg"
            copy(source, "class_c", Path("defect_prototype") / name,
                 f"class_c/defect_prototype/img_20260925_10{group_number:04d}",
                 f"provisional_defect_group:{group_name}")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    with (OUTPUT / "source_manifest.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(copied[0]))
        writer.writeheader()
        writer.writerows(copied)
    summary = {
        "status": "preliminary_prototype_not_final_research_validation",
        "counts": dict(Counter(row["class"] for row in copied)),
        "exact_duplicates_skipped": duplicates,
        "selection_sha256": selection_digest,
        "limitations": selection["review_limitations"],
    }
    (OUTPUT / "dataset_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary


if __name__ == "__main__":
    print(json.dumps(build(), indent=2))
