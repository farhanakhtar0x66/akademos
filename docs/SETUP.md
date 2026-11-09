# Akademos: server setup and building

Android app for attendance, marks and answer sheets. Teachers and the administrator edit; parents only view their own child.

- **Kotlin + Jetpack Compose** (Material 3, light/dark, large text, spring animations, runs at the phone's highest refresh rate: 90/120 Hz)
- **Supabase** backend. Every permission is enforced *inside the database* (row-level security), not just in the app.

---

## 0. Preview without a server

Until `local.properties` has real Supabase keys, the **debug** build opens a *Preview mode*. Pick Administrator, Teacher or Parent to explore every screen with sample students. Nothing is saved, and release builds never include it.

## 1. One-time server setup (about 10 minutes)

1. Create a free project at <https://supabase.com> (choose the region closest to you, e.g. Mumbai).
2. **SQL Editor → New query** → paste all of [`supabase/schema.sql`](../supabase/schema.sql) → **Run**.
3. **Authentication → Sign In / Providers → Email**:
   - turn **off** “Confirm email” (users sign in with a username, so there's no email to confirm)
   - set minimum password length to **8**
4. **Project Settings → API keys**: copy the **Project URL** and the **publishable** key (`sb_publishable_…`) into `local.properties`. Never use the secret key in the app.
   ```
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=sb_publishable_...
   ```
   (The publishable key is meant to be public. It can't do anything the security rules don't allow.)

## 2. Make the first administrator (your dad)

1. Install the app, tap **Create an account**, pick a username + password.
2. In Supabase **SQL Editor**, run:
   ```sql
   select public.admin_bootstrap_code();
   ```
3. Enter the key it prints (valid for 24 hours, works once) on the app's “Enter your access key” screen.

From then on he makes every other key inside the app: **Admin → Access keys**.

## 3. Day-to-day flow

| Who | How they get in | What they can do |
|---|---|---|
| Administrator | bootstrap key above | everything: classes, subjects, students, batches & timings, keys, block people, activity log |
| Teacher | teacher key from Admin | take attendance, enter marks, upload answer sheets |
| Parent | parent key, tied to their child(ren) | view only: their child's attendance, marks, answer sheets |

**Batches** are how the changing groups work. A batch is a set of students who take one subject together (e.g. *Class 10 · English*), and it has its own weekly timings (e.g. Mon & Wed 4:00–5:00 PM). The same student can be in several batches.

**Reminders** (teacher and admin phones):
- At each batch's start time: a full-screen alert, even on the lock screen, if attendance hasn't been taken yet.
- At the end time: a full-screen “Class is over, mark who is leaving” alert.
- 15 minutes after attendance is first saved: a follow-up alert listing absent students so late arrivals can be marked.
- The app checks the server first, so if another teacher already took attendance, it stays quiet.
- On first launch the Today screen shows a checklist to allow notifications, exact alarms, full-screen alerts and an exception from battery saver. All four are needed on Android 14+.

## 4. Security summary

- Access keys: 12 random characters (about 60 bits). Only a SHA-256 hash is stored. Each key is single-use and expires (1, 2 or 7 days). The admin can cancel one before it's used.
- Wrong keys: 5 tries per 15 minutes per account.
- Roles can't be changed from the app. The only way to get a role is a valid key, checked on the server.
- Who marked attendance or entered marks is stamped by the server, so it can't be faked. Scores over the maximum are rejected.
- Parents can read only rows, and answer-sheet files, for children linked to them.
- The administrator can block anyone at once (**Admin → People**). Every change to attendance, marks, students and accounts is written to an audit log.
- On the phone: the login session is encrypted with a hardware Keystore key. Staff access is locked behind fingerprint / phone PIN after 1 minute in the background. Screenshots are blocked on the marks screen. App backup is disabled.

Run the security tests yourself (needs Python 3):
```bash
python3 -m venv .venv && .venv/bin/pip install pgserver psycopg2-binary
.venv/bin/python supabase/tests/rls_test.py supabase/schema.sql
```

## 5. Building

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Gradle downloads a JDK 17 by itself the first time. ```bash
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk (signed, ~4 MB)
```

Release builds are signed with the key in `~/akademos-signing/` (override with `AKADEMOS_SIGNING=/path/to/keystore.properties`). That folder holds `akademos-release.jks` and `keystore.properties`:

```
storeFile=/absolute/path/akademos-release.jks
storePassword=…
keyAlias=akademos
keyPassword=…
```

**Back this folder up somewhere safe (e.g. a password manager or an encrypted USB drive).** Android only installs updates signed with the same key. If it's lost, everyone has to uninstall and reinstall the app. Never commit it.

### Publishing a new version
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:assembleRelease`
3. Upload it under the fixed name `Akademos.apk`, so the download link and QR code in the README keep working:
   ```bash
   cp app/build/outputs/apk/release/app-release.apk Akademos.apk
   gh release create vX.Y.Z Akademos.apk --title "Akademos X.Y.Z" --notes "What changed…"
   ```
