"""Train and export DragonView's three-class on-device quality classifier.

Folder names provide the ground-truth labels. Images are read recursively from:

    ml/datasets/dragon_fruit_quality/raw/class_a/
    ml/datasets/dragon_fruit_quality/raw/class_b/
    ml/datasets/dragon_fruit_quality/raw/class_c/

The exported model matches MobileNetV2Classifier.java exactly: FLOAT32 RGB input
[1, 224, 224, 3], values in [-1, 1], and a three-value softmax output.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
CLASS_NAMES = ("class_a", "class_b", "class_c")
DISPLAY_LABELS = ("Class A", "Class B", "Class C")
IMAGE_SIZE = 224
SEED = 20260912
TRAINING_VIEWS_PER_CLASS = 1

ML_DIR = Path(__file__).resolve().parent
PROJECT_DIR = ML_DIR.parent
RAW_DATASET_DIR = ML_DIR / "datasets" / "dragon_fruit_quality" / "raw"
OUTPUT_DIR = ML_DIR / "outputs"
ASSET_DIR = PROJECT_DIR / "app" / "src" / "main" / "assets" / "ml"

BURST_PATTERN = re.compile(r"^(IMG_\d{8}_\d+)_BURST", re.IGNORECASE)
MINUTE_PATTERN = re.compile(r"^IMG_(\d{8})_(\d{4})", re.IGNORECASE)


@dataclass(frozen=True)
class Example:
    path: Path
    label: int
    class_name: str
    capture_group: str
    sha256: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--audit-only",
        action="store_true",
        help="Validate and summarize the dataset without importing TensorFlow.",
    )
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--fine-tune-epochs", type=int, default=10)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--dataset-dir", type=Path, default=RAW_DATASET_DIR)
    parser.add_argument("--output-dir", type=Path, default=OUTPUT_DIR)
    parser.add_argument("--asset-dir", type=Path, default=ASSET_DIR)
    parser.add_argument(
        "--allow-unsafe-dataset",
        action="store_true",
        help="Override diversity/imbalance guards. Intended only for pipeline testing.",
    )
    return parser.parse_args()


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def capture_group(path: Path, class_name: str) -> str:
    """Conservatively group near-neighbor captures to prevent split leakage."""
    relative_parent = path.parent.relative_to(RAW_DATASET_DIR / class_name)
    batch = relative_parent.parts[0] if relative_parent.parts else "unbatched"
    if batch.endswith("_training_only"):
        return f"{class_name}/{batch}/single_source_session"
    burst = BURST_PATTERN.match(path.stem)
    if burst:
        session = burst.group(1).lower()
    else:
        minute = MINUTE_PATTERN.match(path.stem)
        session = "_".join(minute.groups()).lower() if minute else path.stem.lower()
    return f"{class_name}/{batch}/{session}"


def load_examples() -> tuple[list[Example], int]:
    examples: list[Example] = []
    hashes_by_class: dict[str, set[str]] = {name: set() for name in CLASS_NAMES}
    duplicates = 0

    for label, class_name in enumerate(CLASS_NAMES):
        class_dir = RAW_DATASET_DIR / class_name
        if not class_dir.is_dir():
            raise ValueError(f"Missing class directory: {class_dir}")
        paths = sorted(
            path
            for path in class_dir.rglob("*")
            if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
        )
        if not paths:
            raise ValueError(f"No supported images found in: {class_dir}")

        for path in paths:
            digest = file_hash(path)
            other_classes = set(CLASS_NAMES) - {class_name}
            if any(digest in hashes_by_class[other] for other in other_classes):
                raise ValueError(
                    f"Conflicting ground truth: the same image occurs in multiple classes: {path}"
                )
            if digest in hashes_by_class[class_name]:
                duplicates += 1
                continue
            hashes_by_class[class_name].add(digest)
            examples.append(
                Example(path, label, class_name, capture_group(path, class_name), digest)
            )

    return examples, duplicates


def audit(examples: list[Example], duplicates: int, allow_unsafe: bool) -> None:
    image_counts = Counter(example.class_name for example in examples)
    group_counts = {
        class_name: len(
            {example.capture_group for example in examples if example.class_name == class_name}
        )
        for class_name in CLASS_NAMES
    }

    print(f"Dataset: {RAW_DATASET_DIR}")
    for class_name in CLASS_NAMES:
        print(
            f"  {class_name}: {image_counts[class_name]} unique images, "
            f"{group_counts[class_name]} capture groups"
        )
    print(
        f"  per-class training views: {TRAINING_VIEWS_PER_CLASS} per source image"
    )
    print(f"  ignored exact duplicates: {duplicates}")

    problems: list[str] = []
    for class_name in CLASS_NAMES:
        if group_counts[class_name] < 3:
            problems.append(
                f"{class_name} needs at least 3 independent capture groups "
                "(one each for train, validation, and test)"
            )
    smallest = min(image_counts.values())
    largest = max(image_counts.values())
    if smallest == 0 or largest / smallest > 5:
        problems.append(
            f"class imbalance is {largest}:{smallest}; collect more genuine minority-class images"
        )

    if problems:
        message = "Dataset safety check failed:\n- " + "\n- ".join(problems)
        if not allow_unsafe:
            raise ValueError(message + "\nUse --allow-unsafe-dataset only to test the code path.")
        print("WARNING: " + message, file=sys.stderr)


def split_by_capture_group(
    examples: list[Example],
) -> tuple[list[Example], list[Example], list[Example]]:
    rng = random.Random(SEED)
    split_records: dict[str, list[Example]] = {"train": [], "validation": [], "test": []}

    for class_name in CLASS_NAMES:
        domains: dict[str, dict[str, list[Example]]] = defaultdict(lambda: defaultdict(list))
        for example in examples:
            if example.class_name == class_name:
                date = re.search(r"(20\d{6})", example.capture_group)
                domain = f"{class_name}/{example.path.parent.name}/{date.group(1) if date else 'unknown'}"
                domains[domain][example.capture_group].append(example)

        # Split inside each date/batch domain. This prevents a capture condition from
        # appearing only in validation, which previously placed every August Class A
        # image outside training and made the model predict them all as Class B.
        for grouped in domains.values():
            group_names = sorted(grouped)
            rng.shuffle(group_names)
            if len(group_names) == 1:
                assignments = {"train": group_names, "validation": [], "test": []}
            elif len(group_names) == 2:
                # Keep the larger group in training and the independent smaller group
                # for validation. Never split the images inside either capture group.
                group_names.sort(key=lambda name: len(grouped[name]), reverse=True)
                assignments = {
                    "train": group_names[:1],
                    "validation": group_names[1:],
                    "test": [],
                }
            else:
                validation_groups = max(1, round(len(group_names) * 0.15))
                test_groups = max(1, round(len(group_names) * 0.15))
                if validation_groups + test_groups >= len(group_names):
                    validation_groups = test_groups = 1
                assignments = {
                    "validation": group_names[:validation_groups],
                    "test": group_names[validation_groups : validation_groups + test_groups],
                    "train": group_names[validation_groups + test_groups :],
                }

            for split_name, names in assignments.items():
                for name in names:
                    split_records[split_name].extend(grouped[name])

    for records in split_records.values():
        rng.shuffle(records)
    return (
        split_records["train"],
        split_records["validation"],
        split_records["test"],
    )


def train_and_export(args: argparse.Namespace, examples: list[Example]) -> None:
    try:
        import numpy as np
        import tensorflow as tf
    except ImportError as error:
        raise SystemExit(
            "Training dependencies are missing. Run: "
            "python -m pip install -r ml/requirements.txt"
        ) from error

    tf.keras.utils.set_random_seed(SEED)
    train_examples, validation_examples, test_examples = split_by_capture_group(examples)

    def report_split(name: str, records: list[Example]) -> None:
        counts = Counter(record.class_name for record in records)
        print(f"{name}: {len(records)} images {dict(counts)}")

    report_split("train", train_examples)
    report_split("validation", validation_examples)
    report_split("test", test_examples)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    split_manifest = {
        name: [
            {"path": str(record.path.resolve()), "class": record.class_name,
             "capture_group": record.capture_group, "sha256": record.sha256}
            for record in records
        ]
        for name, records in (("train", train_examples), ("validation", validation_examples),
                              ("test", test_examples))
    }
    (OUTPUT_DIR / "classifier_split_manifest.json").write_text(
        json.dumps(split_manifest, indent=2), encoding="utf-8"
    )

    def decode(path: "tf.Tensor", label: "tf.Tensor"):
        image = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
        image.set_shape([None, None, 3])
        shape = tf.shape(image)
        side = tf.minimum(shape[0], shape[1])
        top = (shape[0] - side) // 2
        left = (shape[1] - side) // 2
        image = tf.image.crop_to_bounding_box(image, top, left, side, side)
        image = tf.image.resize(image, [IMAGE_SIZE, IMAGE_SIZE], antialias=True)
        image = tf.cast(image, tf.float32) / 127.5 - 1.0
        return image, tf.one_hot(label, len(CLASS_NAMES))

    def dataset(records: list[Example], training: bool, balanced: bool = False):
        if training and balanced:
            class_datasets = []
            for label in range(len(CLASS_NAMES)):
                class_records = [record for record in records if record.label == label]
                paths = [str(record.path) for record in class_records]
                labels = [record.label for record in class_records]
                source = tf.data.Dataset.from_tensor_slices((paths, labels))
                training_views = TRAINING_VIEWS_PER_CLASS
                source = source.repeat(training_views)
                source = source.shuffle(
                    len(class_records) * training_views,
                    seed=SEED + label,
                    reshuffle_each_iteration=True,
                ).repeat().map(decode, num_parallel_calls=tf.data.AUTOTUNE)
                class_datasets.append(source)
            result = tf.data.Dataset.sample_from_datasets(
                class_datasets,
                weights=[1.0 / len(CLASS_NAMES)] * len(CLASS_NAMES),
                seed=SEED,
            )
            return result.batch(args.batch_size).prefetch(tf.data.AUTOTUNE)

        paths = [str(record.path) for record in records]
        labels = [record.label for record in records]
        result = tf.data.Dataset.from_tensor_slices((paths, labels))
        if training:
            result = result.shuffle(len(records), seed=SEED, reshuffle_each_iteration=True)
        return (
            result.map(decode, num_parallel_calls=tf.data.AUTOTUNE)
            .batch(args.batch_size)
            .prefetch(tf.data.AUTOTUNE)
        )

    train_data = dataset(train_examples, True, balanced=True)
    print(f"Training views per source image: {TRAINING_VIEWS_PER_CLASS}")
    validation_data = dataset(validation_examples, False)
    test_data = dataset(test_examples, False)
    steps_per_epoch = max(1, math.ceil(len(train_examples) / args.batch_size))

    augmentation = tf.keras.Sequential(
        [
            tf.keras.layers.RandomFlip("horizontal"),
            tf.keras.layers.RandomRotation(0.06, fill_mode="reflect"),
            tf.keras.layers.RandomZoom(0.12, fill_mode="reflect"),
            tf.keras.layers.RandomContrast(0.15),
        ],
        name="training_augmentation",
    )
    inputs = tf.keras.Input(shape=(IMAGE_SIZE, IMAGE_SIZE, 3), dtype=tf.float32, name="image")
    base = tf.keras.applications.MobileNetV2(
        input_shape=(IMAGE_SIZE, IMAGE_SIZE, 3), include_top=False, weights="imagenet"
    )
    base.trainable = False
    features = base(augmentation(inputs), training=False)
    features = tf.keras.layers.GlobalAveragePooling2D()(features)
    features = tf.keras.layers.Dropout(0.25)(features)
    outputs = tf.keras.layers.Dense(
        len(CLASS_NAMES), activation="softmax", name="quality_probabilities"
    )(features)
    model = tf.keras.Model(inputs, outputs, name="dragonview_mobilenet_v2")

    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=4, restore_best_weights=True
        )
    ]
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.fit(
        train_data,
        validation_data=validation_data,
        epochs=args.epochs,
        steps_per_epoch=steps_per_epoch,
        callbacks=callbacks,
    )

    if args.fine_tune_epochs > 0:
        base.trainable = True
        for layer in base.layers[:-30]:
            layer.trainable = False
        model.compile(
            optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
            loss="categorical_crossentropy",
            metrics=["accuracy"],
        )
        model.fit(
            train_data,
            validation_data=validation_data,
            epochs=args.fine_tune_epochs,
            steps_per_epoch=steps_per_epoch,
            callbacks=callbacks,
        )

    test_loss, test_accuracy = model.evaluate(test_data, verbose=0)
    print(f"test_loss={test_loss:.4f} test_accuracy={test_accuracy:.4f}")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    model.save(OUTPUT_DIR / "dragonview_mobilenet_v2.keras")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    model_path = ASSET_DIR / "mobilenet_v2.tflite"
    model_path.write_bytes(tflite_model)
    (ASSET_DIR / "labels.txt").write_text(
        "\n".join(DISPLAY_LABELS) + "\n", encoding="utf-8"
    )

    interpreter = tf.lite.Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_detail = interpreter.get_input_details()[0]
    output_detail = interpreter.get_output_details()[0]
    expected_input = [1, IMAGE_SIZE, IMAGE_SIZE, 3]
    expected_output = [1, len(CLASS_NAMES)]
    if input_detail["shape"].tolist() != expected_input or input_detail["dtype"] != np.float32:
        raise ValueError(f"Exported model has invalid input contract: {input_detail}")
    if output_detail["shape"].tolist() != expected_output or output_detail["dtype"] != np.float32:
        raise ValueError(f"Exported model has invalid output contract: {output_detail}")

    interpreter.set_tensor(
        input_detail["index"], np.zeros(expected_input, dtype=np.float32)
    )
    interpreter.invoke()
    probabilities = interpreter.get_tensor(output_detail["index"])[0]
    if not np.isclose(probabilities.sum(), 1.0, atol=0.02):
        raise ValueError("Exported output is not a softmax probability distribution")
    print(f"Exported model: {model_path}")
    print(f"Exported labels: {ASSET_DIR / 'labels.txt'}")


def main() -> int:
    args = parse_args()
    global RAW_DATASET_DIR, OUTPUT_DIR, ASSET_DIR
    RAW_DATASET_DIR = args.dataset_dir.resolve()
    OUTPUT_DIR = args.output_dir.resolve()
    ASSET_DIR = args.asset_dir.resolve()
    try:
        examples, duplicates = load_examples()
        audit(examples, duplicates, args.allow_unsafe_dataset)
        if args.audit_only:
            print("Audit completed; no model was trained or exported.")
            return 0
        train_and_export(args, examples)
        return 0
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
