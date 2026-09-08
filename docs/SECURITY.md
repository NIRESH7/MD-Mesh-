# Security

This is a company MDM console. Treat the server like a production control plane.

## Must do before production

- Change `admin` / `password123`.
- Set a long random `JWT_SECRET` (32+ characters).
- Serve the panel and API on HTTPS only (see [DEPLOYMENT.md](DEPLOYMENT.md)).
- Set `ADMIN_ORIGIN` to the real panel origin; `NODE_ENV=production` enables CORS lock-down.
- Restrict `/api/devices/sync` at the network layer (VPN / private Wi-Fi). The APK authenticates with `unique_id` only, by design, so anyone who can reach the API can register a fake device. Do not expose sync to the public internet without extra controls (mTLS, enrollment tokens).
- Keep MySQL off the public internet.

## Already in the code

- Passwords hashed with bcryptjs (cost 10).
- Parameterized SQL (mysql2 placeholders).
- JWT on all admin routes; 24h expiry.
- Login rate limit (20 / 15 minutes / IP).
- Helmet headers, JSON body size cap 5mb.
- Audit trail for register / lock / unlock.
- Device admin + device owner for hide/show (cannot hide apps without the user or provisioning step).

## Device identity

`unique_id` is a UUID stored in app prefs. It can be reset by reinstalling the agent. Device owner mode plus disabling unknown sources reduces tampering. It is not a cryptographic device attestation.

## App hiding

Hiding launchers is not a military sandbox. A determined user with ADB can still interfere unless USB debugging is off and the device is owned. Pair MDM with physical control of the hardware.
