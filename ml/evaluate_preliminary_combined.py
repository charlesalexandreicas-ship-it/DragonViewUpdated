"""Report the app's exploratory offline A/B baseline plus Class C gate.

This combination was selected after examining the prototype test set, so its
figures demonstrate behavior only and are not unbiased final research metrics.
"""

from __future__ import annotations

import csv
import json
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from evaluate_classifier import EXPECTED_CLASSES, compute_metrics, save_confusion_matrix, save_metrics_chart


ROOT = Path(__file__).resolve().parent / "outputs" / "preliminary_three_class" / "evaluation"
CLASS_C_THRESHOLD = 0.30  # Keep equal to MobileNetV2Classifier.CLASS_C_PROTOTYPE_THRESHOLD.


def save_percentage_confusion_matrix(matrix: np.ndarray, output: Path) -> np.ndarray:
    """Show prediction percentages within each true class; empty rows stay at zero."""
    row_totals = matrix.sum(axis=1, keepdims=True)
    percentages = np.divide(
        matrix, row_totals, out=np.zeros_like(matrix, dtype=float), where=row_totals != 0
    ) * 100.0
    labels = list(EXPECTED_CLASSES)
    figure, axis = plt.subplots(figsize=(7, 6))
    image = axis.imshow(percentages, interpolation="nearest", cmap="Blues", vmin=0, vmax=100)
    figure.colorbar(image, ax=axis, label="Percent of true-class images")
    axis.set(
        xticks=np.arange(len(labels)), yticks=np.arange(len(labels)),
        xticklabels=labels, yticklabels=labels,
        xlabel="Predicted label", ylabel="True label",
        title="EXPLORATORY A/B/C app-preview confusion matrix (%)",
    )
    for row in range(matrix.shape[0]):
        for column in range(matrix.shape[1]):
            axis.text(column, row, f"{percentages[row, column]:.1f}%",
                      ha="center", va="center",
                      color="white" if percentages[row, column] > 50 else "black")
    figure.tight_layout()
    figure.savefig(output, dpi=180, bbox_inches="tight")
    plt.close(figure)
    return percentages


def main() -> int:
    baseline = pd.read_csv(ROOT / "baseline" / "predictions.csv").set_index("sha256")
    candidate = pd.read_csv(ROOT / "candidate" / "predictions.csv").set_index("sha256")
    validation = pd.read_csv(ROOT / "candidate_validation" / "predictions.csv")
    if set(baseline.index) != set(candidate.index):
        raise ValueError("Baseline and candidate test predictions are not the same images")
    joined = baseline.join(candidate, lsuffix="_baseline", rsuffix="_candidate", validate="one_to_one")
    if any(joined.true_label_baseline != joined.true_label_candidate):
        raise ValueError("The models were evaluated against different true labels")
    non_c = validation[validation.true_label != "Class C"]
    c = validation[validation.true_label == "Class C"]
    if non_c.empty or c.empty or non_c.probability_class_c.max() >= CLASS_C_THRESHOLD:
        raise ValueError("Class C gate is not clean on the validation set")

    output = ROOT / "combined_app_preview"
    output.mkdir(parents=True, exist_ok=True)
    rows = []
    for digest, record in joined.iterrows():
        is_c = record.probability_class_c_candidate >= CLASS_C_THRESHOLD
        if is_c:
            predicted = "Class C"
        else:
            predicted = ("Class A" if record.probability_class_a_baseline
                         >= record.probability_class_b_baseline else "Class B")
        rows.append({
            "sha256": digest, "image": record.image_baseline,
            "capture_group": record.capture_group_baseline,
            "true_label": record.true_label_baseline,
            "predicted_label": predicted,
            "class_c_gate_probability": float(record.probability_class_c_candidate),
            "baseline_class_a_probability": float(record.probability_class_a_baseline),
            "baseline_class_b_probability": float(record.probability_class_b_baseline),
        })
    with (output / "predictions.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    matrix, metrics, summary = compute_metrics(
        [row["true_label"] for row in rows], [row["predicted_label"] for row in rows]
    )
    pd.DataFrame(matrix, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_csv(
        output / "confusion_matrix.csv", index_label="true_label"
    )
    metrics.to_csv(output / "per_class_metrics.csv", index=False)
    save_confusion_matrix(matrix, output / "confusion_matrix.png",
                          title="EXPLORATORY A/B/C app-preview confusion matrix")
    percentages = save_percentage_confusion_matrix(matrix, output / "confusion_matrix_percent.png")
    pd.DataFrame(percentages, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_csv(
        output / "confusion_matrix_percent.csv", index_label="true_label"
    )
    save_metrics_chart(metrics, summary, output / "metrics.png",
                       title="EXPLORATORY A/B/C app-preview metrics")
    report = {
        "status": "exploratory_test_informed_not_unbiased_final_evaluation",
        "rule": "candidate Class C probability >= 0.30, otherwise baseline A/B argmax",
        "validation_non_c_max_c_probability": float(non_c.probability_class_c.max()),
        "validation_c_recall_at_gate": float((c.probability_class_c >= CLASS_C_THRESHOLD).mean()),
        "summary": summary,
        "matrix": matrix.tolist(),
        "limitations": [
            "The combination was chosen after inspecting this preliminary test set; these numbers are not an untouched final test result.",
            "Class C test images are related views from one capture group, not independent fruit specimens.",
            "The Class C source differs from A/B sources; background and capture conditions may confound the result.",
            "This is image-level model evaluation, not end-to-end multi-fruit detector-plus-classifier testing.",
        ],
    }
    (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(pd.DataFrame(matrix, index=EXPECTED_CLASSES, columns=EXPECTED_CLASSES).to_string())
    print(metrics.to_string(index=False))
    print(json.dumps(summary, indent=2))
    print(f"Exploratory figures: {output.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
