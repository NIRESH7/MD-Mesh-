# MD Mesh — Mobile Device Management

Admin console, Node.js API, MySQL, and an Android agent that can lock a device to a single app.

**Default login:** `admin` / `password123`

## Quick start (Docker)

From this folder:

```powershell
docker compose up --build
```

Open [http://localhost:5000](http://localhost:5000). To populate the fleet without a phone:

```powershell
docker compose exec api node scripts/mock-devices.js
```

Then lock a device to WhatsApp from the console. The next agent poll (5 seconds) applies the restriction.

## What you get

| Path | Role |
|------|------|
| `backend/` | Express API, JWT auth, MySQL pool |
| `admin-panel/` | Login, fleet dashboard, lock/unlock, audit log |
| `android/` | Kotlin agent (sync, poll, device admin, hide apps) |
| `database/schema.sql` | MySQL schema |
| `docs/` | Setup, API, APK, security, deploy, troubleshooting |

## Local run (without Docker)

1. Install Node.js 18+ and MySQL 8.
2. Copy `backend/.env.example` to `backend/.env` and set `DB_PASSWORD` and `JWT_SECRET` (32+ characters).
3. `cd backend && npm install && npm run seed && npm test && npm start`
4. Open `http://localhost:5000`

## Android agent

Open `android/` in Android Studio, set the server URL on first launch (use your PC LAN IP, not `localhost`, on a physical device). For real app hiding, set the app as **device owner**:

```powershell
adb shell dpm set-device-owner com.mdmesh.agent/.policy.MeshDeviceAdminReceiver
```

The device must have no existing accounts. Details: `docs/APK.md`.

## How lock works

1. Admin picks a device and an app, confirms lock.
2. API stores `is_locked` + `locked_to_app` and writes an audit row.
3. The agent POSTs `/api/devices/sync` every 5 seconds and receives the restriction.
4. With device owner, other apps are hidden via `DevicePolicyManager.setApplicationHidden`. The locked app is launched.

Unlock reverses the same path.

## Tests

```powershell
cd backend
npm test
```

Covers login validation, JWT middleware, device status, and authenticated listing. Full lock/unlock needs MySQL plus the agent (or `node scripts/mock-devices.js` for the console).
"# MD-Mesh-" 
