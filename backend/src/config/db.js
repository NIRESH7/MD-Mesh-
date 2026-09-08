const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const env = require('./env');
const logger = require('../utils/logger');

const dbPath = env.sqlitePath;
fs.mkdirSync(path.dirname(dbPath), { recursive: true });

const db = new Database(dbPath);
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

function ensureSchema() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS admins (
      admin_id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      email TEXT,
      created_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS devices (
      device_id INTEGER PRIMARY KEY AUTOINCREMENT,
      unique_id TEXT UNIQUE NOT NULL,
      device_name TEXT,
      device_model TEXT,
      ip_address TEXT,
      current_status TEXT DEFAULT 'active',
      is_locked INTEGER DEFAULT 0,
      locked_to_app TEXT,
      last_sync TEXT,
      created_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS device_apps (
      app_id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id INTEGER NOT NULL,
      app_name TEXT,
      app_package TEXT,
      is_system_app INTEGER DEFAULT 0,
      added_at TEXT DEFAULT (datetime('now')),
      UNIQUE(device_id, app_package),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS restrictions (
      restriction_id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id INTEGER NOT NULL UNIQUE,
      locked_app TEXT,
      restriction_enabled INTEGER DEFAULT 0,
      locked_at TEXT,
      unlocked_at TEXT,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS audit_logs (
      log_id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id INTEGER,
      device_name TEXT,
      action TEXT NOT NULL,
      details TEXT,
      admin_id INTEGER,
      timestamp TEXT DEFAULT (datetime('now')),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE SET NULL
    );

    CREATE TABLE IF NOT EXISTS device_allowlist (
      device_id INTEGER NOT NULL,
      app_package TEXT NOT NULL,
      app_name TEXT,
      PRIMARY KEY (device_id, app_package),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS device_web_allowlist (
      device_id INTEGER NOT NULL,
      url_prefix TEXT NOT NULL,
      sort_order INTEGER DEFAULT 0,
      PRIMARY KEY (device_id, url_prefix),
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS app_usage_sessions (
      session_id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id INTEGER NOT NULL,
      app_package TEXT NOT NULL,
      app_name TEXT,
      started_at TEXT NOT NULL,
      ended_at TEXT NOT NULL,
      duration_sec INTEGER NOT NULL,
      client_key TEXT UNIQUE,
      FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY,
      value TEXT,
      updated_at TEXT DEFAULT (datetime('now'))
    );

    CREATE INDEX IF NOT EXISTS idx_devices_unique_id ON devices(unique_id);
    CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(current_status);
    CREATE INDEX IF NOT EXISTS idx_device_apps_device_id ON device_apps(device_id);
    CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_logs(timestamp);
    CREATE INDEX IF NOT EXISTS idx_allowlist_device ON device_allowlist(device_id);
    CREATE INDEX IF NOT EXISTS idx_web_allowlist_device ON device_web_allowlist(device_id);
    CREATE INDEX IF NOT EXISTS idx_usage_device_time ON app_usage_sessions(device_id, started_at);
  `);

  // Migrations for existing DBs
  const deviceCols = db.prepare('PRAGMA table_info(devices)').all().map((c) => c.name);
  if (!deviceCols.includes('block_web_media')) {
    db.exec('ALTER TABLE devices ADD COLUMN block_web_media INTEGER DEFAULT 1');
  }
}

ensureSchema();

function makeConnection(database) {
  return {
    async execute(sql, params = []) {
      const trimmed = String(sql).trim();
      const stmt = database.prepare(trimmed);
      if (/^(SELECT|WITH)\b/i.test(trimmed)) {
        return [stmt.all(...params)];
      }
      const info = stmt.run(...params);
      return [{
        insertId: Number(info.lastInsertRowid),
        affectedRows: info.changes,
        changes: info.changes
      }];
    }
  };
}

async function query(sql, params = []) {
  const [rows] = await makeConnection(db).execute(sql, params);
  return rows;
}

async function withTransaction(work) {
  db.exec('BEGIN');
  try {
    const result = await work(makeConnection(db));
    db.exec('COMMIT');
    return result;
  } catch (error) {
    try { db.exec('ROLLBACK'); } catch (_) { /* ignore */ }
    throw error;
  }
}

async function ping() {
  db.prepare('SELECT 1').get();
}

async function end() {
  db.close();
}

logger.info(`SQLite ready at ${dbPath}`);

module.exports = {
  db,
  query,
  withTransaction,
  ping,
  end,
  pool: { end },
  ensureSchema
};
