const jwt = require('jsonwebtoken');
const env = require('../config/env');

function authenticate(req, res, next) {
  const header = req.headers.authorization || '';
  const [scheme, token] = header.split(' ');

  if (scheme !== 'Bearer' || !token) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }

  try {
    const payload = jwt.verify(token, env.jwtSecret, { algorithms: ['HS256'] });
    req.admin = {
      adminId: payload.adminId,
      username: payload.username
    };
    return next();
  } catch (error) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }
}

module.exports = { authenticate };
