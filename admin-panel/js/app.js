(function initConsole() {
if (!Auth.require()) return;

const state = {
  devices: [],
  selectedId: null,
  selectedDevice: null,
  apps: [],
  selectedPackages: new Set(),
  allowedUrls: [],
  blockWebMedia: true,
  appSearch: '',
  screenTimeToday: null,
  screenTime7d: null,
  logs: [],
  view: 'fleet'
};

const fleetBody = document.getElementById('fleet-body');
const logsBody = document.getElementById('logs-body');
const drawer = document.getElementById('drawer');
const backdrop = document.getElementById('drawer-backdrop');
const toasts = document.getElementById('toasts');
const modalBackdrop = document.getElementById('modal-backdrop');

function toast(message, type = 'ok') {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = message;
  toasts.appendChild(el);
  setTimeout(() => el.remove(), 3200);
}

function confirmDialog(title, text) {
  return new Promise((resolve) => {
    document.getElementById('modal-title').textContent = title;
    document.getElementById('modal-text').textContent = text;
    modalBackdrop.classList.add('open');
    const ok = document.getElementById('modal-ok');
    const cancel = document.getElementById('modal-cancel');
    const done = (value) => {
      modalBackdrop.classList.remove('open');
      ok.onclick = null;
      cancel.onclick = null;
      resolve(value);
    };
    ok.onclick = () => done(true);
    cancel.onclick = () => done(false);
  });
}

function statusLabel(status) {
  return ({
    active: 'Active',
    locked: 'Restricted',
    inactive: 'Inactive',
    sync_timeout: 'Sync timeout'
  })[status] || status;
}

function connectionLabel(device) {
  const s = device.current_status;
  if (s === 'inactive') return 'Offline';
  if (s === 'sync_timeout') return 'Sync timeout';
  return 'Online';
}

function formatTime(value) {
  if (!value) return 'Never';
  return String(value).replace('T', ' ').slice(0, 19);
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function allowedAppsOf(device) {
  if (Array.isArray(device.allowed_apps) && device.allowed_apps.length) {
    return device.allowed_apps;
  }
  if (device.locked_to_app) {
    return [{ app_package: device.locked_to_app, app_name: device.locked_to_app }];
  }
  return [];
}

function lockedToLabel(device) {
  if (!Number(device.is_locked)) return '—';
  const apps = allowedAppsOf(device);
  if (!apps.length) return escapeHtml(device.locked_to_app || '—');
  if (apps.length <= 4) {
    return escapeHtml(apps.map((a) => a.app_name || a.app_package).join(', '));
  }
  const shown = apps.slice(0, 3).map((a) => a.app_name || a.app_package).join(', ');
  return `${escapeHtml(shown)} <span class="chip-muted">+${apps.length - 3}</span>`;
}

function renderWebsitesSection() {
  const urls = state.allowedUrls || [];
  return `
    <p class="section-title">Allowed websites</p>
    <p class="hint">Type a link, click <strong>Add</strong>, then <strong>Save websites</strong>. Example: https://www.linkedin.com</p>
    <div class="web-add-row">
      <input type="text" id="web-url-input" class="input" placeholder="https://www.linkedin.com" autocomplete="off" />
      <button class="btn btn-ok" type="button" id="web-url-add">Add</button>
    </div>
    <ul class="web-url-list" id="web-url-list">
      ${urls.length
        ? urls.map((u, i) => `
          <li data-index="${i}">
            <span class="mono web-url-text">${escapeHtml(u)}</span>
            <button type="button" class="btn btn-ghost web-url-remove" data-index="${i}">Remove</button>
          </li>`).join('')
        : '<li class="web-url-empty">No websites added yet</li>'}
    </ul>
    <label class="web-media-check">
      <input type="checkbox" id="block-web-media" ${state.blockWebMedia ? 'checked' : ''} />
      Block images &amp; videos (recommended)
    </label>
    <button class="btn btn-ok" type="button" id="save-websites-btn" style="margin-top:8px">Save websites</button>
  `;
}

function normalizeWebsiteInput(raw) {
  let s = String(raw || '').trim();
  if (!s) return null;
  if (!/^https?:\/\//i.test(s)) s = `https://${s}`;
  try {
    const u = new URL(s);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
    u.hash = '';
    let out = u.toString();
    if (out.endsWith('/') && u.pathname === '/') out = out.slice(0, -1);
    return out;
  } catch {
    return null;
  }
}

function bindWebsiteControls() {
  const addBtn = drawer.querySelector('#web-url-add');
  const input = drawer.querySelector('#web-url-input');
  if (addBtn && input) {
    const add = (e) => {
      if (e) {
        e.preventDefault();
        e.stopPropagation();
      }
      if (!Array.isArray(state.allowedUrls)) state.allowedUrls = [];
      const normalized = normalizeWebsiteInput(input.value);
      if (!normalized) {
        toast('Enter a valid link, e.g. https://www.linkedin.com', 'error');
        input.focus();
        return;
      }
      if (state.allowedUrls.includes(normalized)) {
        toast('Already in the list', 'error');
        input.value = '';
        return;
      }
      state.allowedUrls = [...state.allowedUrls, normalized];
      input.value = '';
      refreshWebsiteListOnly();
      toast(`Added — click Save websites (${state.allowedUrls.length})`);
      input.focus();
    };
    addBtn.addEventListener('click', add);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        add(e);
      }
    });
  }
  const media = drawer.querySelector('#block-web-media');
  if (media) {
    media.addEventListener('change', () => {
      state.blockWebMedia = media.checked;
    });
  }
  drawer.querySelectorAll('.web-url-remove').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const i = Number(btn.dataset.index);
      state.allowedUrls = (state.allowedUrls || []).filter((_, idx) => idx !== i);
      refreshWebsiteListOnly();
    });
  });
  const saveBtn = drawer.querySelector('#save-websites-btn');
  if (saveBtn) saveBtn.addEventListener('click', (e) => {
    e.preventDefault();
    saveWebsites();
  });
}

function refreshWebsiteListOnly() {
  const list = drawer.querySelector('#web-url-list');
  if (!list) return;
  const urls = state.allowedUrls || [];
  list.innerHTML = urls.length
    ? urls.map((u, i) => `
      <li data-index="${i}">
        <span class="mono web-url-text">${escapeHtml(u)}</span>
        <button type="button" class="btn btn-ghost web-url-remove" data-index="${i}">Remove</button>
      </li>`).join('')
    : '<li class="web-url-empty">No websites added yet</li>';
  list.querySelectorAll('.web-url-remove').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const i = Number(btn.dataset.index);
      state.allowedUrls = (state.allowedUrls || []).filter((_, idx) => idx !== i);
      refreshWebsiteListOnly();
    });
  });
}

async function saveWebsites() {
  if (!state.selectedId) return;
  if (!Array.isArray(state.allowedUrls)) state.allowedUrls = [];
  // If user typed a URL but forgot Add, include it
  const input = drawer.querySelector('#web-url-input');
  if (input && input.value.trim()) {
    const normalized = normalizeWebsiteInput(input.value);
    if (!normalized) {
      toast('Enter a valid link before saving', 'error');
      return;
    }
    if (!state.allowedUrls.includes(normalized)) {
      state.allowedUrls = [...state.allowedUrls, normalized];
    }
    input.value = '';
    refreshWebsiteListOnly();
  }
  try {
    const res = await Api.setWebsites(state.selectedId, {
      allowed_urls: state.allowedUrls,
      block_web_media: state.blockWebMedia ? 1 : 0
    });
    state.allowedUrls = res.allowed_urls || [];
    state.blockWebMedia = Number(res.block_web_media) !== 0;
    if (state.selectedDevice) {
      state.selectedDevice.allowed_urls = state.allowedUrls;
      state.selectedDevice.block_web_media = state.blockWebMedia ? 1 : 0;
    }
    toast(`Saved ${state.allowedUrls.length} website(s)`);
    refreshWebsiteListOnly();
  } catch (error) {
    toast(error.message || 'Save failed — is the server running?', 'error');
  }
}

function renderAllowedSection(allowed, locked) {
  if (!locked || !allowed.length) {
    return `<p class="hint">No restriction — all apps are free (Active).</p>`;
  }
  return `
    <div class="allow-section">
      <p class="section-title" style="margin-top:0">Apps with access (${allowed.length})</p>
      <ul class="allowed-full-list">
        ${allowed.map((a) => `
          <li>
            <span class="app-name">${escapeHtml(a.app_name || a.app_package)}</span>
            <span class="app-pkg">${escapeHtml(a.app_package)}</span>
          </li>`).join('')}
      </ul>
    </div>`;
}

function formatDuration(seconds) {
  const s = Math.max(0, Number(seconds) || 0);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m`;
  return `${s}s`;
}

function renderScreenTimeBlock(title, data) {
  const apps = (data && data.apps) || [];
  if (!apps.length) {
    return `
      <div class="screen-time-block">
        <p class="screen-time-heading">${escapeHtml(title)}</p>
        <p class="screen-time-empty">No usage recorded yet</p>
      </div>`;
  }
  const maxSec = Math.max(...apps.map((a) => Number(a.duration_sec) || 0), 1);
  return `
    <div class="screen-time-block">
      <p class="screen-time-heading">${escapeHtml(title)}</p>
      <ul class="screen-time-list">
        ${apps.map((a) => {
          const pct = Math.round(((Number(a.duration_sec) || 0) / maxSec) * 100);
          return `
            <li>
              <div class="st-row">
                <span class="st-name">${escapeHtml(a.app_name || a.app_package)}</span>
                <span class="st-dur">${formatDuration(a.duration_sec)}</span>
              </div>
              <div class="st-bar"><span style="width:${pct}%"></span></div>
            </li>`;
        }).join('')}
      </ul>
    </div>`;
}

function renderFleet() {
  if (!state.devices.length) {
    fleetBody.innerHTML = `
      <div class="empty">
        <h2>No devices yet</h2>
        <p>Install the MD Mesh agent on a phone or tablet. It will appear here after the first sync.</p>
      </div>`;
    return;
  }

  fleetBody.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Device</th>
          <th>Model</th>
          <th>Status</th>
          <th>Connection</th>
          <th>Locked to</th>
          <th>Last sync</th>
          <th>ID</th>
        </tr>
      </thead>
      <tbody>
        ${state.devices.map((d) => `
          <tr data-id="${d.device_id}" class="${state.selectedId === d.device_id ? 'selected' : ''}">
            <td>${escapeHtml(d.device_name || 'Unnamed')}</td>
            <td>${escapeHtml(d.device_model || '—')}</td>
            <td><span class="status status-${d.current_status}"><span class="dot"></span>${statusLabel(d.current_status)}</span></td>
            <td>${connectionLabel(d)}</td>
            <td class="mono">${lockedToLabel(d)}</td>
            <td>${formatTime(d.last_sync)}</td>
            <td class="mono">${escapeHtml(d.unique_id)}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>`;

  fleetBody.querySelectorAll('tbody tr').forEach((row) => {
    row.addEventListener('click', () => openDevice(Number(row.dataset.id)));
  });
}

function renderDrawer() {
  const d = state.selectedDevice;
  if (!d) return;
  const locked = Number(d.is_locked) === 1;
  const allowed = allowedAppsOf(d);
  const allowedSet = new Set(allowed.map((a) => a.app_package));

  const apps = state.apps.map((app) => {
    const checked = state.selectedPackages.has(app.app_package);
    const isAllowed = allowedSet.has(app.app_package);
    return `
      <li data-package="${escapeHtml(app.app_package)}" class="${checked ? 'selected' : ''} ${isAllowed && locked ? 'locked-app' : ''}">
        <label class="app-check">
          <input type="checkbox" ${checked ? 'checked' : ''} data-package="${escapeHtml(app.app_package)}" />
          <span class="app-name">${escapeHtml(app.app_name)}</span>
        </label>
        <span class="tag">${app.is_system_app ? 'System' : 'User'}</span>
        <span class="app-pkg">${escapeHtml(app.app_package)}</span>
      </li>`;
  }).join('');

  const canRestrict = state.selectedPackages.size >= 1;

  drawer.innerHTML = `
    <div class="drawer-scroll">
      <div class="drawer-head">
        <h2>${escapeHtml(d.device_name || 'Device')}</h2>
        <button class="btn btn-ghost" type="button" id="close-drawer">Close</button>
      </div>
      <span class="status status-${d.current_status}"><span class="dot"></span>${statusLabel(d.current_status)}</span>
      <div class="meta">
        <div><span>Model</span><span>${escapeHtml(d.device_model || '—')}</span></div>
        <div><span>Device ID</span><span class="mono">${escapeHtml(d.unique_id)}</span></div>
        <div><span>IP</span><span class="mono">${escapeHtml(d.ip_address || '—')}</span></div>
        <div><span>Registered</span><span>${formatTime(d.created_at)}</span></div>
        <div><span>Last sync</span><span>${formatTime(d.last_sync)}</span></div>
        ${locked ? `<div><span>Allowed apps</span><span>${allowed.length}</span></div>` : ''}
      </div>
      ${renderAllowedSection(allowed, locked)}
      ${renderWebsitesSection()}
      <p class="section-title" id="apps-section-title">Installed apps (${state.apps.length})</p>
      <p class="hint">Select one or more apps (include Chrome/Brave to browse). Home stays available; other apps are blocked.</p>
      <input type="search" id="app-search" class="app-search" placeholder="Search apps (name or package)…" value="${escapeHtml(state.appSearch || '')}" autocomplete="off" />
      <ul class="app-list">${apps || '<li>No apps reported yet</li>'}</ul>
      <p class="section-title">Screen time</p>
      ${renderScreenTimeBlock('Today', state.screenTimeToday)}
      ${renderScreenTimeBlock('Last 7 days', state.screenTime7d)}
    </div>
    <div class="drawer-foot">
      <div class="lock-actions">
        ${locked
          ? `<button class="btn btn-ok" id="set-active-btn" type="button">Set Active (unlock all apps)</button>`
          : `<button class="btn btn-danger" id="restrict-btn" type="button" ${canRestrict ? '' : 'disabled'}>
          Restrict to selected apps${canRestrict ? ` (${state.selectedPackages.size})` : ''}
        </button>`}
      </div>
    </div>
  `;

  document.getElementById('close-drawer').onclick = closeDrawer;
  bindWebsiteControls();
  bindAppSearch();
  drawer.querySelectorAll('.app-list input[type="checkbox"]').forEach((input) => {
    input.addEventListener('change', (e) => {
      e.stopPropagation();
      togglePackageSelection(input.dataset.package, input.checked);
    });
  });
  drawer.querySelectorAll('.app-list li[data-package]').forEach((item) => {
    item.addEventListener('click', (e) => {
      if (e.target.closest('input, label')) return;
      const pkg = item.dataset.package;
      const next = !state.selectedPackages.has(pkg);
      togglePackageSelection(pkg, next);
    });
  });
  const restrictBtn = document.getElementById('restrict-btn');
  if (restrictBtn) restrictBtn.onclick = restrictSelected;
  const setActiveBtn = document.getElementById('set-active-btn');
  if (setActiveBtn && !setActiveBtn.disabled) setActiveBtn.onclick = unlockSelected;
}

function bindAppSearch() {
  const input = document.getElementById('app-search');
  if (!input) return;
  const apply = () => {
    state.appSearch = input.value || '';
    filterAppList(state.appSearch);
  };
  input.addEventListener('input', apply);
  // Keep focus if user was typing after a rare full redraw
  if (state.appSearch) {
    filterAppList(state.appSearch);
    const len = input.value.length;
    input.focus();
    input.setSelectionRange(len, len);
  }
}

function filterAppList(query) {
  const q = String(query || '').trim().toLowerCase();
  const items = drawer.querySelectorAll('.app-list li[data-package]');
  let visible = 0;
  items.forEach((li) => {
    const name = (li.querySelector('.app-name')?.textContent || '').toLowerCase();
    const pkg = (li.dataset.package || '').toLowerCase();
    const show = !q || name.includes(q) || pkg.includes(q);
    li.hidden = !show;
    if (show) visible += 1;
  });
  const title = document.getElementById('apps-section-title');
  if (title) {
    title.textContent = q
      ? `Installed apps (${visible} of ${state.apps.length})`
      : `Installed apps (${state.apps.length})`;
  }
  let empty = drawer.querySelector('.app-list-empty-search');
  if (q && visible === 0) {
    if (!empty) {
      empty = document.createElement('li');
      empty.className = 'app-list-empty-search';
      empty.textContent = 'No apps match your search';
      drawer.querySelector('.app-list')?.appendChild(empty);
    }
  } else if (empty) {
    empty.remove();
  }
}

/** Update selection without rebuilding drawer (keeps scroll position). */
function togglePackageSelection(pkg, selected) {
  if (!pkg) return;
  if (selected) state.selectedPackages.add(pkg);
  else state.selectedPackages.delete(pkg);

  const item = drawer.querySelector(`.app-list li[data-package="${CSS.escape(pkg)}"]`);
  if (item) {
    item.classList.toggle('selected', selected);
    const input = item.querySelector('input[type="checkbox"]');
    if (input) input.checked = selected;
  }
  updateDrawerFootActions();
}

function updateDrawerFootActions() {
  const d = state.selectedDevice;
  if (!d) return;
  const locked = Number(d.is_locked) === 1;
  const foot = drawer.querySelector('.drawer-foot .lock-actions');
  if (!foot) return;
  const canRestrict = state.selectedPackages.size >= 1;
  if (locked) {
    foot.innerHTML = `<button class="btn btn-ok" id="set-active-btn" type="button">Set Active (unlock all apps)</button>`;
    const setActiveBtn = document.getElementById('set-active-btn');
    if (setActiveBtn) setActiveBtn.onclick = unlockSelected;
  } else {
    foot.innerHTML = `<button class="btn btn-danger" id="restrict-btn" type="button" ${canRestrict ? '' : 'disabled'}>
      Restrict to selected apps${canRestrict ? ` (${state.selectedPackages.size})` : ''}
    </button>`;
    const restrictBtn = document.getElementById('restrict-btn');
    if (restrictBtn) restrictBtn.onclick = restrictSelected;
  }
}


async function openDevice(id) {
  state.selectedId = id;
  renderFleet();
  try {
    const [deviceRes, appsRes, todayRes, weekRes] = await Promise.all([
      Api.device(id),
      Api.apps(id),
      Api.screenTime(id, 'today').catch(() => ({ apps: [] })),
      Api.screenTime(id, '7d').catch(() => ({ apps: [] }))
    ]);
    state.selectedDevice = deviceRes.device;
    state.apps = appsRes.apps || [];
    state.screenTimeToday = todayRes;
    state.screenTime7d = weekRes;
    state.selectedPackages = new Set(
      allowedAppsOf(deviceRes.device).map((a) => a.app_package)
    );
    state.allowedUrls = Array.isArray(deviceRes.device.allowed_urls)
      ? [...deviceRes.device.allowed_urls]
      : [];
    state.blockWebMedia = Number(deviceRes.device.block_web_media) !== 0;
    renderDrawer();
    drawer.classList.add('open');
    backdrop.classList.add('open');
  } catch (error) {
    if (error.status === 401) return Auth.clear(), location.replace('./index.html');
    toast(error.message, 'error');
  }
}

function closeDrawer() {
  drawer.classList.remove('open');
  backdrop.classList.remove('open');
  state.selectedId = null;
  state.selectedPackages = new Set();
  state.allowedUrls = [];
  state.blockWebMedia = true;
  state.appSearch = '';
  state.screenTimeToday = null;
  state.screenTime7d = null;
  renderFleet();
}

async function restrictSelected() {
  if (!state.selectedId || state.selectedPackages.size < 1) return;
  const packages = [...state.selectedPackages];
  const names = packages.map((pkg) => {
    const app = state.apps.find((a) => a.app_package === pkg);
    return app ? app.app_name : pkg;
  });
  const label = names.length <= 3
    ? names.join(', ')
    : `${names.slice(0, 2).join(', ')} +${names.length - 2} more`;
  const urlNote = state.allowedUrls.length
    ? ` ${state.allowedUrls.length} website(s) allowed.`
    : ' No website links set (browsers can open but no URL lockdown).';
  const ok = await confirmDialog(
    'Restrict device',
    `Allow only: ${label}. Home stays available; all other apps will be blocked.${urlNote}`
  );
  if (!ok) return;
  try {
    await Api.lock(state.selectedId, packages, {
      allowed_urls: state.allowedUrls,
      block_web_media: state.blockWebMedia ? 1 : 0
    });
    toast(`Restricted to ${packages.length} app(s)`);
    await refresh();
    await openDevice(state.selectedId);
  } catch (error) {
    toast(error.message, 'error');
  }
}

async function unlockSelected() {
  const ok = await confirmDialog(
    'Set device Active',
    'Unlock all apps and mark this device Active (free)? Restriction will clear on the next phone sync (~5s).'
  );
  if (!ok) return;
  try {
    await Api.unlock(state.selectedId);
    toast('Device set to Active — all apps unlocked');
    await refresh();
    await openDevice(state.selectedId);
  } catch (error) {
    toast(error.message, 'error');
  }
}

function updateDrawerLiveMeta(device) {
  if (!drawer.classList.contains('open')) return;
  const statusEl = drawer.querySelector('.drawer-scroll > .status');
  if (statusEl) {
    statusEl.className = `status status-${device.current_status}`;
    statusEl.innerHTML = `<span class="dot"></span>${statusLabel(device.current_status)}`;
  }
  const metaRows = drawer.querySelectorAll('.meta > div');
  metaRows.forEach((row) => {
    const label = row.querySelector('span:first-child');
    const value = row.querySelector('span:last-child');
    if (!label || !value) return;
    if (label.textContent === 'Last sync') value.textContent = formatTime(device.last_sync);
    if (label.textContent === 'IP') {
      value.textContent = device.ip_address || '—';
      value.classList.add('mono');
    }
  });
}

async function refresh() {
  if (!state.devices.length) {
    fleetBody.innerHTML = '<p class="empty"><span class="spinner"></span> Loading fleet…</p>';
  }
  try {
    const data = await Api.devices({
      search: document.getElementById('search').value.trim(),
      status: document.getElementById('status-filter').value,
      sort: document.getElementById('sort').value
    });
    state.devices = data.devices || [];
    if (state.view === 'fleet') renderFleet();
    if (state.selectedId && state.selectedDevice) {
      const latest = state.devices.find((d) => d.device_id === state.selectedId);
      if (latest) {
        state.selectedDevice = { ...state.selectedDevice, ...latest };
        // Do not rebuild drawer on poll — that resets scroll to top while selecting apps
        updateDrawerLiveMeta(latest);
      }
    }
  } catch (error) {
    if (error.status === 401) {
      Auth.clear();
      window.location.replace('./index.html');
      return;
    }
    toast(error.message, 'error');
    if (!state.devices.length) {
      fleetBody.innerHTML = `<div class="empty"><h2>Could not load devices</h2><p>${escapeHtml(error.message)}</p><button class="btn" type="button" id="retry-fleet">Retry</button></div>`;
      const retry = document.getElementById('retry-fleet');
      if (retry) retry.onclick = refresh;
    }
  }
}

function renderLogs() {
  if (!state.logs.length) {
    logsBody.innerHTML = '<div class="empty"><h2>No audit events</h2><p>Restrict, unlock, and registration actions will appear here.</p></div>';
    return;
  }
  logsBody.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>Time</th>
          <th>Device</th>
          <th>Action</th>
          <th>Details</th>
          <th>Admin</th>
        </tr>
      </thead>
      <tbody>
        ${state.logs.map((log) => `
          <tr>
            <td>${formatTime(log.timestamp)}</td>
            <td>${escapeHtml(log.device_name || log.device_id || '—')}</td>
            <td class="mono">${escapeHtml(log.action)}</td>
            <td>${escapeHtml(log.details || '')}</td>
            <td>${escapeHtml(log.admin_name || 'system')}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>`;
}

async function refreshLogs() {
  try {
    const data = await Api.logs({
      device_id: document.getElementById('log-device').value.trim(),
      action: document.getElementById('log-action').value,
      start_date: document.getElementById('log-start').value,
      end_date: document.getElementById('log-end').value,
      limit: 200
    });
    state.logs = data.logs || [];
    if (state.view === 'logs') renderLogs();
  } catch (error) {
    if (error.status === 401) {
      Auth.clear();
      window.location.replace('./index.html');
      return;
    }
    toast(error.message, 'error');
  }
}

function exportCsv() {
  const rows = [['Time', 'Device', 'Action', 'Details', 'Admin']];
  state.logs.forEach((log) => {
    rows.push([
      formatTime(log.timestamp),
      log.device_name || '',
      log.action,
      (log.details || '').replace(/"/g, '""'),
      log.admin_name || 'system'
    ]);
  });
  const csv = rows.map((r) => r.map((c) => `"${c}"`).join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'md-mesh-audit.csv';
  a.click();
  URL.revokeObjectURL(url);
}

function showView(name) {
  state.view = name;
  document.querySelectorAll('.view').forEach((el) => el.classList.remove('active'));
  document.getElementById(`view-${name}`).classList.add('active');
  document.querySelectorAll('.topnav [data-view]').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.view === name);
  });
  if (name === 'logs') refreshLogs();
  if (name === 'settings') loadSettings();
}

let pinRevealed = false;

async function loadSettings() {
  const status = document.getElementById('pin-status');
  const input = document.getElementById('uninstall-pin');
  try {
    const data = await Api.getUninstallPin(pinRevealed);
    if (data.is_set) {
      status.textContent = pinRevealed
        ? `PIN is set: ${data.pin}`
        : `PIN is set (masked: ${data.masked}). Tap View to reveal.`;
      if (pinRevealed && data.pin) input.value = data.pin;
      else if (!pinRevealed) input.value = '';
    } else {
      status.textContent = 'No uninstall PIN set yet. Devices will not block uninstall until you save one.';
      input.value = '';
    }
  } catch (error) {
    if (error.status === 401) return Auth.clear(), location.replace('./index.html');
    status.textContent = error.message;
    toast(error.message, 'error');
  }
}

document.querySelectorAll('.topnav [data-view]').forEach((btn) => {
  btn.addEventListener('click', () => showView(btn.dataset.view));
});
document.getElementById('logout').addEventListener('click', () => {
  Auth.clear();
  window.location.replace('./index.html');
});
backdrop.addEventListener('click', closeDrawer);
['search', 'status-filter', 'sort'].forEach((id) => {
  const el = document.getElementById(id);
  if (id === 'search') {
    let t;
    el.addEventListener('input', () => {
      clearTimeout(t);
      t = setTimeout(refresh, 250);
    });
  } else {
    el.addEventListener('change', refresh);
  }
});
['log-device', 'log-action', 'log-start', 'log-end'].forEach((id) => {
  document.getElementById(id).addEventListener('change', refreshLogs);
  document.getElementById(id).addEventListener('keyup', (e) => {
    if (e.key === 'Enter') refreshLogs();
  });
});
document.getElementById('export-logs').addEventListener('click', exportCsv);

document.getElementById('toggle-view-pin').addEventListener('click', async () => {
  pinRevealed = !pinRevealed;
  const btn = document.getElementById('toggle-view-pin');
  const input = document.getElementById('uninstall-pin');
  btn.textContent = pinRevealed ? 'Hide' : 'View';
  input.type = pinRevealed ? 'text' : 'password';
  await loadSettings();
});

document.getElementById('save-pin').addEventListener('click', async () => {
  const pin = document.getElementById('uninstall-pin').value.trim();
  if (!/^\d{4,8}$/.test(pin)) {
    toast('PIN must be 4–8 digits', 'error');
    return;
  }
  try {
    await Api.setUninstallPin(pin);
    toast('Uninstall PIN saved — devices pick it up on next sync');
    pinRevealed = false;
    document.getElementById('toggle-view-pin').textContent = 'View';
    document.getElementById('uninstall-pin').type = 'password';
    await loadSettings();
  } catch (error) {
    toast(error.message, 'error');
  }
});

document.getElementById('clear-pin').addEventListener('click', async () => {
  const ok = await confirmDialog('Clear PIN', 'Remove uninstall protection PIN from all devices?');
  if (!ok) return;
  try {
    await Api.clearUninstallPin();
    toast('PIN cleared');
    pinRevealed = false;
    document.getElementById('uninstall-pin').value = '';
    await loadSettings();
  } catch (error) {
    toast(error.message, 'error');
  }
});

document.getElementById('save-admin-password').addEventListener('click', async () => {
  const current = document.getElementById('current-password').value;
  const next = document.getElementById('new-password').value;
  const confirm = document.getElementById('confirm-password').value;
  if (next.length < 6) {
    toast('New password must be at least 6 characters', 'error');
    return;
  }
  if (next !== confirm) {
    toast('New passwords do not match', 'error');
    return;
  }
  try {
    await Api.changePassword(current, next);
    toast('Admin password updated');
    document.getElementById('current-password').value = '';
    document.getElementById('new-password').value = '';
    document.getElementById('confirm-password').value = '';
  } catch (error) {
    toast(error.message, 'error');
  }
});

refresh();
setInterval(() => {
  refresh();
  if (state.view === 'logs') refreshLogs();
}, 5000);
})();
