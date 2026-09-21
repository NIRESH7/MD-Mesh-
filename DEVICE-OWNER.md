# Device Owner setup (phones and tablets)

Pandiyan Agency keeps your **normal phone/tablet Home**. It does **not** replace the launcher.

- **Strict Lock (Soft Lock):** only allowlisted apps can stay open on the normal Home.
- **Device Owner:** same + **Clear Recents** still keeps blocked apps hidden. Soft Lock alone is not enough on Xiaomi / Samsung / many OEMs after Clear Recents.

## What Device Owner does

- Hides / suspends apps that are **not** on the admin allowlist
- Survives Clear Recents / force-stop
- Keeps the **normal system Home** screen
- Same APK for mobile and tablet

## Without Device Owner

You still use normal Home + Strict Lock. Allowed apps open; other apps are kicked back to Home.  
**Clear Recents can turn Strict Lock off** on Xiaomi — then all apps open again until Strict Lock is turned back on.

## One-time setup (each device)

1. **Backup** anything important.
2. **Factory reset** the phone/tablet.
3. On first setup, **skip Google / Xiaomi / Samsung account** (add accounts later if needed).
4. Enable **Developer options** → **USB debugging**.
5. Install the Pandiyan APK:
   ```bat
   adb install -r android\app\build\outputs\apk\debug\app-debug.apk
   ```
   Or from Android Studio / `gradle installDebug`.
6. Set Device Owner from the PC:
   ```bat
   scripts\set-device-owner.ps1
   ```
   Or manually:
   ```bat
   adb shell dpm set-device-owner com.mdmesh.agent/.policy.MeshDeviceAdminReceiver
   ```
7. Open Pandiyan Agency → confirm **Device Owner: ON**.
8. Turn **Strict Lock ON** (backup layer).
9. In the admin panel, restrict the device to the allowed apps.
10. Test: Clear Recents → only allowed apps should open.

## Helper script

```bat
cd "C:\Users\Admin\Desktop\MD Mesh"
powershell -ExecutionPolicy Bypass -File .\scripts\set-device-owner.ps1
```

## If set-device-owner fails

Common causes:

- Google / OEM account already on the device → factory reset and skip account
- Device already has a Device Owner / work profile
- Xiaomi production builds may block shell without a clean wipe
- Wrong component name (must be `com.mdmesh.agent/.policy.MeshDeviceAdminReceiver`)

Check status:

```bat
adb shell dumpsys device_policy | findstr /i "Device Owner"
```

## After Device Owner is ON

- Restrict / unlock from the admin panel as usual
- Normal Home stays; disallowed apps are hidden
- Clear Recents will not bring back hidden apps
- Unlock from admin unhides apps again
