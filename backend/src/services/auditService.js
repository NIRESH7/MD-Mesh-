const { query } = require('../config/db');

async function writeAudit({ deviceId = null, deviceName = null, action, details, adminId = null }) {
  await query(
    `INSERT INTO audit_logs (device_id, device_name, action, details, admin_id)
     VALUES (?, ?, ?, ?, ?)`,
    [deviceId, deviceName, action, details, adminId]
  );
}

module.exports = { writeAudit };
