process.env.JWT_SECRET = 'test_secret_key_must_be_32_chars_min';
process.env.NODE_ENV = 'test';

jest.mock('../src/config/db', () => ({
  query: jest.fn(),
  withTransaction: jest.fn(),
  ping: jest.fn().mockResolvedValue(true),
  pool: { end: jest.fn() }
}));

const request = require('supertest');
const bcrypt = require('bcryptjs');
const { createApp } = require('../src/app');
const { query } = require('../src/config/db');

describe('API endpoints', () => {
  const app = createApp();
  let token;

  beforeAll(async () => {
    const hash = await bcrypt.hash('password123', 4);
    query.mockImplementation(async (sql) => {
      if (sql.includes('FROM admins')) {
        return [{ admin_id: 1, username: 'admin', password_hash: hash }];
      }
      if (sql.includes('FROM devices')) {
        return [];
      }
      if (sql.includes('COUNT(*)')) {
        return [{ total: 0 }];
      }
      return [];
    });

    const res = await request(app)
      .post('/api/admin/login')
      .send({ username: 'admin', password: 'password123' });
    token = res.body.token;
  });

  test('login rejects short passwords', async () => {
    const res = await request(app).post('/api/admin/login').send({ username: 'admin', password: '123' });
    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });

  test('devices require auth', async () => {
    const res = await request(app).get('/api/devices');
    expect(res.status).toBe(401);
  });

  test('devices list with token', async () => {
    const res = await request(app).get('/api/devices').set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(Array.isArray(res.body.devices)).toBe(true);
  });

  test('health endpoint', async () => {
    const res = await request(app).get('/api/health');
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });
});
