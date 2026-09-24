"""Create a non-destructive, model-assisted review of the DragonView dataset.

The source ``raw`` tree is never edited. Unique images are evaluated with the
trained MobileNet model and the same red/magenta coverage signal used by the
Android scanner. Only agreeing signals enter the confident Class A/Class B
buckets; disagreements are routed to ``uncertain`` for human review.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
IMAGE_SIZE = 224
CLASS_A_SHADE_MIN = 0.78
CLASS_B_SHADE_MAX = 0.70
MODEL_THRESHOLD = 0.50
ML_DIR = Path(__file__).resolve().parent
RAW_DIR = ML_DIR / "datasets" / "dragon_fruit_quality" / "raw"
MODEL_PATH = ML_DIR / "outputs" / "dragonview_mobilenet_v2.keras"
DEFAULT_OUTPUT = (
    ML_DIR / "datasets" / "dragon_fruit_quality" / "segregated" / "hybrid_review_v1"
)


@dataclass
class Record:
    source: Path
    original_class: str
    sha256: str
    duplicate_of: str = ""
    class_a_probability: float | None = None
    class_b_probability: float | None = None
    red_magenta_ratio: float | None = None
    bucket: str = ""
    reason: str = ""
    output: str = ""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, default=RAW_DIR)
    parser.add_argument("--model", type=Path, default=MODEL_PATH)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument(
        "--materialize",
        action="store_true",
        help="Copy unique images into class_a, class_b, and uncertain folders.",
    )
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def center_crop_224(path: Path) -> np.ndarray:
    with Image.open(path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")
        width, height = image.size
        side = min(width, height)
        left = (width - side) // 2
        top = (height - side) // 2
        image = image.crop((left, top, left + side, top + side))
        image = image.resize((IMAGE_SIZE, IMAGE_SIZE), Image.Resampling.BILINEAR)
        return np.asarray(image, dtype=np.uint8)


def red_magenta_ratio(rgb: np.ndarray) -> float:
    """Match MobileNetV2Classifier.redMagentaRatio on the 224px center crop."""
    values = rgb.astype(np.float32) / 255.0
    maximum = values.max(axis=2)
    minimum = values.min(axis=2)
    difference = maximum - minimum
    saturation = np.divide(
        difference,
        maximum,
        out=np.zeros_like(difference),
        where=maximum != 0,
    )

    yy, xx = np.ogrid[:IMAGE_SIZE, :IMAGE_SIZE]
    center = (IMAGE_SIZE - 1) / 2.0
    radius = IMAGE_SIZE * (90.0 / 224.0)
    ellipse = ((xx - center) / radius) ** 2 + ((yy - center) / radius) ** 2 <= 1.0
    eligible = ellipse & (saturation > 55.0 / 255.0) & (maximum > 35.0 / 255.0)
    eligible_count = int(eligible.sum())
    if eligible_count == 0:
        return 0.0

    red, green, blue = values[..., 0], values[..., 1], values[..., 2]
    hue = np.zeros_like(maximum)
    nonzero = difference != 0
    red_max = nonzero & (maximum == red)
    green_max = nonzero & (maximum == green)
    blue_max = nonzero & (maximum == blue)
    hue[red_max] = 60.0 * np.mod((green[red_max] - blue[red_max]) / difference[red_max], 6.0)
    hue[green_max] = 60.0 * (((blue[green_max] - red[green_max]) / difference[green_max]) + 2.0)
    hue[blue_max] = 60.0 * (((red[blue_max] - green[blue_max]) / difference[blue_max]) + 4.0)
    hue[hue < 0.0] += 360.0
    red_magenta = eligible & ((hue <= 8.0) | (hue >= 225.0))
    return float(red_magenta.sum() / eligible_count)


def collect(raw_dir: Path) -> tuple[list[Record], list[Record]]:
    unique: list[Record] = []
    duplicates: list[Record] = []
    first_by_hash: dict[str, Record] = {}
    for original_class in ("class_a", "class_b"):
        class_dir = raw_dir / original_class
        if not class_dir.is_dir():
            raise FileNotFoundError(f"Missing class directory: {class_dir}")
        paths = sorted(
            path for path in class_dir.rglob("*")
            if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
        )
        for path in paths:
            digest = sha256(path)
            if digest in first_by_hash:
                first = first_by_hash[digest]
                if first.original_class != original_class:
                    raise ValueError(f"Conflicting source labels for identical image: {path}")
                duplicates.append(
                    Record(path, original_class, digest, duplicate_of=str(first.source))
                )
                continue
            record = Record(path, original_class, digest)
            first_by_hash[digest] = record
            unique.append(record)
    return unique, duplicates


def classify(records: list[Record], model_path: Path, batch_size: int) -> None:
    import tensorflow as tf

    model = tf.keras.models.load_model(model_path, compile=False)
    for start in range(0, len(records), batch_size):
        batch_records = records[start : start + batch_size]
        crops = [center_crop_224(record.source) for record in batch_records]
        inputs = np.stack(crops).astype(np.float32) / 127.5 - 1.0
        probabilities = np.asarray(model.predict(inputs, verbose=0), dtype=np.float32)
        if probabilities.shape != (len(batch_records), 2):
            raise ValueError(f"Expected model output [batch, 2], found {probabilities.shape}")

        for record, crop, scores in zip(batch_records, crops, probabilities):
            probability_a = float(scores[0])
            probability_b = float(scores[1])
            shade_ratio = red_magenta_ratio(crop)
            model_a = probability_a >= MODEL_THRESHOLD
            shade_a = shade_ratio >= CLASS_A_SHADE_MIN
            shade_b = shade_ratio <= CLASS_B_SHADE_MAX
            record.class_a_probability = probability_a
            record.class_b_probability = probability_b
            record.red_magenta_ratio = shade_ratio
            if model_a and shade_a:
                record.bucket = "class_a"
                record.reason = "model_and_shade_agree_a"
            elif not model_a and shade_b:
                record.bucket = "class_b"
                record.reason = "model_and_shade_agree_b"
            else:
                record.bucket = "uncertain"
                record.reason = "model_shade_disagreement"


def relative_source(record: Record, raw_dir: Path) -> str:
    return record.source.relative_to(raw_dir).as_posix()


def output_name(record: Record) -> str:
    return f"{record.sha256[:12]}__{record.source.name}"


def write_outputs(
    records: list[Record], duplicates: list[Record], raw_dir: Path, output_dir: Path,
    model_path: Path, materialize: bool,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    if materialize:
        for bucket in ("class_a", "class_b", "uncertain"):
            (output_dir / bucket).mkdir(exist_ok=True)
        for record in records:
            target = output_dir / record.bucket / output_name(record)
            previous = [
                output_dir / bucket / output_name(record)
                for bucket in ("class_a", "class_b", "uncertain")
                if bucket != record.bucket
                and (output_dir / bucket / output_name(record)).exists()
            ]
            if target.exists():
                if sha256(target) != record.sha256:
                    raise FileExistsError(f"Refusing to overwrite different file: {target}")
            elif previous:
                if len(previous) != 1 or sha256(previous[0]) != record.sha256:
                    raise FileExistsError(f"Ambiguous existing reviewed file for: {record.source}")
                previous[0].replace(target)
            else:
                shutil.copy2(record.source, target)
            record.output = target.relative_to(output_dir).as_posix()

    fields = [
        "source", "original_class", "sha256", "duplicate_of",
        "class_a_probability", "class_b_probability", "red_magenta_ratio",
        "bucket", "reason", "output",
    ]
    with (output_dir / "segregation_manifest.csv").open(
        "w", newline="", encoding="utf-8"
    ) as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for record in records:
            writer.writerow({
                "source": relative_source(record, raw_dir),
                "original_class": record.original_class,
                "sha256": record.sha256,
                "duplicate_of": "",
                "class_a_probability": f"{record.class_a_probability:.8f}",
                "class_b_probability": f"{record.class_b_probability:.8f}",
                "red_magenta_ratio": f"{record.red_magenta_ratio:.8f}",
                "bucket": record.bucket,
                "reason": record.reason,
                "output": record.output,
            })
        for record in duplicates:
            writer.writerow({
                "source": relative_source(record, raw_dir),
                "original_class": record.original_class,
                "sha256": record.sha256,
                "duplicate_of": str(Path(record.duplicate_of).relative_to(raw_dir).as_posix()),
                "class_a_probability": "",
                "class_b_probability": "",
                "red_magenta_ratio": "",
                "bucket": "duplicate",
                "reason": "exact_sha256_duplicate",
                "output": "",
            })

    bucket_counts = Counter(record.bucket for record in records)
    agreement = Counter(
        (record.original_class, record.bucket) for record in records
    )
    report = {
        "source": str(raw_dir),
        "model": str(model_path),
        "policy": {
            "class_a": f"MobileNet P(A) >= {MODEL_THRESHOLD} and shade >= {CLASS_A_SHADE_MIN}",
            "class_b": f"MobileNet P(A) < {MODEL_THRESHOLD} and shade <= {CLASS_B_SHADE_MAX}",
            "uncertain": "Signals disagree or shade falls inside the review margin",
        },
        "unique_images": len(records),
        "exact_duplicates_excluded": len(duplicates),
        "bucket_counts": dict(sorted(bucket_counts.items())),
        "source_vs_review_bucket": {
            f"{source}->{bucket}": count
            for (source, bucket), count in sorted(agreement.items())
        },
        "images_materialized": materialize,
    }
    (output_dir / "segregation_report.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8"
    )
    print(json.dumps(report, indent=2))


def main() -> None:
    args = parse_args()
    raw_dir = args.raw_dir.resolve()
    model_path = args.model.resolve()
    output_dir = args.output.resolve()
    if output_dir == raw_dir or raw_dir in output_dir.parents:
        raise ValueError("Output must not be the raw dataset or a child of it")
    unique, duplicates = collect(raw_dir)
    classify(unique, model_path, args.batch_size)
    write_outputs(
        unique, duplicates, raw_dir, output_dir, model_path, args.materialize
    )


if __name__ == "__main__":
    main()
