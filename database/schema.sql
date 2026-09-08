CREATE DATABASE IF NOT EXISTS device_manager
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE device_manager;

CREATE TABLE IF NOT EXISTS admins (
  admin_id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS devices (
  device_id INT PRIMARY KEY AUTO_INCREMENT,
  unique_id VARCHAR(100) UNIQUE NOT NULL,
  device_name VARCHAR(100),
  device_model VARCHAR(100),
  ip_address VARCHAR(45),
  current_status ENUM('active', 'inactive', 'locked', 'sync_timeout') DEFAULT 'active',
  is_locked INT DEFAULT 0,
  locked_to_app VARCHAR(150),
  last_sync TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_unique_id (unique_id),
  INDEX idx_status (current_status),
  INDEX idx_last_sync (last_sync)
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;

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
) ENGINE=InnoDB;
