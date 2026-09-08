# Database

MySQL 5.7+ / 8. Schema: `database/schema.sql`. Seed: `backend/scripts/seed.js`.

## Tables

**admins** — console users. `password_hash` is bcrypt.

**devices** — one row per physical device. `unique_id` is the APK UUID. `is_locked` / `locked_to_app` are the live command the agent reads. `current_status` is updated on sync; the API also recomputes status from `last_sync` so a crashed agent shows inactive.

**device_apps** — installed packages per device. Replaced on each sync (new packages inserted, missing packages deleted).

**restrictions** — 1:1 with devices. Mirrors lock state plus `locked_at` / `unlocked_at`.

**audit_logs** — REGISTER, LOCK_TO_APP, UNLOCK_ALL. `admin_id` is null for device-originated register.

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
