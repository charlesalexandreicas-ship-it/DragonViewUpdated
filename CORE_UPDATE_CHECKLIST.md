# AndreiCopy Mobile Core Update

## Completed

- [x] Group Inventory by harvest batch instead of showing every size/grade as a separate card.
- [x] Show the full size, grade, original quantity, remaining quantity, and transaction history after opening a batch.
- [x] Preserve FIFO sales allocation and separate per-classification adjustments/regrading.
- [x] Replace the fixed 45-day planting prospect with stem-planting age and farmer-confirmed milestones.
- [x] Add rooted/unrooted cutting type, current stage, suggested stage, and flowering-based fruit age.
- [x] Add expected appearance, suggested work, and warning guidance for each milestone.
- [x] Add dated observations, optional growth measurements, and farmer progress photos.
- [x] Add Seed and Stem Cutting choices to the global Record Planting action.
- [x] Add a separate Record Grafting event that transitions an eligible seed-grown farm record.
- [x] Make farm records single-open accordions and show every stage in the selected pathway.
- [x] Open stage guidance in a modal bottom sheet with appearance, actions, records, warnings, completion signs, sources, and optional current-stage photo.
- [x] Keep planting/observation recording actions outside the guidance bottom sheet.
- [x] Add a Room version 2-to-3 migration for propagation method and grafting events.
- [x] Add recoverable removal for planting records, inventory batches, and sales records.
- [x] Add Recently Removed with restoration and an archive/restore audit trail.
- [x] Keep archived sales accounting and FIFO history intact; make archived inventory unavailable until restored.
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

The earlier checks above apply to the preceding build. Per the project owner's instruction, the latest Plant Guidance 1–4 update has received static review only; no Gradle build, emulator run, APK verification, or test suite was run for this update.
