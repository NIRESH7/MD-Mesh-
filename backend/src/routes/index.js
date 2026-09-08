const express = require('express');
const rateLimit = require('express-rate-limit');
const { authenticate } = require('../middleware/auth');
const authController = require('../controllers/authController');
const deviceController = require('../controllers/deviceController');
const auditController = require('../controllers/auditController');

const router = express.Router();

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { success: false, error: 'Too many login attempts. Try again later.' }
});

router.post('/admin/login', loginLimiter, authController.login);
router.post('/admin/change-password', authenticate, authController.changePassword);
router.get('/admin/uninstall-pin', authenticate, authController.getUninstallPin);
router.put('/admin/uninstall-pin', authenticate, authController.setUninstallPin);
router.delete('/admin/uninstall-pin', authenticate, authController.clearUninstallPin);

router.post('/devices/sync', deviceController.sync);
router.post('/devices/usage', deviceController.usage);
router.post('/devices/release-by-pin', deviceController.releaseByPin);

router.get('/devices', authenticate, deviceController.list);
router.get('/devices/:id', authenticate, deviceController.getOne);
router.get('/devices/:id/apps', authenticate, deviceController.getApps);
router.get('/devices/:id/screen-time', authenticate, deviceController.screenTime);
router.post('/devices/:id/lock', authenticate, deviceController.lock);
router.put('/devices/:id/websites', authenticate, deviceController.setWebsites);
router.post('/devices/:id/unlock', authenticate, deviceController.unlock);

router.get('/audit-logs', authenticate, auditController.list);

module.exports = router;
