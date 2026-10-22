import sys
from pathlib import Path

import numpy as np


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from evaluate_preliminary_combined import save_percentage_confusion_matrix


def test_percentage_matrix_normalizes_each_true_class_and_handles_empty_support(tmp_path):
    counts = np.array([[2, 1, 1], [0, 0, 0], [1, 0, 3]])

    percentages = save_percentage_confusion_matrix(counts, tmp_path / "percent.png")

    np.testing.assert_allclose(percentages[0], [50, 25, 25])
    np.testing.assert_allclose(percentages[1], [0, 0, 0])
    np.testing.assert_allclose(percentages[2], [25, 0, 75])
    assert (tmp_path / "percent.png").is_file()
