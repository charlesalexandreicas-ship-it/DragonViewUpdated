"""Audit an external frame dataset before it enters DragonView training.

The audit is intentionally non-destructive. It records integrity, exact hashes,
sharpness, adjacent-frame similarity, and conservative capture groups. Sequential
``frame_######`` files are treated as one capture session; they are never counted
as independent fruit specimens or divided across train/validation/test.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image


DEFAULT_SOURCE = Path(
    r"C:\Users\Charlesalexandre\Downloads\archive\local_datasets\dataset"
)
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "outputs" / "external_dataset_audit"
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
SOURCE_POLICY = {
    "Ripe_frames": {"target": "Class A", "approved": True},
    "Overripe_frames": {"target": "Class B", "approved": True},
    "Ripe2_frames": {"target": "Class C", "approved": True},
    "Rotten_frames": {"target": None, "approved": False},
}
FRAME_NAME = re.compile(r"^frame_(\d+)$", re.IGNORECASE)
PHONE_TIMESTAMP = re.compile(r"^IMG(20\d{6})(\d{4})", re.IGNORECASE)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def capture_group(path: Path) -> str:
    """Infer only defensible groups from filenames; do not split video frames."""
    if FRAME_NAME.match(path.stem):
        return f"{path.parent.name}/video_sequence_01"
    timestamp = PHONE_TIMESTAMP.match(path.stem)
    if timestamp:
        return f"{path.parent.name}/phone_{timestamp.group(1)}_{timestamp.group(2)}"
    return f"{path.parent.name}/standalone_{path.stem.casefold()}"


def image_metrics(path: Path) -> tuple[int, int, float, str]:
    with Image.open(path) as opened:
        width, height = opened.size
        opened.draft("L", (256, 256))
        gray = np.asarray(opened.convert("L").resize((256, 256)), dtype=np.float32)
    laplacian = (
        -4 * gray
        + np.roll(gray, 1, axis=0)
        + np.roll(gray, -1, axis=0)
        + np.roll(gray, 1, axis=1)
        + np.roll(gray, -1, axis=1)
    )
    sharpness = float(laplacian[1:-1, 1:-1].var())
    small = np.asarray(Image.fromarray(gray.astype(np.uint8)).resize((9, 8)))
    bits = small[:, 1:] > small[:, :-1]
    packed = np.packbits(bits.reshape(-1))
    return width, height, sharpness, packed.tobytes().hex()


def hamming(left_hex: str, right_hex: str) -> int:
    left = bytes.fromhex(left_hex)
    right = bytes.fromhex(right_hex)
    return sum((a ^ b).bit_count() for a, b in zip(left, right))


def audit(source: Path) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if not source.is_dir():
        raise ValueError(f"Dataset directory not found: {source}")

    rows: list[dict[str, Any]] = []
    exact_locations: dict[str, list[tuple[str, str]]] = defaultdict(list)
    summary_folders: dict[str, Any] = {}

    for folder_name, policy in SOURCE_POLICY.items():
        folder = source / folder_name
        if not folder.is_dir():
            raise ValueError(f"Expected source folder not found: {folder}")
        paths = sorted(
            path
            for path in folder.iterdir()
            if path.is_file() and path.suffix.casefold() in IMAGE_EXTENSIONS
        )
        corrupt = 0
        previous_hash: str | None = None
        adjacent_near_duplicates = 0
        sharpness_values: list[float] = []
        dimensions: Counter[str] = Counter()
        groups: set[str] = set()

        for path in paths:
            digest = sha256(path)
            exact_locations[digest].append((folder_name, path.name))
            group = capture_group(path)
            groups.add(group)
            row: dict[str, Any] = {
                "source_folder": folder_name,
                "source_file": path.name,
                "source_path": str(path.resolve()),
                "target_class": policy["target"] or "EXCLUDED",
                "approved_mapping": bool(policy["approved"]),
                "capture_group": group,
                "sha256": digest,
                "readable": False,
                "width": None,
                "height": None,
                "sharpness_laplacian_variance": None,
                "difference_hash": None,
                "adjacent_hash_distance": None,
                "audit_decision": "excluded_by_policy" if not policy["approved"] else "candidate_only",
                "audit_reason": "Rotten_frames was not approved" if not policy["approved"] else "pending dataset-level diversity gate",
            }
            try:
                width, height, sharpness, difference_hash = image_metrics(path)
                row.update(
                    {
                        "readable": True,
                        "width": width,
                        "height": height,
                        "sharpness_laplacian_variance": round(sharpness, 4),
                        "difference_hash": difference_hash,
                    }
                )
                dimensions[f"{width}x{height}"] += 1
                sharpness_values.append(sharpness)
                if previous_hash is not None:
                    distance = hamming(previous_hash, difference_hash)
                    row["adjacent_hash_distance"] = distance
                    if distance <= 3:
                        adjacent_near_duplicates += 1
                previous_hash = difference_hash
            except Exception as error:  # preserve the failing file in the audit
                corrupt += 1
                row["audit_decision"] = "rejected"
                row["audit_reason"] = f"unreadable image: {type(error).__name__}"
            rows.append(row)

        quantiles = {}
        if sharpness_values:
            values = np.asarray(sharpness_values)
            quantiles = {
                key: round(float(value), 4)
                for key, value in zip(
                    ("p01", "p05", "p10", "p50", "p90"),
                    np.quantile(values, (0.01, 0.05, 0.10, 0.50, 0.90)),
                )
            }
        summary_folders[folder_name] = {
            "target_class": policy["target"],
            "approved_mapping": bool(policy["approved"]),
            "files": len(paths),
            "readable": len(paths) - corrupt,
            "corrupt": corrupt,
            "capture_groups_inferred_from_filenames": len(groups),
            "capture_groups": sorted(groups),
            "adjacent_frames_with_dhash_distance_at_most_3": adjacent_near_duplicates,
            "dimensions": dict(dimensions),
            "sharpness_quantiles": quantiles,
        }

    duplicates = [locations for locations in exact_locations.values() if len(locations) > 1]
    cross_class_duplicates = [
        locations for locations in duplicates if len({folder for folder, _ in locations}) > 1
    ]
    class_groups: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        if row["approved_mapping"] and row["readable"]:
            class_groups[row["target_class"]].add(row["capture_group"])

    blockers: list[str] = []
    for label in ("Class A", "Class B", "Class C"):
        count = len(class_groups[label])
        if count < 3:
            blockers.append(
                f"{label} has {count} defensible capture group(s); at least 3 are required "
                "for leakage-free train/validation/test coverage"
            )
    if cross_class_duplicates:
        blockers.append(
            f"{len(cross_class_duplicates)} exact image(s) occur across differently labeled folders"
        )

    # This source remains quarantined whenever a class cannot be evaluated safely.
    ready = not blockers
    if not ready:
        for row in rows:
            if row["approved_mapping"] and row["audit_decision"] == "candidate_only":
                row["audit_decision"] = "quarantined"
                row["audit_reason"] = "dataset-level diversity gate failed; not imported into training"

    summary = {
        "source": str(source.resolve()),
        "folder_policy": SOURCE_POLICY,
        "folders": summary_folders,
        "exact_duplicate_sets": len(duplicates),
        "cross_folder_exact_duplicate_sets": len(cross_class_duplicates),
        "independent_capture_groups_by_class": {
            label: len(class_groups[label]) for label in ("Class A", "Class B", "Class C")
        },
        "ready_for_three_class_training_and_evaluation": ready,
        "blockers": blockers,
        "action": "approved sources quarantined; no training data or model assets changed" if not ready else "eligible for controlled import",
    }
    return rows, summary


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        rows, summary = audit(args.source)
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    args.output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.output_dir / "file_audit.csv"
    with csv_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    json_path = args.output_dir / "audit_summary.json"
    json_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print(json.dumps(summary, indent=2))
    print(f"File audit: {csv_path.resolve()}")
    print(f"Summary: {json_path.resolve()}")
    return 0 if summary["ready_for_three_class_training_and_evaluation"] else 3


if __name__ == "__main__":
    raise SystemExit(main())
