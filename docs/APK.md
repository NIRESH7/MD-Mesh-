# Android agent

Project: `android/` (Kotlin, min SDK 26, target 34). Package: `com.mdmesh.agent`.

There is no prebuilt `.apk` in this repo. Build it in Android Studio (Build → Build Bundle(s) / APK(s) → Build APK(s)). Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Install on a device

1. Enable Developer options and USB debugging.
2. `adb install -r app-debug.apk`
3. Open **MD Mesh**.
4. Server URL:
   - Emulator: `http://10.0.2.2:5000` (already the default)
   - Physical device: `http://YOUR_PC_LAN_IP:5000`
5. Tap **Enable device admin**, accept, then **Connect device**.

The agent generates a UUID, scans packages, and POSTs `/api/devices/sync` every 5 seconds from a foreground service.

## Device owner (required to hide apps)

`DevicePolicyManager.setApplicationHidden` needs **device owner**, not only device admin. The device must be freshly set up (no Google account).

```powershell
adb shell dpm set-device-owner com.mdmesh.agent/.policy.MeshDeviceAdminReceiver
```

To remove later (factory reset is the clean path for production kiosks):

```powershell
adb shell dpm remove-active-admin com.mdmesh.agent/.policy.MeshDeviceAdminReceiver
```

Without device owner, the agent still syncs, still launches the locked app, and still shows lock status. Other apps stay visible in the launcher.

## Lock behaviour

When `restriction.is_locked = 1`:

1. Hide non-protected packages except the target (device owner).
2. Launch the target app.
3. Persistent notification: device is locked.

Unlock unhides packages this agent hid (tracked in SharedPreferences) and clears lock-task packages.

System UI, Play services, and the agent itself are never hidden.

## Permissions

Internet, query all packages, foreground service, boot completed, optional battery exemption and notifications. Device admin is prompted in-app.

## Troubleshooting sync

- Phone and PC on the same Wi-Fi.
- Windows Firewall inbound TCP 5000.
- `http` not `https` unless you terminated TLS.
- Confirm `GET http://LAN_IP:5000/api/health` from the phone browser.
