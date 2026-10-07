const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
require('dotenv').config({ path: path.join(__dirname, '../.env') });

function first(...keys) {
  for (const key of keys) {
    const value = process.env[key];
    if (value !== undefined && String(value).length > 0) return value;
  }
  return undefined;
}

const nodeEnv = first('HS_NODE_ENV', 'NODE_ENV') || 'development';
const fallbackSecret = 'dev_only_change_me_secret_key_32ch';

const jwtSecret =
  first('JWT_SECRET', 'jwtadminSecret', 'JWT_ADMIN_SECRET') ||
  (nodeEnv === 'production' ? '' : fallbackSecret);

const env = {
  nodeEnv,
  port: Number(first('HS_PORT', 'PORT') || 3034),
  db: {
    host: first('HS_DB_HOST', 'DB_HOST') || '127.0.0.1',
    port: Number(first('HS_DB_PORT', 'DB_PORT') || 3306),
    user: first('HS_DB_USERNAME', 'DB_USER') || 'admin',
    password: first('HS_DB_PASSWORD', 'DB_PASSWORD') || '',
    database: first('HS_DB_NAME', 'DB_NAME') || 'api'
  },
  jwtSecret,
  jwtExpire: first('JWT_EXPIRE') || '24h',
  passwordSecret: first('passwordSecret', 'PASSWORD_SECRET') || '',
  jwtMemberSecret: first('jwtMemberSecret', 'JWT_MEMBER_SECRET') || '',
  jwtSuperAdminSecret: first('jwtSuperAdminSecret', 'JWT_SUPER_ADMIN_SECRET') || '',
  jwtEmailSecret: first('jwtEmailSecret', 'JWT_EMAIL_SECRET') || '',
  adminOrigin: first('ADMIN_ORIGIN') || 'https://admin.pandiyanagency.com',
  apiPublicUrl:
    first('HS_IMAGE', 'API_PUBLIC_URL') || 'https://api.pandiyanagency.com',
  logFile: first('LOG_FILE') || 'logs/server.log',
  syncTimeoutSeconds: Number(first('SYNC_TIMEOUT_SECONDS') || 300),
  inactiveSeconds: Number(first('INACTIVE_SECONDS') || 900)
};

if (!env.jwtSecret || env.jwtSecret.length < 32) {
  throw new Error('JWT_SECRET (or jwtadminSecret) must be set and at least 32 characters');
}

module.exports = env;
