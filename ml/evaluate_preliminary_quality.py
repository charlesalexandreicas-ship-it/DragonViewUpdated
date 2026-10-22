"""Evaluate the isolated prototype classifier on its held-out image groups.

These are single-fruit source images, not individually annotated detector crops.
The report is a preliminary pipeline check, not final per-fruit scanner evidence.
"""

from __future__ import annotations

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

from PIL import Image, ImageOps

from evaluate_classifier import (
    EXPECTED_CLASSES, compute_metrics, create_interpreter, predict, preprocess,
    save_confusion_matrix, save_metrics_chart, validate_model_contract,
)


ML_DIR = Path(__file__).resolve().parent
ROOT = ML_DIR / "outputs" / "preliminary_three_class"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=ROOT / "candidate_asset" / "mobilenet_v2.tflite")
    parser.add_argument("--output", type=Path, default=ROOT / "evaluation" / "candidate")
    parser.add_argument("--split-manifest", type=Path, default=ROOT / "training" / "classifier_split_manifest.json")
    parser.add_argument("--split", choices=("validation", "test"), default="test")
    args = parser.parse_args()

    splits = json.loads(args.split_manifest.read_text(encoding="utf-8"))
    groups = [set(record["capture_group"] for record in splits[name]) for name in ("train", "validation", "test")]
    hashes = [set(record["sha256"] for record in splits[name]) for name in ("train", "validation", "test")]
    for first in range(3):
        for second in range(first + 1, 3):
            if groups[first] & groups[second] or hashes[first] & hashes[second]:
                raise ValueError("Capture group or exact-image leakage across splits")
    test = splits[args.split]
    label_lookup = {"class_a": "Class A", "class_b": "Class B", "class_c": "Class C"}
    support = Counter(record["class"] for record in test)
    if not test or any(support[name] == 0 for name in label_lookup):
        raise ValueError(f"Selected split lacks support for all three classes: {dict(support)}")

    interpreter = create_interpreter(args.model)
    input_info, output_info = validate_model_contract(interpreter, list(EXPECTED_CLASSES))
    output_rows = []
    true_labels, predicted_labels = [], []
    for record in test:
        path = Path(record["path"])
        if not path.is_file():
            raise ValueError(f"Test image missing: {path}")
        with Image.open(path) as opened:
            image = ImageOps.exif_transpose(opened).convert("RGB")
        probabilities = predict(interpreter, input_info, output_info, preprocess(image))
        actual = label_lookup[record["class"]]
        predicted = EXPECTED_CLASSES[int(probabilities.argmax())]
        true_labels.append(actual)
        predicted_labels.append(predicted)
        output_rows.append({
            "image": str(path), "capture_group": record["capture_group"],
            "sha256": record["sha256"], "true_label": actual,
            "predicted_label": predicted, "confidence": float(probabilities.max()),
            **{f"probability_{label.replace(' ', '_').lower()}": float(value)
               for label, value in zip(EXPECTED_CLASSES, probabilities)},
        })

    matrix, metrics, summary = compute_metrics(true_labels, predicted_labels)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "predictions.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(output_rows[0]))
        writer.writeheader()
        writer.writerows(output_rows)
    metrics.to_csv(args.output / "per_class_metrics.csv", index=False)
    import pandas as pd
    pd.DataFrame(matrix, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_csv(
        args.output / "confusion_matrix.csv", index_label="true_label"
    )
    save_confusion_matrix(
        matrix, args.output / "confusion_matrix.png", title="PRELIMINARY A/B/C image-level confusion matrix"
    )
    save_metrics_chart(
        metrics, summary, args.output / "metrics.png",
        title="PRELIMINARY A/B/C image-level classification metrics",
    )
    report = {
        "evaluation_status": "preliminary_image_level_not_final_independent_fruit_evaluation",
        "model": str(args.model.resolve()), "split": args.split, "summary": summary,
        "test_support": {label_lookup[name]: support[name] for name in label_lookup},
        "test_capture_groups": {name: len(groups[index]) for index, name in enumerate(("train", "validation", "test"))},
        "confusion_matrix": matrix.tolist(),
        "limitations": [
            "A/B labels include provisional visual review; C means visible defect/overripe proxy, not confirmed juice suitability.",
            "A defect capture group may contain many near-identical views of one fruit; image counts are not independent-fruit counts.",
            "Class C comes from a different source collection, so model success may reflect capture/background style rather than fruit grade.",
            "Evaluation is of single-fruit image classification, not the complete detector-plus-classifier scanner.",
        ],
    }
    (args.output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(pd.DataFrame(matrix, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_string())
    print(metrics.to_string(index=False))
    print(json.dumps(summary, indent=2))
    print(f"Figures and CSV: {args.output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
