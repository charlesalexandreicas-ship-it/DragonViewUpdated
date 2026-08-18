# AndreiCopy Mobile Core Update

## Completed

- [x] Group Inventory by harvest batch instead of showing every size/grade as a separate card.
- [x] Show the full size, grade, original quantity, remaining quantity, and transaction history after opening a batch.
- [x] Preserve FIFO sales allocation and separate per-classification adjustments/regrading.
- [x] Replace the fixed 45-day planting prospect with stem-planting age and farmer-confirmed milestones.
- [x] Add rooted/unrooted cutting type, current stage, suggested stage, and flowering-based fruit age.
- [x] Add expected appearance, suggested work, and warning guidance for each milestone.
- [x] Add dated observations, optional growth measurements, and farmer progress photos.
- [x] Preserve existing users and records through the Room database version 1-to-2 migration.
- [x] Apply the existing Dragon View visual system to the new screens, including consistent type, card, button, icon, and spacing rules.
- [x] Keep the scanner and image-classification behavior unchanged.
- [x] Keep kilogram/weight behavior unchanged.

## Intentionally deferred

- [ ] Trained image-classification model and dragon-fruit image dataset.
- [ ] Licensed and agronomically validated reference photos for each plant stage.
- [ ] Farm map, field labels, and uploaded satellite imagery.
- [ ] Kilogram/weight workflow redesign.

## Verification

- [x] Debug Java compilation.
- [x] Plant-stage unit tests.
- [x] Android lint (no blocking errors).
- [x] Android resources and navigation compilation.
- [x] Debug APK assembly.
- [ ] On-device exploratory test with real farm records and photos.
