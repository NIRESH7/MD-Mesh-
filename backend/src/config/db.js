const mysql = require('mysql2/promise');
const env = require('./env');
const logger = require('../utils/logger');

const pool = mysql.createPool({
  host: env.db.host,
  port: env.db.port,
  user: env.db.user,
  password: env.db.password,
  database: env.db.database,
  waitForConnections: true,
  connectionLimit: 10,
  namedPlaceholders: false
});

async function ensureSchema() {
  const statements = [
    `CREATE TABLE IF NOT EXISTS admins (
      admin_id INT PRIMARY KEY AUTO_INCREMENT,
      username VARCHAR(50) UNIQUE NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      email VARCHAR(100),
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS devices (
      device_id INT PRIMARY KEY AUTO_INCREMENT,
      unique_id VARCHAR(100) UNIQUE NOT NULL,
      device_name VARCHAR(100),
      device_model VARCHAR(100),
      ip_address VARCHAR(45),
      current_status ENUM('active', 'inactive', 'locked', 'sync_timeout') DEFAULT 'active',
      is_locked INT DEFAULT 0,
      locked_to_app VARCHAR(150),
      block_web_media INT DEFAULT 1,
      last_sync TIMESTAMP NULL DEFAULT NULL,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_unique_id (unique_id),
      INDEX idx_status (current_status),
      INDEX idx_last_sync (last_sync)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS device_apps (
      app_id INT PRIMARY KEY AUTO_INCREMENT,
      device_id INT NOT NULL,
      app_name VARCHAR(150),
      app_package VARCHAR(150),
      is_system_app INT DEFAULT 0,
      added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
      UNIQUE KEY unique_app (device_id, app_package),
      INDEX idx_device_id (device_id),
      INDEX idx_package (app_package)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS restrictions (
      restriction_id INT PRIMARY KEY AUTO_INCREMENT,
      device_id INT NOT NULL UNIQUE,
      locked_app VARCHAR(150),
      restriction_enabled INT DEFAULT 0,
      locked_at TIMESTAMP NULL DEFAULT NULL,
      unlocked_at TIMESTAMP NULL DEFAULT NULL,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
      INDEX idx_device_id (device_id),
      INDEX idx_enabled (restriction_enabled)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS audit_logs (
      log_id INT PRIMARY KEY AUTO_INCREMENT,
      device_id INT NULL,
      device_name VARCHAR(100),
      action VARCHAR(50) NOT NULL,
      details TEXT,
      admin_id INT NULL,
      timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE SET NULL,
      INDEX idx_device_id (device_id),
      INDEX idx_action (action),
      INDEX idx_timestamp (timestamp)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS device_allowlist (
      device_id INT NOT NULL,
      app_package VARCHAR(150) NOT NULL,
      app_name VARCHAR(150),
      PRIMARY KEY (device_id, app_package),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
      INDEX idx_allowlist_device (device_id)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS device_web_allowlist (
      device_id INT NOT NULL,
      url_prefix VARCHAR(512) NOT NULL,
      sort_order INT DEFAULT 0,
      PRIMARY KEY (device_id, url_prefix),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
      INDEX idx_web_allowlist_device (device_id)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS app_usage_sessions (
      session_id INT PRIMARY KEY AUTO_INCREMENT,
      device_id INT NOT NULL,
      app_package VARCHAR(150) NOT NULL,
      app_name VARCHAR(150),
      started_at DATETIME NOT NULL,
      ended_at DATETIME NOT NULL,
      duration_sec INT NOT NULL,
      client_key VARCHAR(200) UNIQUE,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
      INDEX idx_usage_device_time (device_id, started_at)
    ) ENGINE=InnoDB`,

    `CREATE TABLE IF NOT EXISTS settings (
      \`key\` VARCHAR(100) PRIMARY KEY,
      value TEXT,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB`
  ];

  for (const sql of statements) {
    await pool.execute(sql);
  }

  // Migration for DBs created before block_web_media existed
  const [cols] = await pool.execute(
    `SELECT COLUMN_NAME AS name
     FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'devices' AND COLUMN_NAME = 'block_web_media'`,
    [env.db.database]
  );
  if (!cols.length) {
    await pool.execute(
      'ALTER TABLE devices ADD COLUMN block_web_media INT DEFAULT 1'
    );
  }
}

async function query(sql, params = []) {
  const [rows] = await pool.execute(sql, params);
  return rows;
}

async function withTransaction(work) {
  const connection = await pool.getConnection();
  try {
    await connection.beginTransaction();
    const result = await work(connection);
    await connection.commit();
    return result;
  } catch (error) {
    try {
      await connection.rollback();
    } catch (_) {
      /* ignore */
    }
    throw error;
  } finally {
    connection.release();
  }
}

async function ping() {
  const connection = await pool.getConnection();
  try {
    await connection.ping();
  } finally {
    connection.release();
  }
}

async function end() {
  await pool.end();
}

logger.info(
  `MySQL pool ready (${env.db.host}:${env.db.port}/${env.db.database})`
);

module.exports = {
  pool,
  query,
  withTransaction,
  ping,
  end,
  ensureSchema
};
