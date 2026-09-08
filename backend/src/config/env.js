const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const nodeEnv = process.env.NODE_ENV || 'development';
const fallbackSecret = 'dev_only_change_me_secret_key_32ch';

const env = {
  nodeEnv,
  port: Number(process.env.PORT || 5000),
  sqlitePath: path.resolve(
    process.env.SQLITE_PATH || path.join(__dirname, '../../data/mdmesh.sqlite')
  ),
  db: {
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'device_manager'
  },
  jwtSecret: process.env.JWT_SECRET || (nodeEnv === 'production' ? '' : fallbackSecret),
  jwtExpire: process.env.JWT_EXPIRE || '24h',
  adminOrigin: process.env.ADMIN_ORIGIN || 'http://localhost:5000',
  logFile: process.env.LOG_FILE || 'logs/server.log',
  syncTimeoutSeconds: Number(process.env.SYNC_TIMEOUT_SECONDS || 300),
  inactiveSeconds: Number(process.env.INACTIVE_SECONDS || 900)
};

if (!env.jwtSecret || env.jwtSecret.length < 32) {
  throw new Error('JWT_SECRET must be set and at least 32 characters');
}

module.exports = env;
