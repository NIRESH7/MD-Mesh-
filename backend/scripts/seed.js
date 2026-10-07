const path = require('path');
const bcrypt = require('bcryptjs');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const { ensureSchema, query, end } = require('../src/config/db');

async function seed() {
  await ensureSchema();

  const adminUser = process.env.ADMIN_USERNAME || 'admin';
  const adminPassword = process.env.ADMIN_PASSWORD || 'password123';
  const adminEmail = process.env.ADMIN_EMAIL || 'admin@example.com';
  const hash = await bcrypt.hash(adminPassword, 10);

  const existing = await query('SELECT admin_id FROM admins WHERE username = ?', [adminUser]);
  if (!existing.length) {
    await query(
      `INSERT INTO admins (username, password_hash, email) VALUES (?, ?, ?)`,
      [adminUser, hash, adminEmail]
    );
  }

  console.log('MySQL database ready');
  console.log(`Admin user: ${adminUser}`);
  console.log('Password: password123 (unless ADMIN_PASSWORD was set)');
  await end();
}

seed().catch((error) => {
  console.error('Seed failed:', error.message);
  process.exit(1);
});
