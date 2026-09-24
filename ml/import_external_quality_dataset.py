"""Import a leakage-reduced, training-only subset of the approved frame dataset.

This is an explicit project-owner override of the diversity audit. Sequential
video frames are sampled every tenth frame and remain one capture group. They are
never eligible for validation or final test metrics.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = Path(r"C:\Users\Charlesalexandre\Downloads\archive\local_datasets\dataset")
RAW_ROOT = ROOT / "ml" / "datasets" / "dragon_fruit_quality" / "raw"
MAPPINGS = {
    "Ripe_frames": ("class_a", "internet_ripe_training_only"),
    "Overripe_frames": ("class_b", "internet_overripe_training_only"),
    "Ripe2_frames": ("class_c", "internet_ripe2_training_only"),
}
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def selected(paths: list[Path], stride: int) -> list[Path]:
    chosen: list[Path] = []
    for path in paths:
        if path.stem.casefold().startswith("frame_"):
            try:
                frame_number = int(path.stem.rsplit("_", 1)[1])
            except ValueError:
                continue
            if frame_number % stride == 0:
                chosen.append(path)
        else:
            chosen.append(path)
    return chosen


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--stride", type=int, default=10)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.stride < 1:
        raise SystemExit("--stride must be at least 1")
    rows: list[dict[str, str | int]] = []
    for source_folder, (class_name, batch_name) in MAPPINGS.items():
        source_dir = args.source / source_folder
        if not source_dir.is_dir():
            raise SystemExit(f"Missing approved source folder: {source_dir}")
        destination = RAW_ROOT / class_name / batch_name
        destination.mkdir(parents=True, exist_ok=True)
        existing = [path for path in destination.iterdir() if path.is_file()]
        if existing:
            raise SystemExit(
                f"Import destination is not empty: {destination}. "
                "Remove the prior controlled import before rerunning."
            )
        paths = sorted(
            path for path in source_dir.iterdir()
            if path.is_file() and path.suffix.casefold() in IMAGE_EXTENSIONS
        )
        chosen = selected(paths, args.stride)
        for source_path in chosen:
            destination_name = f"{source_folder}__{source_path.name}"
            destination_path = destination / destination_name
            shutil.copy2(source_path, destination_path)
            rows.append(
                {
                    "source_folder": source_folder,
                    "source_path": str(source_path.resolve()),
                    "target_class": class_name,
                    "target_batch": batch_name,
                    "destination_path": str(destination_path.resolve()),
                    "sha256": file_hash(destination_path),
                    "frame_stride": args.stride,
                    "split_policy": "training_only_single_capture_group",
                }
            )
        print(f"{source_folder} -> {class_name}: {len(chosen)} imported from {len(paths)} files")

    manifest_path = RAW_ROOT / "external_training_only_manifest.csv"
    with manifest_path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Manifest: {manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
