# Troubleshooting

**Login fails.** Seed the database (`npm run seed` or restart the API container). Default `admin` / `password123`. Check `JWT_SECRET` is 32+ characters.

**Empty fleet.** The APK has not synced, or you have not run `node scripts/mock-devices.js`. Check API logs for `/api/devices/sync`.

**Device stays Inactive.** Last sync older than 15 minutes. Agent killed by battery saver — grant the battery exemption. Confirm server URL and firewall.

**Lock does nothing on the phone.** Device owner is not set (`adb shell dpm set-device-owner ...`). Wait up to 5 seconds for the next poll. Confirm the package name exists on the device.

**Cannot set device owner.** Remove accounts from the device (Settings → Accounts) or factory reset. The owner can only be set when no accounts exist.

**CORS errors.** In production set `ADMIN_ORIGIN` to the exact panel origin. In development all origins are allowed.

**MySQL connection refused.** Docker: wait for the healthcheck. Local: `DB_HOST`, `DB_PASSWORD`, and that the schema database exists (`npm run seed`).

**Port 5000 in use.** Change `PORT` in `.env` / compose.

**APK cannot reach `localhost`.** On a phone, `localhost` is the phone. Use the PC LAN IP or `10.0.2.2` in the emulator.
