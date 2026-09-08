process.env.JWT_SECRET = 'test_secret_key_must_be_32_chars_min';
process.env.NODE_ENV = 'test';
process.env.DB_HOST = '127.0.0.1';
process.env.DB_NAME = 'device_manager_test';

const jwt = require('jsonwebtoken');
const { authenticate } = require('../src/middleware/auth');

function mockRes() {
  const res = {};
  res.status = jest.fn().mockReturnValue(res);
  res.json = jest.fn().mockReturnValue(res);
  return res;
}

describe('authenticate middleware', () => {
  test('rejects missing token', () => {
    const req = { headers: {} };
    const res = mockRes();
    const next = jest.fn();
    authenticate(req, res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  test('accepts a valid JWT', () => {
    const token = jwt.sign({ adminId: 1, username: 'admin' }, process.env.JWT_SECRET, { expiresIn: '1h' });
    const req = { headers: { authorization: `Bearer ${token}` } };
    const res = mockRes();
    const next = jest.fn();
    authenticate(req, res, next);
    expect(next).toHaveBeenCalled();
    expect(req.admin).toEqual({ adminId: 1, username: 'admin' });
  });
});
