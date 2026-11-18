# LoyPOS Plesk MySQL Backend (`novelsapps.com.et`)

Server facts (from the Plesk panel, November 2026):

| Item | Value |
| --- | --- |
| Server IP | `213.55.96.154` |
| Domain | `novelsapps.com.et` (HTTPS, Let's Encrypt, HTTP→HTTPS 301) |
| System / FTP user | `novelsap` |
| SSH access | **Forbidden** — everything is done via Plesk File Manager / FTP / browser |
| Document root | `/httpdocs` |
| Scripting | PHP via FastCGI (CGI/SSI enabled) |
| Database | **Must be created first** — panel shows "Database is not created" |

## One-click setup (recommended)

1. **Create the database** — Plesk → *Databases* → *Add database*:
   - Database name: e.g. `loypos` (Plesk prefixes it → `novelsap_loypos`)
   - Related user + password: use the strong password you generated (rotate the one pasted in chat).
   - Note the exact **database name**, **user** and **password** shown by Plesk.

2. **Upload the API** — copy everything from `plesk-backend/api/v1/`
   (including `install.php`, `index.php`, `devices.php`, `.htaccess`, `config.example.php`)
   into `httpdocs/api/v1/` using Plesk **File Manager** (or FTP as user `novelsap`).
   There is also a prebuilt `plesk-deploy-bundle.zip` to upload and extract in place.

3. **Run the installer** — open:
   ```
   https://novelsapps.com.et/api/v1/install.php?token=67566d1660ab1f7692d250a9
   ```
   - Enter the database name / user / password from step 1.
   - Leave **sync token** empty to auto-generate a strong one.
   - Press **Test connection & run migration**.
   - The migration is idempotent and non-destructive: it only runs
     `CREATE TABLE IF NOT EXISTS` (plus a guarded `ADD COLUMN`) and records
     applied steps in `schema_migrations`. **Existing rows are never modified
     or deleted** — the results page shows row counts before and after so you
     can verify. Steps: `001_core_schema`, `002_device_auth`, `003_account_auth`.
   - Use **Report only** mode first if you just want to see what's in the DB.

4. **Create your owner account** — open
   `devices.php?token=67566d1660ab1f7692d250a9` right after installing and
   fill in the one-time **"Create your owner account"** form (email + password).
   From then on the admin page is normal email + password sign-in. The
   `?token=` link keeps working as the recovery key if the owner password is
   ever lost.

5. **Pair the terminals (fully automatic)** — in the app on each POS tablet:
   *Settings → Sign In & Pair This Device* → sign in with your owner or
   manager account. The terminal registers itself and receives its own
   long-lived device token (stored in Android Keystore). No pairing codes,
   nothing to copy. Review / revoke / delete devices anytime in `devices.php`.

6. **Verify & clean up** — the installer prints the generated sync token
   once (it is also stored in `httpdocs/api/v1/config.php`). With it:
   ```
   curl -H "Authorization: Bearer <sync_token>" https://novelsapps.com.et/api/v1/health
   ```
   Expect `{"ok":true,...,"counts":{...}}`. Then **delete
   `httpdocs/api/v1/install.php`** via File Manager (the `install.lock`
   tombstone keeps browsers from re-running it in the meantime).

7. **Lock it down (optional but recommended)** — once every tablet has signed
   in, switch **"Require device tokens"** in `devices.php`. The shared
   sync_token is then rejected everywhere; each tablet is authenticated and
   individually revocable. Switch back to *transition mode* if you need to
   onboard an old APK build.

## Manual alternative (no installer)

- Import `plesk-backend/schema.sql` via Plesk → Databases → **phpMyAdmin** → Import
  (includes the `devices` and `users` tables).
- Copy `config.example.php` to `config.php` and fill in
  `db_name`, `db_user`, `db_pass` and a random 48-char `sync_token`.

## Files

| File | Purpose |
| --- | --- |
| `api/v1/index.php` | The REST sync API (`/health`, `/sync/pull`, `/sync/push`, `/devices/register`, `/devices/me`), device-token auth |
| `api/v1/install.php` | One-click installer & idempotent migrator (delete after setup) |
| `api/v1/devices.php` | Devices & staff admin: owner bootstrap, sign-in, revoke devices, manage staff, auth-mode toggle |
| `api/v1/config.example.php` | Template — the real `config.php` is created on the server only |
| `api/v1/.htaccess` | Routes requests to `index.php`, passes Authorization header |
| `api/v1/proxy.php` | Server-side proxy example (same array config as `index.php`) |
| `schema.sql` | Full schema, MySQL 8 / MariaDB 10.4+, `utf8mb4` |
