# Dragon View Upgrade UI/UX Process

## Design direction

The Android Views interface now follows the Dragon View Figma direction: a vivid pink gradient, clean neutral surfaces, rounded white cards, restrained shadows, compact typography, and a camera-centered mobile navigation pattern.

## Functional screens upgraded

- Login and local registration
- Home dashboard and quick actions
- Inventory list and inventory details
- Harvest recording
- Sales list and entry points
- Planting records
- Analytics and revenue chart
- Shared toolbar, bottom navigation, cards, buttons, fields, colors, and spacing

## Prospect-only experiences

- Plant guidance includes a prospective growth-stage timeline above the working planting records.
- Image processing includes a prospective live-scan view and result presentation. The screen explicitly states that the model connection will be added later.

These prospect areas do not claim that unfinished processing logic is operational.

## Preserved architecture

- Java 17 Android application
- Existing fragments, repositories, and navigation destinations
- Room/SQLite database and existing data model
- Existing view-binding IDs used by functional Java code
- Sales remains available from the toolbar while Camera occupies the center navigation position

## Verification

- All Android XML resources are well-formed.
- Custom XML resource references resolve.
- Primary Java view-binding IDs remain present in their layouts.
- The Android SDK is installed at `C:\AndroidSDK` with platform tools, Android 36, and Build Tools 36.0.0.
- `:app:assembleDebug` completed successfully.
- The verified debug APK is `app/build/outputs/apk/debug/DragonView-AndreiCopy-UIUX-Upgrade-Debug.apk`.
- APK SHA-256: `65A778B6BE13EBC34353D65180BBA53406F4D0405E68B65A0C6AE6155F2CC013`.
