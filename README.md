# DragonView Mobile

DragonView is AndreiCopy Mobile's Java/XML Android application for dragon-fruit farm records, inventory, planting, sales, analytics, and offline quality scanning.

## Architecture

`Android app -> Supabase Auth/Data API/RPC -> PostgreSQL`

- Supabase PostgreSQL is authoritative for farm records.
- A workspace can contain one owner and any number of farmers; business records are scoped to the selected workspace.
- Room stores per-user, per-workspace read snapshots and an idempotent offline outbox.
- Sales and inventory-reducing actions require internet to prevent stock conflicts.
- Harvests, planting records, observations, pricing, and archive requests can queue offline.
- Camera/gallery preprocessing and LiteRT inference remain entirely on-device.

## Quality scanner model

The Android scanner consumes a local MobileNetV2 LiteRT model and never uploads
photos for inference. Dataset auditing, training, and export instructions are in
[`ml/README.md`](ml/README.md). The training script exports the finished model and
labels directly to `app/src/main/assets/ml/` using the tensor contract enforced by
the existing Java classifier.

## Configure

1. Create a Supabase project and deploy the migrations under `supabase/migrations`.
2. Copy `local.properties.example` to `local.properties`.
3. Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` using the project's public API values.
4. Never place the database password or service-role key in this repository or APK.

Full instructions are in [SUPABASE_SETUP.md](SUPABASE_SETUP.md).

## Build

Requirements: Android SDK 36 and Java 17.

```powershell
.\build-dragonview.ps1
```

Numbered APKs are written to `releases/`.
