# DragonViewMobile Application

DragonViewMobile is the AndreiCopy Mobile capstone application for dragon-fruit farm records and operations.

## Current features

- Offline local accounts and Room database storage
- Dashboard and sales analytics
- FIFO harvest inventory grouped by batch
- Multiple size and grade entries per harvest batch
- Sales and configurable pricing
- Stem-planting guidance with milestones, observations, measurements, and progress photos
- Prospect quality-scanner interface

## Build requirements

- Android Studio with JDK 17
- Android SDK 36

Build the debug APK from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The image-classification model, validated plant-stage reference-image dataset, farm mapping, and weight-workflow redesign are intentionally deferred.
