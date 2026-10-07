const { query, withTransaction } = require('../config/db');
const env = require('../config/env');
const { withComputedStatus } = require('../utils/status');
const { writeAudit } = require('./auditService');
const settingsService = require('./settingsService');

const SORT_FIELDS = {
  last_sync: 'd.last_sync',
  device_name: 'd.device_name',
  created_at: 'd.created_at',
  current_status: 'computed_status'
};

function decorate(device) {
  return withComputedStatus(device, env.syncTimeoutSeconds, env.inactiveSeconds);
}

function statusCaseSql() {
  const inactive = Number(env.inactiveSeconds);
  const timeout = Number(env.syncTimeoutSeconds);
  return `
    CASE
      WHEN d.last_sync IS NULL OR d.last_sync < DATE_SUB(NOW(), INTERVAL ${inactive} SECOND) THEN 'inactive'
      WHEN d.last_sync < DATE_SUB(NOW(), INTERVAL ${timeout} SECOND) THEN 'sync_timeout'
      WHEN d.is_locked = 1 THEN 'locked'
      ELSE 'active'
    END
  `;
}

async function getAllowlist(deviceId) {
  return query(
    `SELECT app_package, app_name FROM device_allowlist
     WHERE device_id = ? ORDER BY app_name ASC, app_package ASC`,
    [deviceId]
  );
}

async function getWebAllowlist(deviceId) {
  const rows = await query(
    `SELECT url_prefix FROM device_web_allowlist
     WHERE device_id = ? ORDER BY sort_order ASC, url_prefix ASC`,
    [deviceId]
  );
  return rows.map((r) => r.url_prefix);
}

/** Normalize to http(s) URL without fragment; returns null if invalid. */
function normalizeUrlPrefix(raw) {
  let s = String(raw || '').trim();
  if (!s) return null;
  if (!/^https?:\/\//i.test(s)) {
    s = `https://${s}`;
  }
  let url;
  try {
    url = new URL(s);
  } catch {
    return null;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
  if (!url.hostname) return null;
  url.hash = '';
  // Drop trailing slash on path-only root for cleaner prefixes (keep path if longer)
  let out = url.toString();
  if (out.endsWith('/') && url.pathname === '/') {
    out = out.slice(0, -1);
  }
  return out;
}

function normalizeUrlList(urls) {
  const seen = new Set();
  const out = [];
  for (const raw of urls || []) {
    const n = normalizeUrlPrefix(raw);
    if (!n || seen.has(n)) continue;
    seen.add(n);
    out.push(n);
  }
  return out;
}

async function replaceWebAllowlist(conn, deviceId, urls, blockWebMedia) {
  const list = normalizeUrlList(urls);
  const media = blockWebMedia === 0 || blockWebMedia === false ? 0 : 1;
  await conn.execute('DELETE FROM device_web_allowlist WHERE device_id = ?', [deviceId]);
  let i = 0;
  for (const prefix of list) {
    await conn.execute(
      `INSERT INTO device_web_allowlist (device_id, url_prefix, sort_order) VALUES (?, ?, ?)`,
      [deviceId, prefix, i++]
    );
  }
  await conn.execute(
    `UPDATE devices SET block_web_media = ? WHERE device_id = ?`,
    [media, deviceId]
  );
  return { allowed_urls: list, block_web_media: media };
}

function displayLockedTo(allowlist) {
  if (!allowlist.length) return null;
  if (allowlist.length === 1) return allowlist[0].app_name || allowlist[0].app_package;
  const first = allowlist[0].app_name || allowlist[0].app_package;
  return `${first} +${allowlist.length - 1}`;
}

async function attachAllowlist(device) {
  if (!device) return device;
  let allowed_apps = await getAllowlist(device.device_id);

  // Broken state from old single-app locks: is_locked=1 but no allowlist rows
  if (Number(device.is_locked) === 1 && allowed_apps.length === 0) {
    await query(
      `UPDATE devices SET is_locked = 0, locked_to_app = NULL, current_status = 'active' WHERE device_id = ?`,
      [device.device_id]
    );
    await query(
      `UPDATE restrictions SET restriction_enabled = 0, locked_app = NULL, unlocked_at = NOW()
       WHERE device_id = ?`,
      [device.device_id]
    );
    device = { ...device, is_locked: 0, locked_to_app: null };
    allowed_apps = [];
  }

  const decorated = decorate({
    ...device,
    allowed_apps,
    allowed_count: allowed_apps.length,
    allowed_urls: await getWebAllowlist(device.device_id),
    block_web_media: Number(device.block_web_media) === 0 ? 0 : 1,
    locked_to_app: Number(device.is_locked) === 1 ? displayLockedTo(allowed_apps) : null
  });
  return decorated;
}

async function listDevices({ status, sort, search }) {
  const clauses = [];
  const params = [];
  const computed = statusCaseSql();

  if (search) {
    clauses.push('(d.device_name LIKE ? OR d.unique_id LIKE ? OR d.device_model LIKE ?)');
    const like = `%${search}%`;
    params.push(like, like, like);
  }

  const where = clauses.length ? `WHERE ${clauses.join(' AND ')}` : '';
  const sortColumn = SORT_FIELDS[sort] || 'd.last_sync';
  const order = `ORDER BY ${sortColumn === 'computed_status' ? 'computed_status' : sortColumn} DESC`;

  const rows = await query(
    `SELECT d.*, ${computed} AS computed_status
     FROM devices d
     ${where}
     ${order}`,
    params
  );

  let devices = [];
  for (const row of rows) {
    const { computed_status, ...device } = row;
    devices.push(await attachAllowlist(decorate({ ...device, current_status: computed_status })));
  }

  if (status) {
    devices = devices.filter((d) => d.current_status === status);
  }

  return devices;
}

async function getDeviceById(deviceId) {
  const rows = await query('SELECT * FROM devices WHERE device_id = ?', [deviceId]);
  if (!rows.length) return null;
  return attachAllowlist(decorate(rows[0]));
}

async function getDeviceByUniqueId(uniqueId) {
  const rows = await query('SELECT * FROM devices WHERE unique_id = ?', [uniqueId]);
  if (!rows.length) return null;
  return attachAllowlist(decorate(rows[0]));
}

async function getApps(deviceId, type) {
  const clauses = ['device_id = ?'];
  const params = [deviceId];

  if (type === 'system') {
    clauses.push('is_system_app = 1');
  } else if (type === 'user') {
    clauses.push('is_system_app = 0');
  }

  return query(
    `SELECT app_id, app_name, app_package, is_system_app, added_at
     FROM device_apps
     WHERE ${clauses.join(' AND ')}
     ORDER BY is_system_app ASC, app_name ASC`,
    params
  );
}

async function lockDevice({ deviceId, appPackages, allowedUrls, blockWebMedia, admin }) {
  const device = await getDeviceById(deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const packages = [...new Set((appPackages || []).map((p) => String(p || '').trim()).filter(Boolean))];
  if (!packages.length) {
    const error = new Error('Select at least one app');
    error.status = 400;
    throw error;
  }

  const urlList = normalizeUrlList(allowedUrls);
  for (const raw of allowedUrls || []) {
    if (String(raw || '').trim() && !normalizeUrlPrefix(raw)) {
      const error = new Error(`Invalid website URL: ${raw}`);
      error.status = 400;
      throw error;
    }
  }

  const placeholders = packages.map(() => '?').join(',');
  const found = await query(
    `SELECT app_name, app_package FROM device_apps
     WHERE device_id = ? AND app_package IN (${placeholders})`,
    [deviceId, ...packages]
  );
  if (found.length !== packages.length) {
    const error = new Error('One or more apps were not found on this device');
    error.status = 400;
    throw error;
  }

  const byPkg = Object.fromEntries(found.map((a) => [a.app_package, a.app_name]));
  const display = displayLockedTo(found.map((a) => ({ app_package: a.app_package, app_name: a.app_name })));

  let webResult = { allowed_urls: [], block_web_media: 1 };
  await withTransaction(async (conn) => {
    await conn.execute(
      `UPDATE devices
       SET is_locked = 1, locked_to_app = ?, current_status = 'locked'
       WHERE device_id = ?`,
      [display, deviceId]
    );

    await conn.execute(
      `INSERT INTO restrictions (device_id, locked_app, restriction_enabled, locked_at, unlocked_at)
       VALUES (?, ?, 1, NOW(), NULL)
       ON DUPLICATE KEY UPDATE
         locked_app = VALUES(locked_app),
         restriction_enabled = 1,
         locked_at = NOW(),
         unlocked_at = NULL`,
      [deviceId, display]
    );

    await conn.execute('DELETE FROM device_allowlist WHERE device_id = ?', [deviceId]);
    for (const pkg of packages) {
      await conn.execute(
        `INSERT INTO device_allowlist (device_id, app_package, app_name)
         VALUES (?, ?, ?)`,
        [deviceId, pkg, (byPkg[pkg] || pkg).slice(0, 150)]
      );
    }

    webResult = await replaceWebAllowlist(conn, deviceId, urlList, blockWebMedia);
  });

  await writeAudit({
    deviceId,
    deviceName: device.device_name,
    action: 'SET_ALLOWLIST',
    details: `Restricted to ${packages.length} app(s): ${packages.join(', ')}; websites: ${webResult.allowed_urls.length}`,
    adminId: admin.adminId
  });

  return {
    device,
    packages,
    allowed_apps: packages.map((pkg) => ({ app_package: pkg, app_name: byPkg[pkg] || pkg })),
    allowed_urls: webResult.allowed_urls,
    block_web_media: webResult.block_web_media,
    display
  };
}

async function setWebAllowlist({ deviceId, allowedUrls, blockWebMedia, admin }) {
  const device = await getDeviceById(deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  for (const raw of allowedUrls || []) {
    if (String(raw || '').trim() && !normalizeUrlPrefix(raw)) {
      const error = new Error(`Invalid website URL: ${raw}`);
      error.status = 400;
      throw error;
    }
  }

  const webResult = await withTransaction(async (conn) =>
    replaceWebAllowlist(conn, deviceId, allowedUrls, blockWebMedia)
  );

  await writeAudit({
    deviceId,
    deviceName: device.device_name,
    action: 'SET_WEB_ALLOWLIST',
    details: `Websites: ${webResult.allowed_urls.join(', ') || '(none)'}; block_media=${webResult.block_web_media}`,
    adminId: admin.adminId
  });

  return webResult;
}

async function unlockDevice({ deviceId, admin }) {
  const device = await getDeviceById(deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  await withTransaction(async (conn) => {
    await conn.execute(
      `UPDATE devices
       SET is_locked = 0, locked_to_app = NULL, current_status = 'active'
       WHERE device_id = ?`,
      [deviceId]
    );

    await conn.execute(
      `INSERT INTO restrictions (device_id, locked_app, restriction_enabled, locked_at, unlocked_at)
       VALUES (?, NULL, 0, NULL, NOW())
       ON DUPLICATE KEY UPDATE
         locked_app = NULL,
         restriction_enabled = 0,
         unlocked_at = NOW()`,
      [deviceId]
    );

    await conn.execute('DELETE FROM device_allowlist WHERE device_id = ?', [deviceId]);
    await conn.execute('DELETE FROM device_web_allowlist WHERE device_id = ?', [deviceId]);
    await conn.execute('UPDATE devices SET block_web_media = 1 WHERE device_id = ?', [deviceId]);
  });

  await writeAudit({
    deviceId,
    deviceName: device.device_name,
    action: 'UNLOCK_ALL',
    details: 'All apps unlocked',
    adminId: admin.adminId
  });

  return device;
}

async function releaseByPin({ uniqueId, pin }) {
  const settingsService = require('./settingsService');
  const stored = await settingsService.getUninstallPin();
  if (!stored || String(pin || '').trim() !== String(stored)) {
    const error = new Error('Wrong PIN');
    error.status = 401;
    throw error;
  }

  const device = await getDeviceByUniqueId(uniqueId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  await withTransaction(async (conn) => {
    await conn.execute(
      `UPDATE devices
       SET is_locked = 0, locked_to_app = NULL, current_status = 'active'
       WHERE device_id = ?`,
      [device.device_id]
    );
    await conn.execute(
      `INSERT INTO restrictions (device_id, locked_app, restriction_enabled, locked_at, unlocked_at)
       VALUES (?, NULL, 0, NULL, NOW())
       ON DUPLICATE KEY UPDATE
         locked_app = NULL,
         restriction_enabled = 0,
         unlocked_at = NOW()`,
      [device.device_id]
    );
    await conn.execute('DELETE FROM device_allowlist WHERE device_id = ?', [device.device_id]);
    await conn.execute('DELETE FROM device_web_allowlist WHERE device_id = ?', [device.device_id]);
    await conn.execute('UPDATE devices SET block_web_media = 1 WHERE device_id = ?', [device.device_id]);
  });

  await writeAudit({
    deviceId: device.device_id,
    deviceName: device.device_name,
    action: 'RELEASE_BY_PIN',
    details: 'Device unlocked via uninstall PIN on phone (restrictions cleared)',
    adminId: null
  });

  return device;
}

async function replaceApps(connection, deviceId, installedApps) {
  const incoming = Array.isArray(installedApps) ? installedApps : [];
  const packages = incoming
    .map((app) => String(app.app_package || '').trim())
    .filter(Boolean);

  if (!packages.length) {
    return;
  }

  const placeholders = packages.map(() => '?').join(',');
  await connection.execute(
    `DELETE FROM device_apps WHERE device_id = ? AND app_package NOT IN (${placeholders})`,
    [deviceId, ...packages]
  );

  for (const app of incoming) {
    const pkg = String(app.app_package || '').trim();
    if (!pkg) continue;
    const name = String(app.app_name || pkg).slice(0, 150);
    const isSystem = Number(app.is_system_app) ? 1 : 0;
    await connection.execute(
      `INSERT INTO device_apps (device_id, app_name, app_package, is_system_app)
       VALUES (?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         app_name = VALUES(app_name),
         is_system_app = VALUES(is_system_app)`,
      [deviceId, name, pkg.slice(0, 150), isSystem]
    );
  }
}

async function syncDevice(payload) {
  const uniqueId = String(payload.unique_id || '').trim();
  const deviceName = String(payload.device_name || 'Unnamed device').slice(0, 100);
  const deviceModel = String(payload.device_model || '').slice(0, 100);
  const ipAddress = String(payload.ip_address || '').slice(0, 45);
  const installedApps = Array.isArray(payload.installed_apps) ? payload.installed_apps : [];

  if (!uniqueId) {
    const error = new Error('unique_id is required');
    error.status = 400;
    throw error;
  }

  return withTransaction(async (conn) => {
    const [existingRows] = await conn.execute(
      'SELECT * FROM devices WHERE unique_id = ?',
      [uniqueId]
    );

    let device;
    let registered = false;

    if (!existingRows.length) {
      const [insert] = await conn.execute(
        `INSERT INTO devices
          (unique_id, device_name, device_model, ip_address, current_status, is_locked, locked_to_app, last_sync)
         VALUES (?, ?, ?, ?, 'active', 0, NULL, NOW())`,
        [uniqueId, deviceName, deviceModel, ipAddress]
      );
      const [created] = await conn.execute('SELECT * FROM devices WHERE device_id = ?', [insert.insertId]);
      device = created[0];
      registered = true;

      await conn.execute(
        `INSERT INTO restrictions (device_id, locked_app, restriction_enabled)
         VALUES (?, NULL, 0)`,
        [device.device_id]
      );
    } else {
      device = existingRows[0];
      // Refresh device row after possible heal below; provisional status
      await conn.execute(
        `UPDATE devices
         SET device_name = ?, device_model = ?, ip_address = ?, last_sync = NOW()
         WHERE device_id = ?`,
        [deviceName, deviceModel, ipAddress, device.device_id]
      );
      const [updated] = await conn.execute('SELECT * FROM devices WHERE device_id = ?', [device.device_id]);
      device = updated[0];
    }

    await replaceApps(conn, device.device_id, installedApps);

    const [allowRows] = await conn.execute(
      `SELECT app_package, app_name FROM device_allowlist WHERE device_id = ? ORDER BY app_name ASC`,
      [device.device_id]
    );
    const [webRows] = await conn.execute(
      `SELECT url_prefix FROM device_web_allowlist WHERE device_id = ? ORDER BY sort_order ASC, url_prefix ASC`,
      [device.device_id]
    );

    if (registered) {
      await conn.execute(
        `INSERT INTO audit_logs (device_id, device_name, action, details, admin_id)
         VALUES (?, ?, 'REGISTER', ?, NULL)`,
        [device.device_id, deviceName, `Device registered (${deviceModel || 'unknown model'})`]
      );
    }

    // Heal: locked flag with empty allowlist → treat as unlocked for this sync
    let isLocked = Number(device.is_locked) === 1 ? 1 : 0;
    if (isLocked && (!allowRows || !allowRows.length)) {
      await conn.execute(
        `UPDATE devices SET is_locked = 0, locked_to_app = NULL, current_status = 'active' WHERE device_id = ?`,
        [device.device_id]
      );
      isLocked = 0;
      device = { ...device, is_locked: 0, locked_to_app: null };
    } else {
      await conn.execute(
        `UPDATE devices SET current_status = ? WHERE device_id = ?`,
        [isLocked ? 'locked' : 'active', device.device_id]
      );
    }

    const protectionPin = await settingsService.getUninstallPin();
    const allowedUrls = isLocked ? (webRows || []).map((r) => r.url_prefix) : [];
    const blockMedia = Number(device.block_web_media) === 0 ? 0 : 1;
    return {
      registered,
      device_id: device.device_id,
      restriction: {
        is_locked: isLocked,
        locked_to_app: isLocked ? displayLockedTo(allowRows) : null,
        allowed_apps: isLocked ? allowRows : [],
        allowed_urls: allowedUrls,
        block_web_media: isLocked ? blockMedia : 0
      },
      protection_pin: protectionPin || null
    };
  });
}

async function recordUsage({ uniqueId, sessions }) {
  const device = await getDeviceByUniqueId(uniqueId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const incoming = Array.isArray(sessions) ? sessions : [];
  let saved = 0;

  for (const session of incoming.slice(0, 200)) {
    const pkg = String(session.app_package || '').trim();
    const started = String(session.started_at || '').trim();
    const ended = String(session.ended_at || '').trim();
    let duration = Number(session.duration_sec);
    if (!pkg || !started || !ended) continue;
    if (!Number.isFinite(duration) || duration < 1) {
      const startMs = Date.parse(started.replace(' ', 'T'));
      const endMs = Date.parse(ended.replace(' ', 'T'));
      if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) continue;
      duration = Math.round((endMs - startMs) / 1000);
    }
    duration = Math.min(Math.max(Math.round(duration), 1), 12 * 60 * 60);
    const name = String(session.app_name || pkg).slice(0, 150);
    const clientKey = String(session.client_key || `${device.device_id}:${pkg}:${started}:${ended}`).slice(0, 200);

    try {
      await query(
        `INSERT INTO app_usage_sessions
          (device_id, app_package, app_name, started_at, ended_at, duration_sec, client_key)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [device.device_id, pkg, name, started, ended, duration, clientKey]
      );
      saved += 1;
    } catch (_) {
      // Duplicate client_key — ignore for idempotency
    }
  }

  return { device_id: device.device_id, saved };
}

async function getScreenTime(deviceId, range = 'today') {
  const device = await getDeviceById(deviceId);
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const days = range === '7d' ? 7 : 1;
  const rows = await query(
    `SELECT app_package, COALESCE(MAX(app_name), app_package) AS app_name,
            SUM(duration_sec) AS duration_sec
     FROM app_usage_sessions
     WHERE device_id = ?
       AND started_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
     GROUP BY app_package
     ORDER BY duration_sec DESC`,
    [deviceId, days]
  );

  return {
    device_id: deviceId,
    range: days === 7 ? '7d' : 'today',
    apps: rows.map((row) => ({
      app_package: row.app_package,
      app_name: row.app_name,
      duration_sec: Number(row.duration_sec) || 0,
      duration_min: Math.round((Number(row.duration_sec) || 0) / 60)
    }))
  };
}

module.exports = {
  listDevices,
  getDeviceById,
  getDeviceByUniqueId,
  getApps,
  getAllowlist,
  getWebAllowlist,
  lockDevice,
  setWebAllowlist,
  unlockDevice,
  syncDevice,
  recordUsage,
  getScreenTime,
  releaseByPin,
  normalizeUrlPrefix
};
