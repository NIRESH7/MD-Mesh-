# API

Base URL: `http://HOST:5000`. JSON in and out. Admin routes need `Authorization: Bearer <jwt>`.

## POST /api/admin/login

Body: `{ "username": "admin", "password": "password123" }`

Success: `{ success, token, adminId, username }` — token lasts 24h (`JWT_EXPIRE`).

Username min 3, password min 6. Invalid credentials → 401.

## GET /api/devices

Query: `status`, `sort` (`last_sync` | `device_name` | `created_at`), `search`.

Status is computed:

| Status | Meaning |
|--------|---------|
| `active` | Synced within 5 minutes, unlocked |
| `locked` | Synced within 5 minutes, locked to an app |
| `sync_timeout` | Last sync 5–15 minutes ago |
| `inactive` | No sync for 15+ minutes |

## GET /api/devices/:id

Single device. 404 if missing.

## GET /api/devices/:id/apps

Query: `type=system` or `type=user`.

## POST /api/devices/:id/lock

Body: `{ "app_package": "com.whatsapp" }`

App must already be on the device (reported via sync). Writes `LOCK_TO_APP` audit.

## POST /api/devices/:id/unlock

Clears lock. Writes `UNLOCK_ALL` audit.

## POST /api/devices/sync

No JWT. Called by the APK every 5 seconds.

Body:

```json
{
  "unique_id": "ABC123",
  "device_name": "Tab",
  "device_model": "Samsung Galaxy Tab",
  "ip_address": "192.168.1.10",
  "installed_apps": [
    { "app_name": "WhatsApp", "app_package": "com.whatsapp", "is_system_app": 0 }
  ]
}
```

First unique_id registers the device (`REGISTER` audit). Later calls update apps + `last_sync` and return:

```json
{
  "success": true,
  "device_id": 1,
  "restriction": { "is_locked": 1, "locked_to_app": "com.whatsapp" }
}
```

Routine syncs are not written to audit_logs (that would be ~17k rows/device/day).

## GET /api/audit-logs

Query: `device_id`, `action`, `start_date`, `end_date`, `limit`, `page`.

## GET /api/health

Database ping.
