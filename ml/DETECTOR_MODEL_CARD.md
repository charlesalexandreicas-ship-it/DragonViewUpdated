# DragonView YOLO26 detector

## Intended use

Offline detection and counting of dragon fruits in a single scanner photo. When
one through eight detections remain after confidence filtering and NMS, the app
crops each region and sends it to the separate Class A/B/C classifier. Zero
detections produces `Object unidentified`; more than eight is not graded.

## Ground truth

- 202 annotated images from 18 capture-second groups.
- 1,248 valid dragon-fruit rectangles: 362 originally tagged A and 886 tagged B.
- The A/B tags are intentionally collapsed to one `dragon_fruit` detection class.
- One rectangle without a grade label is skipped.
- One slightly out-of-bounds rectangle is clamped to the image boundary.
- Every referenced image was resolved locally before training.

The split is capture-group-safe: 142 training images (859 boxes), 37 validation
images (243 boxes), and 23 held-out test images (146 boxes). Related burst frames
never cross split boundaries.

## Export contract

- Architecture: YOLO26n, initialized from public pretrained weights and fine-tuned
  on the local annotations.
- Input: FLOAT32 RGB `[1, 320, 320, 3]`, aspect-preserving letterbox with value 114,
  normalized to `[0, 1]`.
- Output: FLOAT32 `[1, 300, 6]` end-to-end detections in
  `[x1, y1, x2, y2, confidence, class]` order. Android applies the calibrated
  confidence threshold, restores source coordinates, and performs a defensive NMS.
- Runtime: LiteRT on-device. Detector and classifier make no network request.

## Evaluation

The exported LiteRT model uses a validation-calibrated 0.05 confidence threshold.
Validation IoU50 precision/recall/F1 are 99.18% / 99.18% / 99.18%; validation
exact-count accuracy is 89.19% with 0.108 mean absolute count error. On the held-out
test groups, IoU50 precision/recall/F1 are 100% / 98.63% / 99.31%; exact-count
accuracy is 91.30% with 0.087 mean absolute count error. The machine-readable
export validation is `ml/outputs/detector_tflite_report.json`.

## Limitations

The supplied annotations contain positive dragon-fruit scenes but no independently
labeled hard-negative or random-object scene set. The model therefore cannot claim
validated open-set rejection. The zero-detection message is a workflow response,
not proof of out-of-distribution accuracy.
