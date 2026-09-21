const TOKEN_KEY = 'pandiyan.token';

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token, remember) {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);
  if (remember) localStorage.setItem(TOKEN_KEY, token);
  else sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);
}

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const auth = getToken();
  if (auth) headers.Authorization = `Bearer ${auth}`;
  const res = await fetch(path, { ...options, headers });
  const data = await res.json().catch(() => ({ success: false, error: 'Invalid server response' }));
  if (!res.ok) {
    const err = new Error(data.error || `Request failed (${res.status})`);
    err.status = res.status;
    err.payload = data;
    throw err;
  }
  return data;
}

export const api = {
  login: (username, password) =>
    request('/api/admin/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  changePassword: (current_password, new_password) =>
    request('/api/admin/change-password', {
      method: 'POST',
      body: JSON.stringify({ current_password, new_password })
    }),
  getUninstallPin: (reveal = false) =>
    request(`/api/admin/uninstall-pin${reveal ? '?reveal=1' : ''}`),
  setUninstallPin: (pin) =>
    request('/api/admin/uninstall-pin', { method: 'PUT', body: JSON.stringify({ pin }) }),
  clearUninstallPin: () => request('/api/admin/uninstall-pin', { method: 'DELETE' }),
  devices: (params = {}) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v) q.set(k, v);
    });
    const suffix = q.toString() ? `?${q}` : '';
    return request(`/api/devices${suffix}`);
  },
  device: (id) => request(`/api/devices/${id}`),
  apps: (id) => request(`/api/devices/${id}/apps`),
  lock: (id, app_packages, { allowed_urls = [], block_web_media = 1 } = {}) =>
    request(`/api/devices/${id}/lock`, {
      method: 'POST',
      body: JSON.stringify({ app_packages, allowed_urls, block_web_media })
    }),
  setWebsites: (id, { allowed_urls = [], block_web_media = 1 } = {}) =>
    request(`/api/devices/${id}/websites`, {
      method: 'PUT',
      body: JSON.stringify({ allowed_urls, block_web_media })
    }),
  unlock: (id) => request(`/api/devices/${id}/unlock`, { method: 'POST' }),
  screenTime: (id, range = 'today') =>
    request(`/api/devices/${id}/screen-time?range=${encodeURIComponent(range)}`),
  logs: (params = {}) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v) q.set(k, v);
    });
    const suffix = q.toString() ? `?${q}` : '';
    return request(`/api/audit-logs${suffix}`);
  }
};
