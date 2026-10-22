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
|-- metrics_overview.png
|-- test_support.csv
|-- test_support.png
`-- evaluation_summary.json
```

The audit always writes `test_support.png` and `test_support.csv`, including when
final evaluation is blocked by a class with zero held-out support. The confusion
matrix and metrics overview are generated only after all three classes pass the
evaluation preflight checks.

For a defense-stage report before independent Class C test data exists, generate
an explicitly marked preliminary A/B evaluation from the genuine held-out fruit
instances:

```powershell
ml\.venv\Scripts\python.exe ml\evaluate_classifier.py --preliminary-ab
```

This preserves Class C predictions as visible A/B errors in the matrix, but excludes
Class C from per-class and macro metrics. Its output under
`ml/outputs/classifier_evaluation/preliminary_ab/` must not be presented as a final
three-class evaluation.

`instance_predictions.csv` preserves the image, annotation, true label, predicted
label, confidence, and all class probabilities for every evaluated fruit. Overall
accuracy and per-class/macro precision, recall, and F1 are calculated from those
true/predicted pairs with scikit-learn; confidence is never reported as precision.

## External dataset intake

### Downloaded Fresh / Defect quality-grading dataset

The separate `Dragon Fruit Quality Grading Dataset/Original Dataset` has 898
`Fresh` and 754 `Defect` JPEGs. These are **candidate pools**, not DragonView
ground-truth classes: `Fresh` does not distinguish Class A from Class B, and
some `Defect` images show deterioration outside DragonView's still-usable Class C
definition. The 1,652 files contain 99 exact duplicate copies. Consecutive
photos also repeat the same physical fruit, so each numbered JPEG cannot be
counted as an independent specimen.

Generate the audit CSV and numbered contact sheets from the project root:

```powershell
ml\.venv\Scripts\python.exe ml\prepare_quality_grading_review.py
```

The output is `ml/outputs/quality_grading_review/review_manifest.csv` plus
`audit_summary.json` and `fresh_*.jpg` / `defect_*.jpg` contact sheets.
Review the original image for each candidate. Set `review_decision` to
`approved_training`, set `final_label` to `Class A`, `Class B`, or `Class C`,
and set a consistent `physical_fruit_id` for all photos of the same fruit.
Do not approve exact duplicates or visibly moldy/rotten images as edible Class C.
Keep uncertain images as `pending`; never use model guesses as ground truth.

After review, import only the approved rows:

```powershell
ml\.venv\Scripts\python.exe ml\prepare_quality_grading_review.py --import-reviewed
```

The importer verifies source hashes and label/group consistency and places
approved copies in `quality_grading_*_training_only` folders. They cannot enter
validation or test through this intake. Final A/B/C evaluation still requires
independent, individually labeled held-out Class C fruit instances in the
annotation workflow used by `evaluate_classifier.py`.

### Preliminary three-class proof of function (not final research validation)

The numbered Fresh/Defect candidates can be assembled into an *isolated* A/B/C
prototype using `preliminary_quality_selection.json`. It contains provisional
visual A/B labels and four grouped Defect sequences as a C proxy. The selection
excludes obvious mold-heavy sequences, but an image cannot prove that a damaged
fruit is edible or juice-grade. Exact duplicates are removed. Fresh selections
remain training-only because their physical-fruit identities are unverified.

From the Android project root, run:

```powershell
ml\.venv\Scripts\python.exe ml\build_preliminary_quality_dataset.py
ml\.venv\Scripts\python.exe ml\train_classifier.py --dataset-dir ml\outputs\preliminary_three_class\dataset\raw --output-dir ml\outputs\preliminary_three_class\training --asset-dir ml\outputs\preliminary_three_class\candidate_asset --epochs 8 --fine-tune-epochs 3 --batch-size 16
ml\.venv\Scripts\python.exe ml\evaluate_preliminary_quality.py
```

The last command prints real held-out A/B/C results and saves
`evaluation/candidate/confusion_matrix.png`, `metrics.png`, CSV predictions,
and `report.json` under `ml/outputs/preliminary_three_class/`. To regenerate
the figures without retraining, run only the last command. The split manifest
records exactly which image hashes and capture groups entered each split.

The Android prototype retains the previously deployed A/B model and uses the
new candidate only to flag Class C when its probability reaches 0.30. Both
models run locally. To regenerate the **exploratory app-preview** confusion
matrix after the baseline, candidate-test, and candidate-validation prediction
CSVs have been generated:

```powershell
ml\.venv\Scripts\python.exe ml\evaluate_preliminary_quality.py --model app\src\main\assets\ml\mobilenet_v2.tflite --output ml\outputs\preliminary_three_class\evaluation\baseline
ml\.venv\Scripts\python.exe ml\evaluate_preliminary_quality.py --split validation --output ml\outputs\preliminary_three_class\evaluation\candidate_validation
ml\.venv\Scripts\python.exe ml\evaluate_preliminary_combined.py
```

The combined figure is **test-informed**: the two-model rule was selected after
reviewing the preliminary test outcome. It shows current app behavior on those
images, not an unbiased held-out accuracy estimate. Do not cite a perfect
app-preview score as research accuracy. Collect independent labeled physical
fruits across matched capture conditions and run a new untouched test before
claiming performance; source backgrounds may currently reveal the class.

### Provisional Fresh-source Class A additions

Fourteen visually selected Fresh-source photos are included in the **main**
classifier dataset under
`ml/datasets/dragon_fruit_quality/raw/class_a/quality_grading_fresh_provisional_training_only/`.
Their filenames and source SHA-256 hashes are recorded in
`ml/provisional_fresh_a_import.csv`. The `training_only` suffix keeps these
photos out of validation and test. Their A labels are visual prototypes, not
verified DragonView grade ground truth; physical-fruit identities and unseen
sides are unknown. The original downloaded photos were not moved or changed.
The deployed model and previously reported figures have not been retrained.
The main dataset currently has 249 A, 1198 B, and 188 C exact-unique images;
its existing B/C imbalance still fails the release-training safety gate. For
inspection only, run
`python ml/train_classifier.py --audit-only --allow-unsafe-dataset` to see the
counts and warning without training or exporting a model. Do not use the unsafe
override to build a release model.

This is single-fruit *image-level* evaluation on provisional labels. Consecutive
views of the same fruit remain one capture group, but one C test group still
contains many related images. Do not call its image count a count of independent
fruits, or present these figures as the final annotated per-fruit scanner result.
The final detector-plus-classifier evaluation remains `evaluate_classifier.py`
and requires independently annotated held-out fruit instances for all three
classes.

### MultiOrgan A/B visual-candidate intake (2026-09-26)

The downloaded MultiOrgan `Healthy_fruit` collection was visually reviewed for
preliminary A/B candidates. Only 14 byte-unique images were admitted: 3 Class A
and 11 Class B. Exact duplicate source files were not imported. Their original
files are in the downloaded dataset's `Fruit/Class A` and `Fruit/Class B`
folders. The Android project's training-only copies are at
`ml/datasets/dragon_fruit_quality/raw/class_a/multiorgan_visual_training_only`
and `ml/datasets/dragon_fruit_quality/raw/class_b/multiorgan_visual_training_only`.

These are **provisional visual labels**, not verified fruit-grade ground truth.
The fruit are photographed on the plant, physical-fruit IDs and harvest quality
are unknown, and some images may depict the same fruit from another angle.
Do not place them in validation or test or report them as independent specimens.
The prototype comparison dataset is isolated under
`ml/outputs/multiorgan_ab_experiment/dataset/raw`; it contains the previous 919
images plus these 14 training-only images. Its existing validation and test
image hashes were verified unchanged before training. The existing Android
assets and the previous preliminary evaluation remain untouched.

The exact moved source filenames and review limitations are recorded in
`C:\Users\Charlesalexandre\Documents\ChatGPT\CAPSTONE PROJECT\MULTIORGAN_AB_VISUAL_REVIEW.md`.

The isolated experiment can be reproduced locally from its assembled dataset:

```powershell
ml\.venv\Scripts\python.exe ml\train_classifier.py --dataset-dir ml\outputs\multiorgan_ab_experiment\dataset\raw --output-dir ml\outputs\multiorgan_ab_experiment\training --asset-dir ml\outputs\multiorgan_ab_experiment\candidate_asset --epochs 8 --fine-tune-epochs 3 --batch-size 16
ml\.venv\Scripts\python.exe ml\evaluate_preliminary_quality.py --model ml\outputs\multiorgan_ab_experiment\candidate_asset\mobilenet_v2.tflite --split-manifest ml\outputs\multiorgan_ab_experiment\training\classifier_split_manifest.json --output ml\outputs\multiorgan_ab_experiment\evaluation\candidate
```

The 2026-09-26 experiment used 669 training images (A 221, B 322, C 126),
128 validation images (A 32, B 60, C 36), and the unchanged 136-image test
(A 10, B 100, C 26). Its test confusion matrix, with true rows and predicted
columns A/B/C, was `[[4, 6, 0], [0, 100, 0], [0, 5, 21]]`; accuracy was
91.91% and macro F1 was 80.43%. The previous candidate's accuracy was also
91.91% and macro F1 was 79.31%. This small difference on a provisional,
source-confounded test is not evidence of generalization. The application
still uses its previously deployed model assets; no APK was made.

### Defect prototype copied into the main Class C dataset (2026-09-26)

The 89 byte-unique images from the isolated experiment's
`dataset/raw/class_c/defect_prototype` were copied into
`ml/datasets/dragon_fruit_quality/raw/class_c/defect_prototype`. The source
images remain in the experiment. The main Class C directory now has 188 images:
89 Defect prototype and 99 Ripeness 2. These are provisional image labels;
visible defects do not independently establish that a fruit is suitable for
juice, and related burst images are not independent fruit specimens.

The main dataset audit currently stops on class imbalance: 235 unique A,
1,198 unique B, and 188 unique C images (80 exact duplicate B copies ignored).
This intake itself did not retrain or replace the Android model. The existing
experimental confusion matrix remains tied to its saved split manifest and
candidate model, not this larger main dataset.

### Main-dataset preliminary run (2026-09-26)

A separate MobileNetV2 candidate was trained from the current main raw dataset
under `ml/outputs/main_dataset_20260926_run/`. The original Android assets and
earlier experimental run were not changed. The safety audit flagged the
1,198-to-188 class imbalance; the run used `--allow-unsafe-dataset` for this
preliminary check, while the trainer's balanced sampler drew the three classes
equally during training. The split manifest records paths, hashes, and capture
groups. The 1,621 unique images split as follows:

| Class | Train | Validation | Test | Total |
| --- | ---: | ---: | ---: | ---: |
| A | 193 | 32 | 10 | 235 |
| B | 876 | 161 | 161 | 1,198 |
| C | 136 | 16 | 36 | 188 |
| Total | 1,205 | 209 | 207 | 1,621 |

The exported LiteRT model's 207-image held-out confusion matrix (true rows,
predicted columns A/B/C) is `[[10, 0, 0], [0, 161, 0], [8, 3, 25]]`.
Its accuracy is 94.69% and macro F1 is 84.16%; the per-class F1 values are
71.43% (A), 99.08% (B), and 81.97% (C). The training script's Keras-model
test accuracy was 95.65%; use the exported LiteRT evaluation when reporting
the candidate's on-device format.

Run outputs are in `evaluation/candidate/` beneath that run directory:
`confusion_matrix.png`, `metrics.png`, CSV files, per-image predictions, and
`report.json`. All 36 C test images come from one Defect burst group, and the
test contains only 10 A images; the labels and fruit identities remain
provisional. These image-level figures do not validate the complete mobile
detector-plus-classifier scanner or fruit-level generalization.

### Other external frame sources

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
