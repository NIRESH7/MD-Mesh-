const fs = require('fs');
const path = require('path');
const winston = require('winston');

const logFile = process.env.LOG_FILE || 'logs/server.log';
const logDir = path.dirname(path.isAbsolute(logFile) ? logFile : path.join(process.cwd(), logFile));

if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

const logger = winston.createLogger({
  level: process.env.NODE_ENV === 'production' ? 'info' : 'debug',
  format: winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    winston.format.errors({ stack: true }),
    winston.format.printf(({ timestamp, level, message, stack }) => {
      return stack
        ? `${timestamp} [${level.toUpperCase()}] ${message}\n${stack}`
        : `${timestamp} [${level.toUpperCase()}] ${message}`;
    })
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({
      filename: path.isAbsolute(logFile) ? logFile : path.join(process.cwd(), logFile),
      maxsize: 5 * 1024 * 1024,
      maxFiles: 5
    })
  ]
});

module.exports = logger;
