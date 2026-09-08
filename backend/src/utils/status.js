function ageSeconds(lastSync) {
  if (!lastSync) return Number.POSITIVE_INFINITY;
  const ts = new Date(String(lastSync).replace(' ', 'T') + 'Z').getTime();
  if (Number.isNaN(ts)) {
    const local = Date.parse(lastSync);
    if (Number.isNaN(local)) return Number.POSITIVE_INFINITY;
    return Math.floor((Date.now() - local) / 1000);
  }
  return Math.floor((Date.now() - ts) / 1000);
}

function computeDeviceStatus(device, syncTimeoutSeconds = 300, inactiveSeconds = 900) {
  const age = ageSeconds(device.last_sync);
  if (age >= inactiveSeconds) return 'inactive';
  if (age >= syncTimeoutSeconds) return 'sync_timeout';
  if (Number(device.is_locked) === 1) return 'locked';
  return 'active';
}

function withComputedStatus(device, syncTimeoutSeconds, inactiveSeconds) {
  if (!device) return device;
  return {
    ...device,
    current_status: computeDeviceStatus(device, syncTimeoutSeconds, inactiveSeconds)
  };
}

module.exports = {
  ageSeconds,
  computeDeviceStatus,
  withComputedStatus
};
