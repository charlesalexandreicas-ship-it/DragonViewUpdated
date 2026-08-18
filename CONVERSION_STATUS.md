# Dragon View Standalone Android Status

Updated: July 27, 2026

## Implemented

- Native Android Java/XML project
- Room/SQLite standalone database
- PBKDF2-hashed local passwords and encrypted remembered session
- Local registration, login, logout, and account data isolation
- Offline dashboard
- Offline FIFO inventory list and transaction details
- Atomic multi-item harvest registration
- Inventory adjustments and regrading
- Per-account fruit-price configuration
- Offline POS checkout with payment validation and FIFO deduction
- Local sales history
- Daily, monthly, and annual sales analytics
- Planting records and date-based lifecycle calculations
- Dragon-fruit Material color system
- No Internet permission, Retrofit, API URL, MySQL, or ADB reverse dependency

## Important data behavior

- Mobile and website data are intentionally separate.
- Uninstalling the app normally removes the local database.
- Signing out keeps local account records on the device.
- A manual encrypted backup and restore interface is not yet implemented.
- The Quality Scanner remains a UI placeholder until a model artifact exists.

## Validation

```text
gradlew.bat clean assembleDebug testDebugUnitTest lintDebug --no-daemon --max-workers=1
```
