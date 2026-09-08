const { computeDeviceStatus } = require('../src/utils/status');

describe('computeDeviceStatus', () => {
  const now = Date.now();

  function isoAgo(seconds) {
    return new Date(now - seconds * 1000).toISOString().slice(0, 19).replace('T', ' ');
  }

  test('returns active when recently synced and unlocked', () => {
    expect(computeDeviceStatus({ last_sync: isoAgo(10), is_locked: 0 })).toBe('active');
  });

  test('returns locked when recently synced and locked', () => {
    expect(computeDeviceStatus({ last_sync: isoAgo(10), is_locked: 1 })).toBe('locked');
  });

  test('returns sync_timeout after 5 minutes', () => {
    expect(computeDeviceStatus({ last_sync: isoAgo(301), is_locked: 0 })).toBe('sync_timeout');
  });

  test('returns inactive after 15 minutes', () => {
    expect(computeDeviceStatus({ last_sync: isoAgo(901), is_locked: 1 })).toBe('inactive');
  });

  test('returns inactive when never synced', () => {
    expect(computeDeviceStatus({ last_sync: null, is_locked: 0 })).toBe('inactive');
  });
});
