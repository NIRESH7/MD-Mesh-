const { query } = require('../config/db');
const { writeAudit } = require('./auditService');

async function getSetting(key) {
  const rows = await query('SELECT value FROM settings WHERE `key` = ?', [key]);
  return rows.length ? rows[0].value : null;
}

async function setSetting(key, value) {
  await query(
    `INSERT INTO settings (\`key\`, value, updated_at)
     VALUES (?, ?, NOW())
     ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = NOW()`,
    [key, value]
  );
}

async function getUninstallPin() {
  return getSetting('uninstall_pin');
}

async function setUninstallPin({ pin, admin }) {
  const cleaned = String(pin || '').trim();
  if (!/^\d{4,8}$/.test(cleaned)) {
    const error = new Error('PIN must be 4–8 digits');
    error.status = 400;
    throw error;
  }
  await setSetting('uninstall_pin', cleaned);
  await writeAudit({
    action: 'SET_UNINSTALL_PIN',
    details: 'Uninstall protection PIN updated from admin panel',
    adminId: admin.adminId
  });
  return { pin: cleaned };
}

async function clearUninstallPin({ admin }) {
  await query('DELETE FROM settings WHERE `key` = ?', ['uninstall_pin']);
  await writeAudit({
    action: 'CLEAR_UNINSTALL_PIN',
    details: 'Uninstall protection PIN cleared',
    adminId: admin.adminId
  });
}

module.exports = {
  getSetting,
  setSetting,
  getUninstallPin,
  setUninstallPin,
  clearUninstallPin
};
