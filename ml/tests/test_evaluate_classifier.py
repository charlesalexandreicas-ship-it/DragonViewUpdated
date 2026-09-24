import importlib.util
import sys
from pathlib import Path

import numpy as np


MODULE_PATH = Path(__file__).resolve().parents[1] / "evaluate_classifier.py"
SPEC = importlib.util.spec_from_file_location("evaluate_classifier", MODULE_PATH)
evaluation = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = evaluation
SPEC.loader.exec_module(evaluation)


def test_multiclass_metrics_are_instance_based() -> None:
    y_true = ["Class A", "Class A", "Class B", "Class B", "Class C", "Class C"]
    y_pred = ["Class A", "Class B", "Class B", "Class C", "Class C", "Class C"]

    matrix, table, summary = evaluation.compute_metrics(y_true, y_pred)

    np.testing.assert_array_equal(matrix, [[1, 1, 0], [0, 1, 1], [0, 0, 2]])
    assert summary["overall_accuracy"] == 4 / 6
    assert summary["total_instances"] == 6
    assert table["Support"].tolist() == [2, 2, 2]
    assert np.isclose(summary["macro_precision"], (1.0 + 0.5 + (2 / 3)) / 3)
    assert np.isclose(summary["macro_recall"], (0.5 + 0.5 + 1.0) / 3)
    assert np.isclose(summary["macro_f1"], ((2 / 3) + 0.5 + 0.8) / 3)


def test_split_leakage_is_rejected() -> None:
    splits = {
        "train": [{"capture_group": "burst-1", "source_image": "a.jpg"}],
        "val": [{"capture_group": "burst-2", "source_image": "b.jpg"}],
        "test": [{"capture_group": "burst-1", "source_image": "c.jpg"}],
    }

    try:
        evaluation.verify_split_isolation(splits)
    except evaluation.EvaluationError as error:
        assert "leakage" in str(error).lower()
    else:
        raise AssertionError("Expected capture-group leakage to be rejected")
