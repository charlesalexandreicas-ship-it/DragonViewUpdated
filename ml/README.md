# DragonView quality-classifier pipeline

DragonView uses two separate offline models in sequence:

1. YOLO26 detects individual dragon fruits and counts them.
2. The MobileNetV2 quality classifier grades each detected crop as Class A, Class B, or Class C.

The scanner runs step 2 when the detector finds from one through eight fruits. Zero
detections produces `Object unidentified`; more than eight asks the user to reduce
the visible fruit count. The detector always runs before grading.

## Dragon-fruit detector

The detector reads the two Label Studio exports in the authorized external
`ANNOTATED DATASET` directory. Rectangle labels A and B are collapsed to one
`dragon_fruit` detector class. The training script resolves project 7 images from
`DataSet/By 4` and project 9 images from `DataSet/By 8`.

```powershell
ml\.venv\Scripts\python.exe ml\train_detector.py --audit-only
ml\.venv\Scripts\python.exe ml\train_detector.py
ml\.venv\Scripts\python.exe ml\validate_detector.py
```

Capture bursts are kept together in train, validation, or test to prevent adjacent
frames from leaking between splits. Successful training writes the deployable model
and runtime thresholds to:

```text
app/src/main/assets/ml/dragon_fruit_detector.tflite
app/src/main/assets/ml/detector_config.json
```

The generated `ml/datasets/dragon_fruit_detection/manifest.json` is the complete
record of source images used in each split. Generated datasets, checkpoints, and
reports stay out of Git; the deployable TFLite asset and documented source pipeline
are version controlled.

The class folders are the labels. Individual image filenames do not need to contain
`Class A`, `Class B`, or `Class C`.

```text
ml/datasets/dragon_fruit_quality/raw/
|-- class_a/
|   |-- batch_01/*.jpg
|   `-- batch_02/*.jpg
|-- class_b/
|   |-- batch_01/*.jpg
|   `-- batch_02/*.jpg
`-- class_c/
    `-- internet_ripe2_training_only/*.jpg
```

Folder names are the ground-truth labels; batch folders only group related capture
sessions. Class C is a real trained output and is never used as a fallback for an
uncertain A/B prediction.

Run the dependency-free audit first from the repository root:

```powershell
python ml/train_classifier.py --audit-only
```

After the audit passes, create a virtual environment and train locally:

```powershell
python -m venv ml/.venv
ml\.venv\Scripts\Activate.ps1
python -m pip install -r ml/requirements.txt
python ml/train_classifier.py
```

If Windows reports that a TensorFlow file path is too long, temporarily map the
repository to a short drive for installation:

```powershell
subst V: "C:\Users\Charlesalexandre\Desktop\Capstone Related\AndreiCopy-Mobile(Latest)"
V:\ml\.venv\Scripts\python.exe -m pip install -r V:\ml\requirements.txt
subst V: /D
```

Training uses the folder labels automatically, filters exact duplicate images,
keeps related burst/session captures in the same split, preserves each date/batch
capture domain in training, balances A/B/C sampling, and fine-tunes MobileNetV2.
Augmentation is generated in memory; the source dataset is not inflated with
identical files, and all views remain in the source image's split.
A first training run may download ImageNet weights;
Android inference itself is entirely offline.

Successful training writes:

```text
app/src/main/assets/ml/mobilenet_v2.tflite
app/src/main/assets/ml/labels.txt
```

These filenames and tensor shapes match the existing Java/XML scanner. Do not use
`--allow-unsafe-dataset` for a release model; it exists only to exercise the
pipeline before enough independent samples have been collected.

The currently exported classifier is documented in [`MODEL_CARD.md`](MODEL_CARD.md).

## Per-fruit classifier evaluation

Final quality metrics are computed only from individually annotated fruits in the
held-out detector test split. Ground-truth boxes are cropped with the same 6%
padding and MobileNetV2 preprocessing used by Android. The classifier is evaluated
independently of detector accuracy; detector metrics remain in the detector report.

The evaluation is strict: `labels.txt` must contain `Class A`, `Class B`, and
`Class C` in model-output order, the TFLite output must have three probabilities,
and every class must have genuine support in the held-out annotations. It will not
invent Class C from uncertain A/B predictions or publish a misleading zero-support
three-class score.

```powershell
ml\.venv\Scripts\python.exe ml\evaluate_classifier.py --audit-only
ml\.venv\Scripts\python.exe ml\evaluate_classifier.py
```

The completed run writes:

```text
ml/outputs/classifier_evaluation/
|-- instance_predictions.csv
|-- per_class_metrics.csv
|-- confusion_matrix.csv
|-- confusion_matrix.png
`-- evaluation_summary.json
```

`instance_predictions.csv` preserves the image, annotation, true label, predicted
label, confidence, and all class probabilities for every evaluated fruit. Overall
accuracy and per-class/macro precision, recall, and F1 are calculated from those
true/predicted pairs with scikit-learn; confidence is never reported as precision.

## External dataset intake

Internet/downloaded frame datasets must pass the non-destructive intake audit
before being copied into classifier training data:

```powershell
ml\.venv\Scripts\python.exe ml\audit_external_quality_dataset.py
```

See [`INTERNET_DATASET_AUDIT.md`](INTERNET_DATASET_AUDIT.md) for the approved
folder-to-class mapping and leakage gate. Video frames from one capture session
remain one group regardless of the number of extracted JPEG files.

The approved external import is explicit and training-only:

```powershell
ml\.venv\Scripts\python.exe ml\import_external_quality_dataset.py
```

It maps `Ripe_frames` to Class A, `Overripe_frames` to Class B, and
`Ripe2_frames` to Class C. `Rotten_frames` is excluded. Every tenth numbered video
frame is sampled, and each external source folder remains one capture group. These
images are excluded from final held-out metric claims until independent capture
groups exist for validation and testing.

## Model-assisted dataset review

To create a non-destructive reviewed copy after training, run:

```powershell
ml\.venv\Scripts\python.exe ml\segregate_dataset.py --materialize
```

The command keeps `raw/` unchanged, excludes exact SHA-256 duplicates, and writes
confident `class_a/`, confident `class_b/`, and `uncertain/` folders under
`ml/datasets/dragon_fruit_quality/segregated/hybrid_review_v1/`. A CSV manifest
records the source label, model probabilities, shade coverage, review decision,
and output path for every image. Review the `uncertain/` images manually; never
silently use them as training labels.
