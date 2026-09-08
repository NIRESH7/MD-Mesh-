const asyncHandler = require('../utils/asyncHandler');
const { query } = require('../config/db');

const list = asyncHandler(async (req, res) => {
  const deviceId = req.query.device_id;
  const action = req.query.action;
  const startDate = req.query.start_date;
  const endDate = req.query.end_date;
  const limit = Math.min(Math.max(Number(req.query.limit) || 50, 1), 500);
  const page = Math.max(Number(req.query.page) || 1, 1);
  const offset = (page - 1) * limit;

  const clauses = [];
  const params = [];

  if (deviceId) {
    clauses.push('l.device_id = ?');
    params.push(Number(deviceId));
  }
  if (action) {
    clauses.push('l.action = ?');
    params.push(String(action));
  }
  if (startDate) {
    clauses.push('l.timestamp >= ?');
    params.push(`${startDate} 00:00:00`);
  }
  if (endDate) {
    clauses.push('l.timestamp <= ?');
    params.push(`${endDate} 23:59:59`);
  }

  const where = clauses.length ? `WHERE ${clauses.join(' AND ')}` : '';

  const countRows = await query(`SELECT COUNT(*) AS total FROM audit_logs l ${where}`, params);
  const total = countRows[0].total;

  const logs = await query(
    `SELECT l.log_id, l.device_id, l.device_name, l.action, l.details,
            l.admin_id, a.username AS admin_name, l.timestamp
     FROM audit_logs l
     LEFT JOIN admins a ON a.admin_id = l.admin_id
     ${where}
     ORDER BY l.timestamp DESC
     LIMIT ${limit} OFFSET ${offset}`,
    params
  );

  res.json({
    success: true,
    logs,
    total_records: total,
    page
  });
});

module.exports = { list };
