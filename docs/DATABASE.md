# Database

MySQL 5.7+ / 8. Schema: `database/schema.sql`. Seed: `backend/scripts/seed.js`.
Tables are also created automatically on API / seed startup via `ensureSchema()`.

## Tables

**admins** — console users. `password_hash` is bcrypt.

**devices** — one row per physical device. `unique_id` is the APK UUID. `is_locked` / `locked_to_app` are the live command the agent reads. `block_web_media` controls website media blocking when locked. `current_status` is updated on sync; the API also recomputes status from `last_sync` so a crashed agent shows inactive.

**device_apps** — installed packages per device. Upserted on each sync (new packages inserted, missing packages deleted).

**restrictions** — 1:1 with devices. Mirrors lock state plus `locked_at` / `unlocked_at`.

**device_allowlist** — packages allowed while the device is locked.

**device_web_allowlist** — website URL prefixes allowed while locked.

**app_usage_sessions** — screen-time sessions reported by the agent.

**settings** — key/value store (e.g. uninstall PIN).

**audit_logs** — REGISTER, SET_ALLOWLIST, UNLOCK_ALL, etc. `admin_id` is null for device-originated events.

## Indexes

`devices.unique_id`, `devices.current_status`, `devices.last_sync`, `device_apps(device_id, app_package)`, `audit_logs(action, timestamp)`.

## Retention

Keep audit rows at least a year. Optional cleanup:

```sql
DELETE FROM audit_logs WHERE timestamp < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 365 DAY);
DELETE FROM devices WHERE last_sync < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 90 DAY);
```

The second statement cascades to apps and restrictions.

## Backup

```powershell
docker compose exec mysql mysqldump -uroot -pmdmesh_root_pass device_manager > backup.sql
```

Restore:

```powershell
Get-Content backup.sql | docker compose exec -T mysql mysql -uroot -pmdmesh_root_pass device_manager
```
