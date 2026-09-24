"""Validate the exported LiteRT detector with Android-equivalent preprocessing."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter


ROOT = Path(__file__).resolve().parent.parent
MODEL = ROOT / "app" / "src" / "main" / "assets" / "ml" / "dragon_fruit_detector.tflite"
CONFIG = ROOT / "app" / "src" / "main" / "assets" / "ml" / "detector_config.json"
MANIFEST = ROOT / "ml" / "datasets" / "dragon_fruit_detection" / "manifest.json"
REPORT = ROOT / "ml" / "outputs" / "detector_tflite_report.json"


def iou(first, second):
    x1, y1 = max(first[0], second[0]), max(first[1], second[1])
    x2, y2 = min(first[2], second[2]), min(first[3], second[3])
    intersection = max(0, x2 - x1) * max(0, y2 - y1)
    a = max(0, first[2] - first[0]) * max(0, first[3] - first[1])
    b = max(0, second[2] - second[0]) * max(0, second[3] - second[1])
    return intersection / max(a + b - intersection, 1e-9)


def letterbox(image, size):
    width, height = image.size
    scale = min(size / width, size / height)
    resized_width, resized_height = round(width * scale), round(height * scale)
    pad_x, pad_y = (size - resized_width) / 2, (size - resized_height) / 2
    canvas = Image.new("RGB", (size, size), (114, 114, 114))
    canvas.paste(image.resize((resized_width, resized_height)), (round(pad_x), round(pad_y)))
    return np.asarray(canvas, dtype=np.float32)[None] / 255.0, scale, pad_x, pad_y


def infer(interpreter, image, size, threshold, nms_threshold):
    tensor, scale, pad_x, pad_y = letterbox(image, size)
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], tensor)
    interpreter.invoke()
    output = interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0]
    candidates = []
    for x1, y1, x2, y2, confidence, _ in output:
        if confidence < threshold:
            continue
        candidates.append((
            max(0, (x1 - pad_x) / scale / image.width),
            max(0, (y1 - pad_y) / scale / image.height),
            min(1, (x2 - pad_x) / scale / image.width),
            min(1, (y2 - pad_y) / scale / image.height),
            float(confidence),
        ))
    candidates.sort(key=lambda item: item[4], reverse=True)
    boxes = []
    for candidate in candidates:
        if all(iou(candidate, kept) < nms_threshold for kept in boxes):
            boxes.append(candidate)
    return boxes[:12]


def evaluate(interpreter, records, size, threshold, nms_threshold):
    tp = fp = fn = exact = error = 0
    for record in records:
        image_path = Path(record["source_image"])
        prepared = ROOT / record["prepared_image"]
        label_path = prepared.parent.parent.parent / "labels" / prepared.parent.name / f"{prepared.stem}.txt"
        truths = []
        for line in label_path.read_text(encoding="utf-8").splitlines():
            _, cx, cy, width, height = map(float, line.split())
            truths.append((cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2))
        with Image.open(image_path) as opened:
            predictions = infer(interpreter, opened.convert("RGB"), size, threshold, nms_threshold)
        matched = set()
        for prediction in predictions:
            choices = [(iou(prediction, truth), index) for index, truth in enumerate(truths)
                       if index not in matched]
            score, index = max(choices, default=(0, -1))
            if score >= 0.5:
                tp += 1
                matched.add(index)
            else:
                fp += 1
        fn += len(truths) - len(matched)
        exact += len(predictions) == len(truths)
        error += abs(len(predictions) - len(truths))
    precision = tp / max(tp + fp, 1)
    recall = tp / max(tp + fn, 1)
    return {
        "images": len(records), "true_positive": tp, "false_positive": fp,
        "false_negative": fn, "precision_iou50": round(precision, 4),
        "recall_iou50": round(recall, 4),
        "f1_iou50": round(2 * precision * recall / max(precision + recall, 1e-9), 4),
        "exact_count_accuracy": round(exact / len(records), 4),
        "mean_absolute_count_error": round(error / len(records), 4),
    }


def main():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    interpreter = Interpreter(model_path=str(MODEL))
    interpreter.allocate_tensors()
    input_shape = interpreter.get_input_details()[0]["shape"].tolist()
    output_shape = interpreter.get_output_details()[0]["shape"].tolist()
    if input_shape != [1, 320, 320, 3] or output_shape != [1, 300, 6]:
        raise ValueError(f"Unexpected tensors: {input_shape} -> {output_shape}")
    candidates = []
    for value in range(5, 41, 5):
        metrics = evaluate(interpreter, manifest["splits"]["val"],
                           config["input_size"], value / 100,
                           config["nms_iou_threshold"])
        metrics["confidence_threshold"] = value / 100
        candidates.append(metrics)
    best = max(candidates, key=lambda item: (
        item["f1_iou50"], item["exact_count_accuracy"],
        -item["mean_absolute_count_error"], item["confidence_threshold"],
    ))
    config["confidence_threshold"] = best["confidence_threshold"]
    CONFIG.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    rng = np.random.default_rng(20260913)
    synthetic = {
        "black": Image.new("RGB", (640, 480), (0, 0, 0)),
        "white": Image.new("RGB", (640, 480), (255, 255, 255)),
        "gray": Image.new("RGB", (640, 480), (127, 127, 127)),
        "noise": Image.fromarray(rng.integers(0, 256, (480, 640, 3), dtype=np.uint8)),
    }
    synthetic_counts = {
        name: len(infer(interpreter, image, config["input_size"],
                        best["confidence_threshold"], config["nms_iou_threshold"]))
        for name, image in synthetic.items()
    }
    result = {
        "model_bytes": MODEL.stat().st_size,
        "input_shape": input_shape,
        "output_shape": output_shape,
        "confidence_threshold": best["confidence_threshold"],
        "validation": best,
        "test": evaluate(interpreter, manifest["splits"]["test"],
                         config["input_size"], best["confidence_threshold"],
                         config["nms_iou_threshold"]),
        "synthetic_negative_detection_counts": synthetic_counts,
        "synthetic_negative_batches_reaching_grading": sum(
            1 <= count <= config["max_scanner_fruit_count"]
            for count in synthetic_counts.values()
        ),
    }
    REPORT.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
