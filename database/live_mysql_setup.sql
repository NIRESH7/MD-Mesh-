-- ============================================================
-- Pandiyan Agency — LIVE MySQL setup
-- API:  https://api.pandiyanagency.com  (port 3034)
-- Admin panel: https://admin.pandiyanagency.com
--
-- DB credentials (from host):
--   Database : api
--   Username : admin
--   Password : Ct8evxW90npjUENjvsaQfePmR
--   Host     : 127.0.0.1 (same server as Node API)
--
-- How to run (as MySQL root / panel phpMyAdmin import):
--   mysql -u root -p < live_mysql_setup.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `api`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create app user (skip if host already created "admin")
CREATE USER IF NOT EXISTS 'admin'@'localhost' IDENTIFIED BY 'Ct8evxW90npjUENjvsaQfePmR';
CREATE USER IF NOT EXISTS 'admin'@'127.0.0.1' IDENTIFIED BY 'Ct8evxW90npjUENjvsaQfePmR';
GRANT ALL PRIVILEGES ON `api`.* TO 'admin'@'localhost';
GRANT ALL PRIVILEGES ON `api`.* TO 'admin'@'127.0.0.1';
FLUSH PRIVILEGES;

USE `api`;

-- ------------------------------------------------------------
-- Tables
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS admins (
  admin_id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_apps (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS restrictions (
  restriction_id INT PRIMARY KEY AUTO_INCREMENT,
  device_id INT NOT NULL UNIQUE,
  locked_app VARCHAR(150),
  restriction_enabled INT DEFAULT 0,
  locked_at TIMESTAMP NULL DEFAULT NULL,
  unlocked_at TIMESTAMP NULL DEFAULT NULL,
  FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
  INDEX idx_device_id (device_id),
  INDEX idx_enabled (restriction_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_allowlist (
  device_id INT NOT NULL,
  app_package VARCHAR(150) NOT NULL,
  app_name VARCHAR(150),
  PRIMARY KEY (device_id, app_package),
  FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
  INDEX idx_allowlist_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_web_allowlist (
  device_id INT NOT NULL,
  url_prefix VARCHAR(512) NOT NULL,
  sort_order INT DEFAULT 0,
  PRIMARY KEY (device_id, url_prefix),
  FOREIGN KEY (device_id) REFERENCES devices(device_id) ON DELETE CASCADE,
  INDEX idx_web_allowlist_device (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_usage_sessions (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS settings (
  `key` VARCHAR(100) PRIMARY KEY,
  value TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Admin login user is created by backend seed (bcrypt hash):
--   cd backend && npm run seed
-- Default: username admin / password password123
-- (Do NOT insert plaintext passwords into this SQL file.)
-- ------------------------------------------------------------
