# DragonView MobileNetV2 prototype

## Intended use

Offline, on-device classification of one detector-cropped dragon fruit as
`Class A`, `Class B`, or `Class C`. This model is a prototype for application and
workflow testing.

## Training data

- Class A: 232 unique images from 22 inferred capture groups.
- Class B: 1,187 unique images from 43 inferred capture groups.
- Class C: 99 representative images from one external capture group.
- 80 exact duplicates were excluded automatically.
- Training uses balanced A/B/C sampling and lightweight geometric/color augmentation.
- The approved external `Ripe_frames`, `Overripe_frames`, and `Ripe2_frames`
  imports are training-only and cannot leak into validation or test.

## Export contract

- Input: FLOAT32 RGB `[1, 224, 224, 3]`, normalized to `[-1, 1]`.
- Output: FLOAT32 softmax `[1, 3]` ordered as `Class A`, `Class B`, `Class C`.
- Runtime: LiteRT, entirely on-device with no inference network request.

## Evaluation status

The exported model passed an inference/export smoke test on the available held-out
A/B split, but no final three-class accuracy is claimed. Class C currently has only
one independent capture group, so all Class C representatives are restricted to
training. A valid final confusion matrix and per-class precision/recall/F1 require
new, independently captured Class C groups reserved for validation and test.

The dataset also remains imbalanced and includes correlated burst frames. A release
claim still requires separately identified physical fruits and capture sessions in
every split. The detector has no independent random-object negative benchmark, so
`Object unidentified` means that the detector returned zero qualifying boxes; it is
not yet a validated open-set guarantee.
