"""Evaluate DragonView's quality classifier per annotated fruit instance.

Ground-truth rectangles come from Label Studio. Only images assigned to the
manifest's held-out test split are evaluated. This intentionally evaluates the
classifier with ground-truth crops; detector quality is measured separately.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from PIL import Image, ImageOps
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MODEL = ROOT / "app" / "src" / "main" / "assets" / "ml" / "mobilenet_v2.tflite"
DEFAULT_LABELS = ROOT / "app" / "src" / "main" / "assets" / "ml" / "labels.txt"
DEFAULT_MANIFEST = ROOT / "ml" / "datasets" / "dragon_fruit_detection" / "manifest.json"
DEFAULT_ANNOTATION_DIR = Path(
    r"C:\Users\Charlesalexandre\Desktop\Capstone Related\ANNOTATED DATASET"
)
DEFAULT_OUTPUT_DIR = ROOT / "ml" / "outputs" / "classifier_evaluation"
EXPECTED_CLASSES = ("Class A", "Class B", "Class C")
LABEL_ALIASES = {
    "a": "Class A",
    "class a": "Class A",
    "class_a": "Class A",
    "b": "Class B",
    "class b": "Class B",
    "class_b": "Class B",
    "c": "Class C",
    "class c": "Class C",
    "class_c": "Class C",
}
UPLOAD_PREFIX = re.compile(r"^[0-9a-fA-F]{8,}-(.+)$")


class EvaluationError(RuntimeError):
    """Raised when an evaluation contract or dataset requirement is violated."""


@dataclass(frozen=True)
class FruitAnnotation:
    image_path: Path
    image_name: str
    capture_group: str
    annotation_id: str
    true_label: str
    x_percent: float
    y_percent: float
    width_percent: float
    height_percent: float


def canonical_label(value: str) -> str:
    key = value.strip().lower()
    if key not in LABEL_ALIASES:
        raise EvaluationError(f"Unsupported annotation label: {value!r}")
    return LABEL_ALIASES[key]


def read_labels(path: Path) -> list[str]:
    if not path.is_file():
        raise EvaluationError(f"Classifier label file not found: {path}")
    return [canonical_label(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def load_manifest(path: Path) -> dict[str, list[dict[str, Any]]]:
    if not path.is_file():
        raise EvaluationError(f"Dataset manifest not found: {path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    splits = data.get("splits", {})
    required = {"train", "val", "test"}
    if not required.issubset(splits):
        raise EvaluationError(f"Manifest must contain splits: {sorted(required)}")
    return splits


def verify_split_isolation(splits: dict[str, list[dict[str, Any]]]) -> None:
    groups = {
        name: {str(item["capture_group"]).casefold() for item in items}
        for name, items in splits.items()
    }
    names = ("train", "val", "test")
    for index, left in enumerate(names):
        for right in names[index + 1 :]:
            overlap = groups[left] & groups[right]
            if overlap:
                examples = ", ".join(sorted(overlap)[:5])
                raise EvaluationError(
                    f"Capture-group leakage between {left} and {right}: {examples}"
                )

    image_splits: dict[str, str] = {}
    for split_name in names:
        for item in splits[split_name]:
            image_name = Path(item["source_image"]).name.casefold()
            previous = image_splits.setdefault(image_name, split_name)
            if previous != split_name:
                raise EvaluationError(
                    f"Image leakage: {image_name} appears in {previous} and {split_name}"
                )


def source_name(task: dict[str, Any]) -> str:
    upload = str(task.get("file_upload") or Path(str(task.get("data", {}).get("image", ""))).name)
    match = UPLOAD_PREFIX.match(upload)
    return match.group(1) if match else upload


def selected_annotation(task: dict[str, Any]) -> dict[str, Any] | None:
    candidates = [item for item in task.get("annotations", []) if not item.get("was_cancelled", False)]
    if not candidates:
        return None
    return max(candidates, key=lambda item: str(item.get("updated_at", item.get("created_at", ""))))


def annotation_files(explicit: Iterable[Path], directory: Path) -> list[Path]:
    files = list(explicit)
    if not files and directory.is_dir():
        files = sorted(directory.glob("*.json"))
    if not files:
        raise EvaluationError(
            "No Label Studio exports found. Pass one or more --annotations JSON files."
        )
    missing = [path for path in files if not path.is_file()]
    if missing:
        raise EvaluationError(f"Annotation export not found: {missing[0]}")
    return files


def collect_test_annotations(
    files: Iterable[Path], test_items: list[dict[str, Any]]
) -> tuple[list[FruitAnnotation], dict[str, int]]:
    test_by_name = {Path(item["source_image"]).name.casefold(): item for item in test_items}
    seen_tasks: set[tuple[str, str]] = set()
    records: list[FruitAnnotation] = []

    for export_path in files:
        tasks = json.loads(export_path.read_text(encoding="utf-8"))
        if not isinstance(tasks, list):
            raise EvaluationError(f"Label Studio export must contain a JSON list: {export_path}")
        for task in tasks:
            image_name = source_name(task)
            item = test_by_name.get(image_name.casefold())
            if item is None:
                continue
            annotation = selected_annotation(task)
            if annotation is None:
                continue
            task_key = (image_name.casefold(), str(annotation.get("id", "")))
            if task_key in seen_tasks:
                continue
            seen_tasks.add(task_key)

            for result_index, result in enumerate(annotation.get("result", [])):
                if result.get("type") != "rectanglelabels":
                    continue
                value = result.get("value", {})
                labels = value.get("rectanglelabels", [])
                if len(labels) != 1:
                    raise EvaluationError(
                        f"Fruit box {result.get('id', result_index)} in {image_name} must have one label"
                    )
                records.append(
                    FruitAnnotation(
                        image_path=Path(item["source_image"]),
                        image_name=image_name,
                        capture_group=str(item["capture_group"]),
                        annotation_id=str(result.get("id", result_index)),
                        true_label=canonical_label(str(labels[0])),
                        x_percent=float(value["x"]),
                        y_percent=float(value["y"]),
                        width_percent=float(value["width"]),
                        height_percent=float(value["height"]),
                    )
                )

    support = {label: 0 for label in EXPECTED_CLASSES}
    for record in records:
        support[record.true_label] += 1
    return records, support


def create_interpreter(model_path: Path):
    if not model_path.is_file():
        raise EvaluationError(f"TFLite model not found: {model_path}")
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        try:
            from tensorflow.lite import Interpreter
        except ImportError as exc:
            raise EvaluationError(
                "No TFLite runtime is installed. Install ml/requirements.txt first."
            ) from exc
    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    return interpreter


def validate_model_contract(interpreter: Any, labels: list[str]) -> tuple[dict[str, Any], dict[str, Any]]:
    inputs = interpreter.get_input_details()
    outputs = interpreter.get_output_details()
    if len(inputs) != 1 or len(outputs) != 1:
        raise EvaluationError("Classifier must expose exactly one input and one output tensor")
    input_info, output_info = inputs[0], outputs[0]
    input_shape = list(map(int, input_info["shape"]))
    output_shape = list(map(int, output_info["shape"]))
    if input_shape != [1, 224, 224, 3] or input_info["dtype"] != np.float32:
        raise EvaluationError(
            f"Expected FLOAT32 input [1, 224, 224, 3], found {input_info['dtype']} {input_shape}"
        )
    if output_shape != [1, len(labels)] or output_info["dtype"] != np.float32:
        raise EvaluationError(
            f"Expected FLOAT32 output [1, {len(labels)}], found {output_info['dtype']} {output_shape}"
        )
    return input_info, output_info


def preflight(labels: list[str], support: dict[str, int], records: list[FruitAnnotation]) -> None:
    problems: list[str] = []
    if labels != list(EXPECTED_CLASSES):
        problems.append(
            "model labels are " + repr(labels) + "; required labels are " + repr(list(EXPECTED_CLASSES))
        )
    missing = [label for label in EXPECTED_CLASSES if support[label] == 0]
    if missing:
        problems.append("held-out test annotations have zero support for " + ", ".join(missing))
    if not records:
        problems.append("no per-fruit annotations matched the held-out test images")
    missing_images = sorted({str(record.image_path) for record in records if not record.image_path.is_file()})
    if missing_images:
        problems.append(f"{len(missing_images)} held-out source image(s) are missing")
    if problems:
        raise EvaluationError("Three-class evaluation cannot run:\n- " + "\n- ".join(problems))


def padded_crop(record: FruitAnnotation, padding: float) -> Image.Image:
    with Image.open(record.image_path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
    width, height = image.size
    left = record.x_percent * width / 100.0
    top = record.y_percent * height / 100.0
    box_width = record.width_percent * width / 100.0
    box_height = record.height_percent * height / 100.0
    pad_x, pad_y = box_width * padding, box_height * padding
    box = (
        max(0, int(np.floor(left - pad_x))),
        max(0, int(np.floor(top - pad_y))),
        min(width, int(np.ceil(left + box_width + pad_x))),
        min(height, int(np.ceil(top + box_height + pad_y))),
    )
    if box[2] <= box[0] or box[3] <= box[1]:
        raise EvaluationError(f"Invalid annotation box {record.annotation_id} in {record.image_name}")
    return image.crop(box)


def preprocess(crop: Image.Image) -> np.ndarray:
    width, height = crop.size
    edge = min(width, height)
    left = (width - edge) // 2
    top = (height - edge) // 2
    square = crop.crop((left, top, left + edge, top + edge))
    resized = square.resize((224, 224), Image.Resampling.BILINEAR)
    return np.expand_dims(np.asarray(resized, dtype=np.float32) / 127.5 - 1.0, axis=0)


def predict(
    interpreter: Any,
    input_info: dict[str, Any],
    output_info: dict[str, Any],
    tensor: np.ndarray,
) -> np.ndarray:
    interpreter.set_tensor(input_info["index"], tensor)
    interpreter.invoke()
    probabilities = np.asarray(interpreter.get_tensor(output_info["index"])[0], dtype=np.float64)
    if not np.all(np.isfinite(probabilities)):
        raise EvaluationError("Model returned a non-finite probability")
    if np.any(probabilities < -1e-5) or not np.isclose(probabilities.sum(), 1.0, atol=1e-3):
        raise EvaluationError("Model output is not a valid softmax probability distribution")
    return probabilities


def compute_metrics(
    y_true: list[str],
    y_pred: list[str],
    metric_labels: Iterable[str] = EXPECTED_CLASSES,
    matrix_labels: Iterable[str] | None = None,
) -> tuple[np.ndarray, pd.DataFrame, dict[str, float]]:
    labels = list(metric_labels)
    displayed_labels = list(matrix_labels) if matrix_labels is not None else labels
    matrix = confusion_matrix(y_true, y_pred, labels=displayed_labels)
    report = classification_report(
        y_true,
        y_pred,
        labels=labels,
        target_names=labels,
        output_dict=True,
        zero_division=0,
    )
    rows = [
        {
            "Class": label,
            "Precision": float(report[label]["precision"]),
            "Recall": float(report[label]["recall"]),
            "F1-Score": float(report[label]["f1-score"]),
            "Support": int(report[label]["support"]),
        }
        for label in labels
    ]
    summary = {
        "overall_accuracy": float(accuracy_score(y_true, y_pred)),
        "macro_precision": float(precision_score(y_true, y_pred, labels=labels, average="macro", zero_division=0)),
        "macro_recall": float(recall_score(y_true, y_pred, labels=labels, average="macro", zero_division=0)),
        "macro_f1": float(f1_score(y_true, y_pred, labels=labels, average="macro", zero_division=0)),
        "total_instances": len(y_true),
    }
    return matrix, pd.DataFrame(rows), summary


def save_confusion_matrix(
    matrix: np.ndarray,
    output_path: Path,
    labels: Iterable[str] = EXPECTED_CLASSES,
    title: str = "DragonView per-fruit confusion matrix",
) -> None:
    labels = list(labels)
    figure, axis = plt.subplots(figsize=(7, 6))
    image = axis.imshow(matrix, interpolation="nearest", cmap="Blues")
    figure.colorbar(image, ax=axis)
    axis.set(
        xticks=np.arange(len(labels)),
        yticks=np.arange(len(labels)),
        xticklabels=labels,
        yticklabels=labels,
        xlabel="Predicted label",
        ylabel="True label",
        title=title,
    )
    threshold = matrix.max() / 2.0 if matrix.size else 0
    for row in range(matrix.shape[0]):
        for column in range(matrix.shape[1]):
            axis.text(
                column,
                row,
                str(matrix[row, column]),
                ha="center",
                va="center",
                color="white" if matrix[row, column] > threshold else "black",
            )
    figure.tight_layout()
    figure.savefig(output_path, dpi=180, bbox_inches="tight")
    plt.close(figure)


def save_test_support(support: dict[str, int], output_path: Path) -> None:
    """Visualize held-out instance support, even when final evaluation is blocked."""
    labels = list(EXPECTED_CLASSES)
    counts = [support[label] for label in labels]
    colors = ["#C2185B", "#2E7D32", "#C62828" if support["Class C"] == 0 else "#F9A825"]
    figure, axis = plt.subplots(figsize=(7, 5))
    bars = axis.bar(labels, counts, color=colors, width=0.62)
    axis.set(
        ylabel="Annotated fruit instances",
        title="DragonView held-out test support",
    )
    axis.set_ylim(0, max(counts + [1]) * 1.18)
    axis.bar_label(bars, padding=4, fontsize=11)
    missing = [label for label in labels if support[label] == 0]
    if missing:
        figure.text(
            0.5,
            0.01,
            "Final three-class metrics blocked: no held-out support for " + ", ".join(missing),
            ha="center",
            color="#C62828",
            fontsize=10,
        )
    figure.tight_layout(rect=(0, 0.05, 1, 1))
    figure.savefig(output_path, dpi=180, bbox_inches="tight")
    plt.close(figure)


def save_metrics_chart(
    metrics: pd.DataFrame,
    summary: dict[str, float],
    output_path: Path,
    title: str = "DragonView per-fruit classification metrics",
    macro_label: str = "Macro",
) -> None:
    """Save per-class precision, recall, and F1 with overall summary metrics."""
    labels = metrics["Class"].tolist()
    x_positions = np.arange(len(labels))
    width = 0.24
    figure, axis = plt.subplots(figsize=(9, 6))
    for offset, column, color in (
        (-width, "Precision", "#C2185B"),
        (0.0, "Recall", "#2E7D32"),
        (width, "F1-Score", "#F9A825"),
    ):
        bars = axis.bar(x_positions + offset, metrics[column], width, label=column, color=color)
        axis.bar_label(bars, labels=[f"{value:.1%}" for value in metrics[column]], padding=3, fontsize=8)
    axis.set(
        xticks=x_positions,
        xticklabels=labels,
        ylim=(0.0, 1.12),
        ylabel="Score",
        title=title,
    )
    axis.yaxis.set_major_formatter(lambda value, _: f"{value:.0%}")
    axis.legend(loc="upper center", ncols=3)
    figure.text(
        0.5,
        0.01,
        (
            f"Accuracy {summary['overall_accuracy']:.2%}  |  "
            f"{macro_label} Precision {summary['macro_precision']:.2%}  |  "
            f"{macro_label} Recall {summary['macro_recall']:.2%}  |  "
            f"{macro_label} F1 {summary['macro_f1']:.2%}"
        ),
        ha="center",
        fontsize=10,
    )
    figure.tight_layout(rect=(0, 0.05, 1, 1))
    figure.savefig(output_path, dpi=180, bbox_inches="tight")
    plt.close(figure)


def print_results(matrix: np.ndarray, metrics: pd.DataFrame, summary: dict[str, float]) -> None:
    print("\nConfusion Matrix (rows=true, columns=predicted)")
    print(pd.DataFrame(matrix, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_string())
    display = metrics.copy()
    for column in ("Precision", "Recall", "F1-Score"):
        display[column] = display[column].map(lambda value: f"{value:.2%}")
    print("\nPer-class metrics")
    print(display.to_string(index=False))
    print(f"\nOverall Accuracy: {summary['overall_accuracy']:.2%}")
    print(f"Macro Precision: {summary['macro_precision']:.2%}")
    print(f"Macro Recall: {summary['macro_recall']:.2%}")
    print(f"Macro F1-Score: {summary['macro_f1']:.2%}")
    print(f"Total evaluated fruit instances: {int(summary['total_instances'])}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--annotations", type=Path, action="append", default=[])
    parser.add_argument("--annotation-dir", type=Path, default=DEFAULT_ANNOTATION_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--crop-padding", type=float, default=0.06)
    parser.add_argument(
        "--audit-only",
        action="store_true",
        help="Validate split isolation and report test support without running inference.",
    )
    parser.add_argument(
        "--preliminary-ab",
        action="store_true",
        help=(
            "Evaluate genuine held-out Class A/B instances while Class C has no test support. "
            "Outputs are explicitly marked preliminary and must not be reported as three-class metrics."
        ),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not 0.0 <= args.crop_padding <= 0.5:
        raise EvaluationError("--crop-padding must be between 0.0 and 0.5")

    splits = load_manifest(args.manifest)
    verify_split_isolation(splits)
    files = annotation_files(args.annotations, args.annotation_dir)
    records, support = collect_test_annotations(files, splits["test"])
    labels = read_labels(args.labels)

    print(f"Held-out test images: {len(splits['test'])}")
    print(f"Matched annotated fruit instances: {len(records)}")
    print("Test support: " + ", ".join(f"{label}={support[label]}" for label in EXPECTED_CLASSES))
    print(f"Model labels: {labels}")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(
        [{"Class": label, "Support": support[label]} for label in EXPECTED_CLASSES]
    ).to_csv(args.output_dir / "test_support.csv", index=False)
    save_test_support(support, args.output_dir / "test_support.png")
    try:
        preflight(labels, support, records)
    except EvaluationError:
        if not args.preliminary_ab:
            raise
        if labels != list(EXPECTED_CLASSES):
            raise
        if support["Class A"] == 0 or support["Class B"] == 0:
            raise EvaluationError("Preliminary A/B evaluation requires held-out support for both classes")
        if support["Class C"] != 0:
            raise EvaluationError("Use the final three-class evaluation when Class C test support exists")
        missing_images = sorted(
            {str(record.image_path) for record in records if not record.image_path.is_file()}
        )
        if missing_images:
            raise EvaluationError(f"{len(missing_images)} held-out source image(s) are missing")
        print("PRELIMINARY MODE: Class A/B metrics only; Class C is not evaluated.")
    if args.audit_only:
        print("Audit passed: the data and label contract are ready for three-class evaluation.")
        return 0

    interpreter = create_interpreter(args.model)
    input_info, output_info = validate_model_contract(interpreter, labels)
    prediction_rows: list[dict[str, Any]] = []
    for index, record in enumerate(records, start=1):
        probabilities = predict(
            interpreter,
            input_info,
            output_info,
            preprocess(padded_crop(record, args.crop_padding)),
        )
        predicted_index = int(np.argmax(probabilities))
        prediction_rows.append(
            {
                "instance_id": f"{record.image_name}::{record.annotation_id}",
                "image_name": record.image_name,
                "source_image": str(record.image_path),
                "capture_group": record.capture_group,
                "annotation_id": record.annotation_id,
                "true_label": record.true_label,
                "predicted_label": labels[predicted_index],
                "confidence": float(probabilities[predicted_index]),
                **{f"probability_{label.lower().replace(' ', '_')}": float(probabilities[i]) for i, label in enumerate(labels)},
            }
        )
        if index % 50 == 0 or index == len(records):
            print(f"Evaluated {index}/{len(records)} fruit instances")

    predictions = pd.DataFrame(prediction_rows)
    metric_labels = list(EXPECTED_CLASSES[:2]) if args.preliminary_ab else list(EXPECTED_CLASSES)
    matrix_labels = list(EXPECTED_CLASSES) if args.preliminary_ab else metric_labels
    matrix, metrics, summary = compute_metrics(
        predictions["true_label"].tolist(),
        predictions["predicted_label"].tolist(),
        metric_labels=metric_labels,
        matrix_labels=matrix_labels,
    )
    if int(metrics["Support"].sum()) != len(predictions):
        raise EvaluationError("Metric support does not equal the number of evaluated fruit instances")

    report_dir = args.output_dir / "preliminary_ab" if args.preliminary_ab else args.output_dir
    report_dir.mkdir(parents=True, exist_ok=True)
    predictions.to_csv(report_dir / "instance_predictions.csv", index=False)
    metrics.to_csv(report_dir / "per_class_metrics.csv", index=False)
    pd.DataFrame(matrix, index=matrix_labels, columns=matrix_labels).to_csv(
        report_dir / "confusion_matrix.csv", index_label="True / Predicted"
    )
    title_suffix = " — PRELIMINARY A/B ONLY" if args.preliminary_ab else ""
    save_confusion_matrix(
        matrix,
        report_dir / "confusion_matrix.png",
        labels=matrix_labels,
        title="DragonView per-fruit confusion matrix" + title_suffix,
    )
    save_metrics_chart(
        metrics,
        summary,
        report_dir / "metrics_overview.png",
        title="DragonView per-fruit classification metrics" + title_suffix,
        macro_label="A/B Macro" if args.preliminary_ab else "Macro",
    )
    result = {
        **summary,
        "evaluation_status": "preliminary_ab_only" if args.preliminary_ab else "final_three_class",
        "classes": metric_labels,
        "confusion_matrix_labels": matrix_labels,
        "support": {row["Class"]: int(row["Support"]) for row in metrics.to_dict("records")},
        "confusion_matrix": matrix.tolist(),
        "model": str(args.model.resolve()),
        "test_manifest": str(args.manifest.resolve()),
        "annotation_exports": [str(path.resolve()) for path in files],
        "crop_padding": args.crop_padding,
    }
    if args.preliminary_ab:
        result["limitation"] = (
            "Class C has zero true held-out instances and is excluded from per-class and macro metrics. "
            "Predicted Class C outcomes remain visible in the confusion matrix as A/B errors."
        )
    (report_dir / "evaluation_summary.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )
    print_results(matrix, metrics, summary)
    print(f"\nEvaluation artifacts: {report_dir.resolve()}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except EvaluationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(2)
