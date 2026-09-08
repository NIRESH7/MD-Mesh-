const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { body, validationResult } = require('express-validator');
const { query } = require('../config/db');
const env = require('../config/env');
const asyncHandler = require('../utils/asyncHandler');
const settingsService = require('../services/settingsService');
const { writeAudit } = require('../services/auditService');

function validate(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    res.status(400).json({ success: false, error: errors.array()[0].msg });
    return false;
  }
  return true;
}

const loginValidators = [
  body('username').trim().isLength({ min: 3 }).withMessage('Username must be at least 3 characters'),
  body('password').isLength({ min: 6 }).withMessage('Password must be at least 6 characters')
];

const login = [
  ...loginValidators,
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;

    const username = String(req.body.username).trim();
    const password = String(req.body.password);

    const rows = await query('SELECT admin_id, username, password_hash FROM admins WHERE username = ?', [username]);
    if (!rows.length) {
      return res.status(401).json({ success: false, error: 'Invalid username or password' });
    }

    const admin = rows[0];
    const ok = await bcrypt.compare(password, admin.password_hash);
    if (!ok) {
      return res.status(401).json({ success: false, error: 'Invalid username or password' });
    }

    const token = jwt.sign(
      { adminId: admin.admin_id, username: admin.username },
      env.jwtSecret,
      { expiresIn: env.jwtExpire, algorithm: 'HS256' }
    );

    return res.json({
      success: true,
      token,
      adminId: admin.admin_id,
      username: admin.username
    });
  })
];

const changePassword = [
  body('current_password').isLength({ min: 6 }).withMessage('Current password required'),
  body('new_password').isLength({ min: 6 }).withMessage('New password must be at least 6 characters'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const current = String(req.body.current_password);
    const next = String(req.body.new_password);
    const rows = await query(
      'SELECT admin_id, password_hash FROM admins WHERE admin_id = ?',
      [req.admin.adminId]
    );
    if (!rows.length) {
      return res.status(404).json({ success: false, error: 'Admin not found' });
    }
    const ok = await bcrypt.compare(current, rows[0].password_hash);
    if (!ok) {
      return res.status(401).json({ success: false, error: 'Current password is wrong' });
    }
    const hash = await bcrypt.hash(next, 10);
    await query(
      `UPDATE admins SET password_hash = ?, updated_at = datetime('now') WHERE admin_id = ?`,
      [hash, req.admin.adminId]
    );
    await writeAudit({
      action: 'CHANGE_ADMIN_PASSWORD',
      details: 'Admin login password changed',
      adminId: req.admin.adminId
    });
    res.json({ success: true, message: 'Password updated' });
  })
];

const getUninstallPin = asyncHandler(async (req, res) => {
  const pin = await settingsService.getUninstallPin();
  const reveal = String(req.query.reveal || '') === '1';
  res.json({
    success: true,
    is_set: Boolean(pin),
    pin: reveal && pin ? pin : null,
    masked: pin ? '*'.repeat(pin.length) : null
  });
});

const setUninstallPin = [
  body('pin').trim().matches(/^\d{4,8}$/).withMessage('PIN must be 4–8 digits'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const result = await settingsService.setUninstallPin({
      pin: req.body.pin,
      admin: req.admin
    });
    res.json({ success: true, message: 'Uninstall PIN saved', pin: result.pin });
  })
];

const clearUninstallPin = asyncHandler(async (req, res) => {
  await settingsService.clearUninstallPin({ admin: req.admin });
  res.json({ success: true, message: 'Uninstall PIN cleared' });
});

module.exports = {
  login,
  changePassword,
  getUninstallPin,
  setUninstallPin,
  clearUninstallPin
};
