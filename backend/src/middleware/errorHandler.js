const logger = require('../utils/logger');

function notFound(req, res, next) {
  res.status(404).json({ success: false, error: 'Not found' });
}

function errorHandler(err, req, res, next) {
  logger.error(`${req.method} ${req.originalUrl} — ${err.message}`, { stack: err.stack });

  if (res.headersSent) {
    return next(err);
  }

  const status = err.status || err.statusCode || 500;
  const message = status === 500 && process.env.NODE_ENV === 'production'
    ? 'Server error'
    : err.message || 'Server error';

  res.status(status).json({
    success: false,
    error: message
  });
}

module.exports = { notFound, errorHandler };
