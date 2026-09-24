"""Prepare, train, validate, and export DragonView's offline YOLO26 detector.

Label Studio grade rectangles (A/B) are collapsed into the single detection
class ``dragon_fruit``. The MobileNetV2 model grades each detected crop when
the detector confirms that between one and eight fruits are visible.
"""

from __future__ import annotations

import argparse
import json
import random
import re
import shutil
import os
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


SEED = 20260913
IMAGE_SIZE = 320
MAX_SCANNER_FRUIT_COUNT = 8
NMS_IOU = 0.45

ML_DIR = Path(__file__).resolve().parent
PROJECT_DIR = ML_DIR.parent
DEFAULT_ANNOTATIONS = PROJECT_DIR.parent / "ANNOTATED DATASET"
DEFAULT_IMAGE_ROOT = PROJECT_DIR / "DataSet"
PREPARED_DIR = ML_DIR / "datasets" / "dragon_fruit_detection"
OUTPUT_DIR = ML_DIR / "outputs"
ASSET_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets" / "ml"

EXPORT_TO_FOLDER = {"project-7": "By 4", "project-9": "By 8"}
CAPTURE_PATTERN = re.compile(r"^(IMG_\d{8}_\d{6})", re.IGNORECASE)
UPLOAD_PREFIX = re.compile(r"^[0-9a-f]{8}-", re.IGNORECASE)


@dataclass(frozen=True)
class Example:
    image_path: Path
    capture_group: str
    boxes: tuple[tuple[float, float, float, float], ...]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--annotations-dir", type=Path, default=DEFAULT_ANNOTATIONS)
    parser.add_argument("--image-root", type=Path, default=DEFAULT_IMAGE_ROOT)
    parser.add_argument("--epochs", type=int, default=60)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--weights", type=Path,
                        help="Validate/export existing trained weights without retraining")
    parser.add_argument("--audit-only", action="store_true")
    parser.add_argument("--prepare-only", action="store_true")
    return parser.parse_args()


def source_folder(export_name: str) -> str:
    for prefix, folder in EXPORT_TO_FOLDER.items():
        if export_name.startswith(prefix):
            return folder
    raise ValueError(f"No image-folder mapping for {export_name}")


def load_examples(annotations_dir: Path, image_root: Path) -> tuple[list[Example], dict]:
    examples: list[Example] = []
    grades: Counter[str] = Counter()
    skipped = clamped = 0
    exports = sorted(annotations_dir.glob("*.json"))
    if not exports:
        raise ValueError(f"No Label Studio JSON files found in {annotations_dir}")

    for export in exports:
        folder = image_root / source_folder(export.name)
        for task in json.loads(export.read_text(encoding="utf-8")):
            uploaded = Path(task["data"]["image"]).name
            filename = UPLOAD_PREFIX.sub("", uploaded)
            image_path = folder / filename
            if not image_path.is_file():
                raise ValueError(f"Referenced image is missing: {image_path}")
            match = CAPTURE_PATTERN.match(filename)
            group = f"{folder.name}/{(match.group(1) if match else Path(filename).stem).lower()}"
            boxes = []
            for annotation in task.get("annotations") or []:
                for result in annotation.get("result") or []:
                    if result.get("type") != "rectanglelabels":
                        continue
                    value = result.get("value") or {}
                    labels = value.get("rectanglelabels") or []
                    if len(labels) != 1 or labels[0] not in {"A", "B"}:
                        skipped += 1
                        continue
                    grades[labels[0]] += 1
                    x1 = float(value["x"]) / 100.0
                    y1 = float(value["y"]) / 100.0
                    x2 = x1 + float(value["width"]) / 100.0
                    y2 = y1 + float(value["height"]) / 100.0
                    original = (x1, y1, x2, y2)
                    x1, y1 = max(0.0, x1), max(0.0, y1)
                    x2, y2 = min(1.0, x2), min(1.0, y2)
                    clamped += original != (x1, y1, x2, y2)
                    if x2 <= x1 or y2 <= y1:
                        raise ValueError(f"Empty box after clamping: {image_path}")
                    boxes.append((x1, y1, x2, y2))
            if not boxes:
                raise ValueError(f"No valid boxes: {image_path}")
            examples.append(Example(image_path, group, tuple(boxes)))

    return examples, {
        "images": len(examples),
        "boxes": sum(len(item.boxes) for item in examples),
        "grade_boxes": dict(sorted(grades.items())),
        "capture_groups": len({item.capture_group for item in examples}),
        "skipped_missing_label": skipped,
        "clamped_boxes": clamped,
    }


def split_by_capture_group(examples: list[Example]) -> dict[str, list[Example]]:
    rng = random.Random(SEED)
    grouped: dict[str, list[Example]] = defaultdict(list)
    for item in examples:
        grouped[item.capture_group].append(item)
    by_source: dict[str, list[str]] = defaultdict(list)
    for group in grouped:
        by_source[group.split("/", 1)[0]].append(group)

    splits = {"train": [], "val": [], "test": []}
    for source, groups in sorted(by_source.items()):
        groups = sorted(groups)
        rng.shuffle(groups)
        validation_count = max(1, round(len(groups) * 0.15))
        test_count = max(1, round(len(groups) * 0.15))
        assignments = {
            "val": groups[:validation_count],
            "test": groups[validation_count:validation_count + test_count],
            "train": groups[validation_count + test_count:],
        }
        for split, names in assignments.items():
            for name in names:
                splits[split].extend(grouped[name])
    for records in splits.values():
        rng.shuffle(records)
    return splits


def prepare_dataset(splits: dict[str, list[Example]]) -> dict:
    if PREPARED_DIR.exists():
        shutil.rmtree(PREPARED_DIR)
    for split in splits:
        (PREPARED_DIR / "images" / split).mkdir(parents=True, exist_ok=True)
        (PREPARED_DIR / "labels" / split).mkdir(parents=True, exist_ok=True)

    manifest = {"seed": SEED, "splits": {}}
    for split, examples in splits.items():
        records = []
        for index, example in enumerate(examples):
            destination_name = f"{split}_{index:04d}_{example.image_path.name}"
            destination = PREPARED_DIR / "images" / split / destination_name
            shutil.copy2(example.image_path, destination)
            lines = []
            for x1, y1, x2, y2 in example.boxes:
                lines.append(f"0 {(x1+x2)/2:.8f} {(y1+y2)/2:.8f} {x2-x1:.8f} {y2-y1:.8f}")
            (PREPARED_DIR / "labels" / split / f"{Path(destination_name).stem}.txt").write_text(
                "\n".join(lines) + "\n", encoding="utf-8"
            )
            records.append({
                "prepared_image": str(destination.relative_to(PROJECT_DIR)),
                "source_image": str(example.image_path),
                "capture_group": example.capture_group,
                "boxes": len(example.boxes),
            })
        manifest["splits"][split] = records

    data_yaml = (
        f"path: '{PREPARED_DIR.as_posix()}'\n"
        "train: images/train\nval: images/val\ntest: images/test\n"
        "names:\n  0: dragon_fruit\n"
    )
    (PREPARED_DIR / "data.yaml").write_text(data_yaml, encoding="utf-8")
    (PREPARED_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return {
        split: {
            "images": len(records),
            "boxes": sum(len(item.boxes) for item in records),
            "capture_groups": len({item.capture_group for item in records}),
        }
        for split, records in splits.items()
    }


def intersection_over_union(first, second) -> float:
    x1, y1 = max(first[0], second[0]), max(first[1], second[1])
    x2, y2 = min(first[2], second[2]), min(first[3], second[3])
    intersection = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    area1 = max(0.0, first[2] - first[0]) * max(0.0, first[3] - first[1])
    area2 = max(0.0, second[2] - second[0]) * max(0.0, second[3] - second[1])
    return intersection / max(area1 + area2 - intersection, 1e-9)


def evaluate_predictions(model, examples: list[Example], confidence: float) -> dict:
    predictions = model.predict(
        [str(item.image_path) for item in examples], imgsz=IMAGE_SIZE,
        conf=confidence, iou=NMS_IOU, device="cpu", verbose=False,
    )
    true_positive = false_positive = false_negative = exact = absolute_error = 0
    for prediction, example in zip(predictions, examples):
        width, height = prediction.orig_shape[1], prediction.orig_shape[0]
        detected = [
            (float(box[0]) / width, float(box[1]) / height,
             float(box[2]) / width, float(box[3]) / height)
            for box in prediction.boxes.xyxy.cpu().numpy()
        ]
        matched = set()
        for box in detected:
            choices = [(intersection_over_union(box, truth), i)
                       for i, truth in enumerate(example.boxes) if i not in matched]
            best_iou, best_index = max(choices, default=(0.0, -1))
            if best_iou >= 0.5:
                true_positive += 1
                matched.add(best_index)
            else:
                false_positive += 1
        false_negative += len(example.boxes) - len(matched)
        exact += len(detected) == len(example.boxes)
        absolute_error += abs(len(detected) - len(example.boxes))
    precision = true_positive / max(true_positive + false_positive, 1)
    recall = true_positive / max(true_positive + false_negative, 1)
    f1 = 2 * precision * recall / max(precision + recall, 1e-9)
    return {
        "confidence_threshold": round(confidence, 2),
        "precision_iou50": round(precision, 4),
        "recall_iou50": round(recall, 4),
        "f1_iou50": round(f1, 4),
        "exact_count_accuracy": round(exact / len(examples), 4),
        "mean_absolute_count_error": round(absolute_error / len(examples), 4),
        "true_positive": true_positive,
        "false_positive": false_positive,
        "false_negative": false_negative,
    }


def train(args, splits, audit, split_report) -> None:
    from ultralytics import YOLO

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if args.weights:
        best_path = args.weights.resolve()
    else:
        model = YOLO(str(PROJECT_DIR / "yolo26n.pt"))
        result = model.train(
            data=str(PREPARED_DIR / "data.yaml"), epochs=args.epochs,
            imgsz=IMAGE_SIZE, batch=args.batch_size, device=args.device,
            workers=0, seed=SEED, deterministic=True, patience=12,
            project=str(OUTPUT_DIR), name="yolo26_detector", exist_ok=True,
            pretrained=True, plots=True, verbose=True,
        )
        best_path = Path(result.save_dir) / "weights" / "best.pt"
    best_model = YOLO(str(best_path))

    candidates = [evaluate_predictions(best_model, splits["val"], threshold / 100.0)
                  for threshold in range(10, 76, 5)]
    best_validation = max(
        candidates,
        key=lambda item: (item["f1_iou50"], item["exact_count_accuracy"],
                          -item["mean_absolute_count_error"]),
    )
    threshold = best_validation["confidence_threshold"]
    test_metrics = evaluate_predictions(best_model, splits["test"], threshold)

    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    asset_model = ASSET_DIR / "dragon_fruit_detector.tflite"
    shutil.copy2(best_path, OUTPUT_DIR / "dragon_fruit_detector.best.pt")

    config = {
        "model": "YOLO26n",
        "input_size": IMAGE_SIZE,
        "confidence_threshold": threshold,
        "nms_iou_threshold": NMS_IOU,
        "max_scanner_fruit_count": MAX_SCANNER_FRUIT_COUNT,
        "class_names": ["dragon_fruit"],
        "preprocessing": "RGB letterbox, 114 padding, divide by 255",
        "output": "end-to-end xyxy, confidence, class [1,300,6]",
    }
    report = {
        "audit": audit,
        "splits": split_report,
        "validation": best_validation,
        "test": test_metrics,
        "negative_scene_test": "not_available",
    }
    (ASSET_DIR / "detector_config.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
    (OUTPUT_DIR / "detector_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    if os.name == "nt":
        # Ultralytics' direct LiteRT exporter is Linux/macOS-only. On Windows,
        # export ONNX then use onnx2tf's native flatbuffer path.
        import onnx
        from onnx2tf import convert
        from onnxsim import simplify
        onnx_path = Path(best_model.export(format="onnx", imgsz=IMAGE_SIZE,
                                           simplify=False, nms=False))
        simplified_path = onnx_path.with_name(f"{onnx_path.stem}_sim.onnx")
        simplified, valid = simplify(
            onnx.load(str(onnx_path)),
            overwrite_input_shapes={"images": [1, 3, IMAGE_SIZE, IMAGE_SIZE]},
        )
        if not valid:
            raise ValueError("Simplified ONNX detector did not pass validation")
        onnx.save(simplified, str(simplified_path))
        litert_dir = OUTPUT_DIR / "yolo26_detector" / "litert"
        convert(input_onnx_file_path=str(simplified_path),
                output_folder_path=str(litert_dir), output_signaturedefs=True)
        exported = litert_dir / f"{simplified_path.stem}_float32.tflite"
    else:
        exported = Path(best_model.export(format="litert", imgsz=IMAGE_SIZE, nms=False))
    shutil.copy2(exported, asset_model)

    print(json.dumps(report, indent=2))
    print(f"Exported detector: {asset_model}")


def main() -> int:
    args = parse_args()
    try:
        examples, audit = load_examples(args.annotations_dir.resolve(), args.image_root.resolve())
        splits = split_by_capture_group(examples)
        split_report = {
            split: {
                "images": len(records),
                "boxes": sum(len(item.boxes) for item in records),
                "capture_groups": len({item.capture_group for item in records}),
            }
            for split, records in splits.items()
        }
        print(json.dumps({"audit": audit, "splits": split_report}, indent=2))
        if args.audit_only:
            return 0
        prepare_dataset(splits)
        if not args.prepare_only:
            train(args, splits, audit, split_report)
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
