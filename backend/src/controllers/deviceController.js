const { body, param, validationResult } = require('express-validator');
const asyncHandler = require('../utils/asyncHandler');
const deviceService = require('../services/deviceService');
const { nowSql } = require('../utils/time');

function validate(req, res) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    res.status(400).json({ success: false, error: errors.array()[0].msg });
    return false;
  }
  return true;
}

const list = asyncHandler(async (req, res) => {
  const devices = await deviceService.listDevices({
    status: req.query.status,
    sort: req.query.sort,
    search: req.query.search
  });
  res.json({ success: true, devices });
});

const getOne = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const device = await deviceService.getDeviceById(Number(req.params.id));
    if (!device) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }
    res.json({ success: true, device });
  })
];

const getApps = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const device = await deviceService.getDeviceById(Number(req.params.id));
    if (!device) {
      return res.status(404).json({ success: false, error: 'Device not found' });
    }
    const apps = await deviceService.getApps(Number(req.params.id), req.query.type);
    res.json({
      success: true,
      device_id: Number(req.params.id),
      apps,
      total_apps: apps.length
    });
  })
];

const lock = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;

    let packages = [];
    if (Array.isArray(req.body.app_packages)) {
      packages = req.body.app_packages;
    } else if (req.body.app_package) {
      packages = [req.body.app_package];
    }

    const result = await deviceService.lockDevice({
      deviceId: Number(req.params.id),
      appPackages: packages,
      allowedUrls: Array.isArray(req.body.allowed_urls) ? req.body.allowed_urls : [],
      blockWebMedia: req.body.block_web_media,
      admin: req.admin
    });

    res.json({
      success: true,
      message: `Device restricted to ${result.allowed_apps.length} app(s)`,
      device_id: Number(req.params.id),
      locked_to_app: result.display,
      allowed_apps: result.allowed_apps,
      allowed_urls: result.allowed_urls,
      block_web_media: result.block_web_media,
      is_locked: 1,
      timestamp: nowSql()
    });
  })
];

const setWebsites = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const result = await deviceService.setWebAllowlist({
      deviceId: Number(req.params.id),
      allowedUrls: Array.isArray(req.body.allowed_urls) ? req.body.allowed_urls : [],
      blockWebMedia: req.body.block_web_media,
      admin: req.admin
    });
    res.json({
      success: true,
      message: 'Website allowlist saved',
      device_id: Number(req.params.id),
      allowed_urls: result.allowed_urls,
      block_web_media: result.block_web_media,
      timestamp: nowSql()
    });
  })
];

const unlock = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    await deviceService.unlockDevice({
      deviceId: Number(req.params.id),
      admin: req.admin
    });
    res.json({
      success: true,
      message: 'Device unlocked',
      device_id: Number(req.params.id),
      is_locked: 0,
      locked_to_app: null,
      allowed_apps: [],
      allowed_urls: [],
      block_web_media: 1,
      timestamp: nowSql()
    });
  })
];

const sync = [
  body('unique_id').trim().notEmpty().withMessage('unique_id is required'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const result = await deviceService.syncDevice(req.body);
    res.json({
      success: true,
      device_id: result.device_id,
      message: result.registered ? 'Device registered' : undefined,
      restriction: result.restriction,
      protection_pin: result.protection_pin || null
    });
  })
];

const usage = [
  body('unique_id').trim().notEmpty().withMessage('unique_id is required'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const result = await deviceService.recordUsage({
      uniqueId: String(req.body.unique_id).trim(),
      sessions: req.body.sessions
    });
    res.json({ success: true, ...result });
  })
];

const releaseByPin = [
  body('unique_id').trim().notEmpty().withMessage('unique_id is required'),
  body('pin').trim().notEmpty().withMessage('pin is required'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const device = await deviceService.releaseByPin({
      uniqueId: String(req.body.unique_id).trim(),
      pin: String(req.body.pin).trim()
    });
    res.json({
      success: true,
      message: 'All restrictions cleared. Device is Active.',
      device_id: device.device_id
    });
  })
];

const screenTime = [
  param('id').isInt({ min: 1 }).withMessage('Invalid device id'),
  asyncHandler(async (req, res) => {
    if (!validate(req, res)) return;
    const range = req.query.range === '7d' ? '7d' : 'today';
    const data = await deviceService.getScreenTime(Number(req.params.id), range);
    res.json({ success: true, ...data });
  })
];

module.exports = { list, getOne, getApps, lock, setWebsites, unlock, sync, usage, releaseByPin, screenTime };
