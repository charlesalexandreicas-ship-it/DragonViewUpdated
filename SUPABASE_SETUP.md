# Supabase Setup

## 1. Create and secure the project

Create one managed Supabase project on the Free plan in the nearest available Asia-Pacific region. During development, email confirmation may remain disabled; enable it before production.

Do not enable broad automatic table exposure. The committed migrations explicitly grant read access and RPC execution only to authenticated users, with Row Level Security on every table.

## 2. Link and deploy

Install the Supabase CLI and a Docker-compatible runtime, then run from the repository root:

```powershell
supabase login
supabase link --project-ref YOUR_PROJECT_REF
supabase db push --dry-run
supabase db push
supabase test db
```

The database password is entered only when the CLI requests it. Do not add it to any project file.

## 3. Configure Android

Add these public values to the ignored `local.properties` file:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co/
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

The publishable key is designed for client applications and is constrained by grants and RLS. Never use `service_role` in Android.

## 4. Verify

Before testing password recovery, add this exact URL under **Authentication → URL Configuration → Redirect URLs** in the Supabase dashboard:

```text
ph.dragonview.mobile://auth/recover
```

Keep the default recovery email template's `{{ .ConfirmationURL }}` link so Supabase verifies the one-time token before redirecting back to Android. Configure a custom SMTP provider before production; Supabase's built-in mail service has delivery restrictions. No secret or service-role key belongs in the APK.

To test recovery, request a link from **Forgot password?** on the sign-in screen, open the email link on a device with Dragon View installed, set a new password, and confirm the old password fails while the new one signs in. A missing or expired link should show an error instead of opening the reset form.

1. Register an owner account and confirm its private starter workspace appears.
2. Copy its eight-character invite code, register a farmer account, and join the owner's workspace.
3. Confirm both accounts see the same planting, inventory, and sales records in that workspace.
4. Confirm the farmer cannot configure prices, open full analytics, archive/restore records, regenerate invites, or remove members.
5. Remove the farmer and confirm that account can no longer read the workspace.
6. Create or select a second workspace and confirm records and Room cache snapshots do not cross workspaces.
7. Complete a sale and confirm inventory is deducted exactly once.
8. Repeat the same operation ID and confirm no duplicate record is created.
9. Turn off networking and confirm cached reads and safe queued writes remain scoped to the active workspace.
10. Confirm sales, reductions, and regrading are blocked offline.
11. Confirm camera/gallery scanning and LiteRT inference work in airplane mode.

## Release rule

Production email confirmation must be enabled, debug HTTP logging must remain disabled in release builds, and migrations must pass `supabase db reset` and `supabase test db` before distributing an APK.
