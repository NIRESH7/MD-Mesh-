import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, clearToken, getToken, saveToken } from './api';

function statusLabel(s) {
  const map = {
    active: 'Active',
    locked: 'Restricted',
    inactive: 'Inactive',
    sync_timeout: 'Sync timeout'
  };
  return map[s] || s || '—';
}

function formatTime(v) {
  if (!v) return '—';
  return String(v).replace('T', ' ').slice(0, 19);
}

function allowedAppsOf(device) {
  if (Array.isArray(device?.allowed_apps) && device.allowed_apps.length) return device.allowed_apps;
  if (device?.locked_to_app) return [{ app_package: device.locked_to_app, app_name: device.locked_to_app }];
  return [];
}

function lockedToLabel(device) {
  if (!Number(device.is_locked)) return '—';
  const apps = allowedAppsOf(device);
  if (!apps.length) return device.locked_to_app || '—';
  if (apps.length <= 3) return apps.map((a) => a.app_name || a.app_package).join(', ');
  return `${apps.slice(0, 2).map((a) => a.app_name || a.app_package).join(', ')} +${apps.length - 2}`;
}

function isOnline(device) {
  const s = device.current_status;
  return s === 'active' || s === 'locked';
}

function Toast({ message, type, onDone }) {
  useEffect(() => {
    const t = setTimeout(onDone, 3200);
    return () => clearTimeout(t);
  }, [onDone]);
  return <div className={`toast toast-${type || 'ok'}`}>{message}</div>;
}

function Login({ onLogin }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await api.login(username.trim(), password);
      saveToken(res.token, remember);
      onLogin();
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth">
      <form className="auth-card" onSubmit={submit}>
        <img src="/logo-pandiyan.png?v=2" alt="Pandiyan Agency" className="auth-logo" />

        <label className="auth-field">
          <span>Username</span>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label className="auth-field">
          <span>Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        <label className="check">
          <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} />
          Remember me
        </label>

        {error ? <p className="form-error">{error}</p> : null}

        <button className="auth-btn" type="submit" disabled={busy}>
          {busy ? 'Please wait…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}

function DeviceDrawer({
  device,
  apps,
  selected,
  setSelected,
  allowedUrls,
  setAllowedUrls,
  blockMedia,
  setBlockMedia,
  screenToday,
  screenWeek,
  onClose,
  onRestrict,
  onUnlock,
  onSaveWeb,
  toast
}) {
  const [search, setSearch] = useState('');
  const [urlInput, setUrlInput] = useState('');
  const locked = Number(device.is_locked) === 1;
  const allowed = allowedAppsOf(device);
  const allowedSet = new Set(allowed.map((a) => a.app_package));

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return apps;
    return apps.filter(
      (a) =>
        (a.app_name || '').toLowerCase().includes(q) ||
        (a.app_package || '').toLowerCase().includes(q)
    );
  }, [apps, search]);

  function toggle(pkg) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(pkg)) next.delete(pkg);
      else next.add(pkg);
      return next;
    });
  }

  function addUrl() {
    let s = urlInput.trim();
    if (!s) return;
    if (!/^https?:\/\//i.test(s)) s = `https://${s}`;
    try {
      const u = new URL(s);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') throw new Error('bad');
      u.hash = '';
      let out = u.toString();
      if (out.endsWith('/') && u.pathname === '/') out = out.slice(0, -1);
      if (allowedUrls.includes(out)) {
        toast('Already in the list', 'error');
        return;
      }
      setAllowedUrls([...allowedUrls, out]);
      setUrlInput('');
      toast(`Added — click Save websites (${allowedUrls.length + 1})`);
    } catch {
      toast('Enter a valid link, e.g. https://www.linkedin.com', 'error');
    }
  }

  return (
    <aside className="drawer open">
      <div className="drawer-scroll">
        <div className="drawer-head">
          <h2>{device.device_name || 'Device'}</h2>
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            Close
          </button>
        </div>
        <span className={`status status-${device.current_status}`}>
          <span className="dot" />
          {statusLabel(device.current_status)}
        </span>
        <div className="meta">
          <div>
            <span>Model</span>
            <span>{device.device_model || '—'}</span>
          </div>
          <div>
            <span>Device ID</span>
            <span className="mono">{device.unique_id}</span>
          </div>
          <div>
            <span>IP</span>
            <span className="mono">{device.ip_address || '—'}</span>
          </div>
          <div>
            <span>Last sync</span>
            <span>{formatTime(device.last_sync)}</span>
          </div>
          {locked ? (
            <div>
              <span>Allowed apps</span>
              <span>{allowed.length}</span>
            </div>
          ) : null}
        </div>

        {locked && allowed.length ? (
          <div className="allow-section">
            <p className="section-title">Apps with access ({allowed.length})</p>
            <ul className="allowed-list">
              {allowed.map((a) => (
                <li key={a.app_package}>
                  <strong>{a.app_name || a.app_package}</strong>
                  <span className="mono">{a.app_package}</span>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <p className="hint">No restriction — all apps are free (Active).</p>
        )}

        <p className="hint">
          Add links, then Save websites. If Chrome (or another browser) is allowed, only these links can open — other sites are blocked.
        </p>
        <div className="web-row">
          <input
            value={urlInput}
            onChange={(e) => setUrlInput(e.target.value)}
            placeholder="https://www.linkedin.com"
            onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addUrl())}
          />
          <button type="button" className="btn-action btn-action-main" style={{ flex: '0 0 auto' }} onClick={addUrl}>
            Add
          </button>
        </div>
        <ul className="web-list">
          {allowedUrls.length ? (
            allowedUrls.map((u, i) => (
              <li key={u}>
                <span className="mono">{u}</span>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setAllowedUrls(allowedUrls.filter((_, idx) => idx !== i))}
                >
                  Remove
                </button>
              </li>
            ))
          ) : (
            <li className="muted">No websites added yet</li>
          )}
        </ul>
        <label className="check">
          <input type="checkbox" checked={blockMedia} onChange={(e) => setBlockMedia(e.target.checked)} />
          Block images &amp; videos (recommended)
        </label>
        <button type="button" className="btn-action btn-action-soft" style={{ marginTop: 8, width: 'auto' }} onClick={onSaveWeb}>
          Save websites
        </button>

        <p className="section-title">Installed apps ({filtered.length}{search ? ` of ${apps.length}` : ''})</p>
        <p className="hint">
          Tick apps to allow. Home stays available.
          {locked ? ' Then tap Save apps.' : ''}
        </p>
        <input
          className="app-search"
          placeholder="Search apps (name or package)…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <ul className="app-list">
          {filtered.map((app) => {
            const checked = selected.has(app.app_package);
            const isAllowed = allowedSet.has(app.app_package);
            return (
              <li
                key={app.app_package}
                className={`${checked ? 'selected' : ''} ${isAllowed && locked ? 'locked-app' : ''}`}
                onClick={() => toggle(app.app_package)}
              >
                <label className="app-check" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggle(app.app_package)}
                  />
                  <span>{app.app_name}</span>
                </label>
                <span className="tag">{app.is_system_app ? 'System' : 'User'}</span>
                <span className="mono pkg">{app.app_package}</span>
              </li>
            );
          })}
        </ul>

        <p className="section-title">Screen time</p>
        <ScreenBlock title="Today" data={screenToday} />
        <ScreenBlock title="Last 7 days" data={screenWeek} />
      </div>
      <div className="drawer-foot">
        <button
          type="button"
          className="btn-action btn-action-main"
          disabled={selected.size < 1}
          onClick={onRestrict}
        >
          {locked ? `Save apps (${selected.size})` : `Restrict (${selected.size})`}
        </button>
        {locked ? (
          <button type="button" className="btn-action btn-action-soft" onClick={onUnlock}>
            Unlock all
          </button>
        ) : null}
      </div>
    </aside>
  );
}

function ScreenBlock({ title, data }) {
  const apps = data?.apps || [];
  if (!apps.length) {
    return (
      <div className="screen-block">
        <p className="screen-heading">{title}</p>
        <p className="muted">No usage recorded yet</p>
      </div>
    );
  }
  const maxSec = Math.max(...apps.map((a) => Number(a.duration_sec) || 0), 1);
  return (
    <div className="screen-block">
      <p className="screen-heading">{title}</p>
      <ul className="screen-list">
        {apps.map((a) => {
          const pct = Math.round(((Number(a.duration_sec) || 0) / maxSec) * 100);
          const s = Math.max(0, Number(a.duration_sec) || 0);
          const h = Math.floor(s / 3600);
          const m = Math.floor((s % 3600) / 60);
          const label = h > 0 ? `${h}h ${m}m` : m > 0 ? `${m}m` : `${s}s`;
          return (
            <li key={a.app_package || a.app_name}>
              <div className="screen-row">
                <span>{a.app_name || a.app_package}</span>
                <span>{label}</span>
              </div>
              <div className="bar">
                <i style={{ width: `${pct}%` }} />
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function Console({ onLogout }) {
  const [view, setView] = useState('fleet');
  const [devices, setDevices] = useState([]);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('last_sync');
  const [selectedId, setSelectedId] = useState(null);
  const [device, setDevice] = useState(null);
  const [apps, setApps] = useState([]);
  const [selectedPkgs, setSelectedPkgs] = useState(new Set());
  const [allowedUrls, setAllowedUrls] = useState([]);
  const [blockMedia, setBlockMedia] = useState(true);
  const [screenToday, setScreenToday] = useState(null);
  const [screenWeek, setScreenWeek] = useState(null);
  const [logs, setLogs] = useState([]);
  const [toast, setToast] = useState(null);
  const [pinInfo, setPinInfo] = useState({ is_set: false, masked: null, pin: null });
  const [pinInput, setPinInput] = useState('');

  const showToast = useCallback((message, type = 'ok') => {
    setToast({ message, type, id: Date.now() });
  }, []);

  const refreshFleet = useCallback(async () => {
    try {
      const data = await api.devices({ search, status, sort });
      setDevices(data.devices || []);
      if (selectedId) {
        const latest = (data.devices || []).find((d) => d.device_id === selectedId);
        if (latest) setDevice((prev) => (prev ? { ...prev, ...latest } : latest));
      }
    } catch (err) {
      if (err.status === 401) {
        clearToken();
        onLogout();
        return;
      }
      showToast(err.message, 'error');
    }
  }, [search, status, sort, selectedId, onLogout, showToast]);

  useEffect(() => {
    refreshFleet();
    const t = setInterval(refreshFleet, 5000);
    return () => clearInterval(t);
  }, [refreshFleet]);

  async function openDevice(id) {
    setSelectedId(id);
    try {
      const [deviceRes, appsRes, todayRes, weekRes] = await Promise.all([
        api.device(id),
        api.apps(id),
        api.screenTime(id, 'today').catch(() => ({ apps: [] })),
        api.screenTime(id, '7d').catch(() => ({ apps: [] }))
      ]);
      setDevice(deviceRes.device);
      setApps(appsRes.apps || []);
      setSelectedPkgs(new Set(allowedAppsOf(deviceRes.device).map((a) => a.app_package)));
      setAllowedUrls(Array.isArray(deviceRes.device.allowed_urls) ? [...deviceRes.device.allowed_urls] : []);
      setBlockMedia(Number(deviceRes.device.block_web_media) !== 0);
      setScreenToday(todayRes);
      setScreenWeek(weekRes);
    } catch (err) {
      if (err.status === 401) {
        clearToken();
        onLogout();
        return;
      }
      showToast(err.message, 'error');
    }
  }

  async function restrict() {
    if (!selectedId || selectedPkgs.size < 1) return;
    const packages = [...selectedPkgs];
    const ok = window.confirm(`Allow only ${packages.length} selected app(s)? Other apps will be blocked.`);
    if (!ok) return;
    try {
      await api.lock(selectedId, packages, {
        allowed_urls: allowedUrls,
        block_web_media: blockMedia ? 1 : 0
      });
      showToast(`Restricted to ${packages.length} app(s)`);
      await refreshFleet();
      await openDevice(selectedId);
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function unlock() {
    if (!selectedId) return;
    if (!window.confirm('Unlock all apps and set device Active?')) return;
    try {
      await api.unlock(selectedId);
      showToast('Device set to Active');
      await refreshFleet();
      await openDevice(selectedId);
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function saveWeb() {
    if (!selectedId) return;
    try {
      await api.setWebsites(selectedId, {
        allowed_urls: allowedUrls,
        block_web_media: blockMedia ? 1 : 0
      });
      showToast(`Saved ${allowedUrls.length} website(s)`);
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function loadLogs() {
    try {
      const data = await api.logs({ limit: 200 });
      setLogs(data.logs || []);
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  async function loadPin() {
    try {
      setPinInfo(await api.getUninstallPin(false));
    } catch (err) {
      showToast(err.message, 'error');
    }
  }

  useEffect(() => {
    if (view === 'logs') loadLogs();
    if (view === 'settings') loadPin();
  }, [view]);

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <img src="/logo-pandiyan.png" alt="Pandiyan Agency" />
          <strong>Pandiyan Agency</strong>
        </div>
        <nav className="topnav">
          <button type="button" className={view === 'fleet' ? 'active' : ''} onClick={() => setView('fleet')}>
            Fleet
          </button>
          <button type="button" className={view === 'logs' ? 'active' : ''} onClick={() => setView('logs')}>
            Audit
          </button>
          <button type="button" className={view === 'settings' ? 'active' : ''} onClick={() => setView('settings')}>
            Settings
          </button>
          <button
            type="button"
            className="nav-out"
            onClick={() => {
              clearToken();
              onLogout();
            }}
          >
            Sign out
          </button>
        </nav>
      </header>

      <main className="workspace">
        {view === 'fleet' ? (
          <section className="view">
            <div className="toolbar">
              <label>
                Search
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Name, model, or ID"
                />
              </label>
              <label>
                Status
                <select value={status} onChange={(e) => setStatus(e.target.value)}>
                  <option value="">All</option>
                  <option value="active">Active</option>
                  <option value="locked">Restricted</option>
                  <option value="inactive">Inactive</option>
                  <option value="sync_timeout">Sync timeout</option>
                </select>
              </label>
              <label>
                Sort
                <select value={sort} onChange={(e) => setSort(e.target.value)}>
                  <option value="last_sync">Last sync</option>
                  <option value="device_name">Name</option>
                  <option value="created_at">Registered</option>
                </select>
              </label>
            </div>
            <div className="table-wrap">
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
                  {devices.map((d) => (
                    <tr
                      key={d.device_id}
                      className={selectedId === d.device_id ? 'selected' : ''}
                      onClick={() => openDevice(d.device_id)}
                    >
                      <td>{d.device_name}</td>
                      <td>{d.device_model}</td>
                      <td>
                        <span className={`status status-${d.current_status}`}>
                          <span className="dot" />
                          {statusLabel(d.current_status)}
                        </span>
                      </td>
                      <td>{isOnline(d) ? 'Online' : 'Offline'}</td>
                      <td>{lockedToLabel(d)}</td>
                      <td>{formatTime(d.last_sync)}</td>
                      <td className="mono">{d.unique_id}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!devices.length ? <p className="empty">No devices yet</p> : null}
            </div>
          </section>
        ) : null}

        {view === 'logs' ? (
          <section className="view">
            <div className="table-wrap">
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
                  {logs.map((log) => (
                    <tr key={log.log_id || `${log.timestamp}-${log.action}`}>
                      <td>{formatTime(log.timestamp)}</td>
                      <td>{log.device_name || log.device_id || '—'}</td>
                      <td className="mono">{log.action}</td>
                      <td>{log.details || ''}</td>
                      <td>{log.admin_name || 'system'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!logs.length ? <p className="empty">No audit events</p> : null}
            </div>
          </section>
        ) : null}

        {view === 'settings' ? (
          <section className="view settings">
            <header className="settings-hero">
              <h1>Settings</h1>
              <p className="settings-lead">Device unlock PIN for phones and tablets</p>
            </header>

            <article className="settings-card">
              <table className="settings-table">
                <tbody>
                  <tr>
                    <th scope="row">Status</th>
                    <td>
                      <span className={`settings-status ${pinInfo.is_set ? 'is-on' : 'is-off'}`}>
                        {pinInfo.is_set ? 'Set' : 'Not set'}
                      </span>
                    </td>
                  </tr>
                  <tr>
                    <th scope="row">New PIN</th>
                    <td>
                      <input
                        value={pinInput}
                        onChange={(e) => setPinInput(e.target.value.replace(/\D/g, '').slice(0, 8))}
                        inputMode="numeric"
                        autoComplete="off"
                        placeholder="4–8 digits"
                        maxLength={8}
                      />
                    </td>
                  </tr>
                  <tr>
                    <th scope="row">Actions</th>
                    <td className="settings-table-actions">
                      <button
                        type="button"
                        className="btn btn-primary"
                        disabled={pinInput.length < 4}
                        onClick={async () => {
                          try {
                            await api.setUninstallPin(pinInput);
                            setPinInput('');
                            showToast('PIN saved — devices sync next poll');
                            loadPin();
                          } catch (err) {
                            showToast(err.message, 'error');
                          }
                        }}
                      >
                        Save
                      </button>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        disabled={!pinInfo.is_set}
                        onClick={async () => {
                          try {
                            const res = await api.getUninstallPin(true);
                            setPinInfo(res);
                            if (res.pin) showToast(`PIN: ${res.pin}`);
                          } catch (err) {
                            showToast(err.message, 'error');
                          }
                        }}
                      >
                        Reveal
                      </button>
                      {pinInfo.is_set ? (
                        <button
                          type="button"
                          className="btn btn-ghost settings-clear"
                          onClick={async () => {
                            if (!window.confirm('Clear PIN on all devices?')) return;
                            try {
                              await api.clearUninstallPin();
                              showToast('PIN cleared');
                              loadPin();
                            } catch (err) {
                              showToast(err.message, 'error');
                            }
                          }}
                        >
                          Clear
                        </button>
                      ) : null}
                    </td>
                  </tr>
                </tbody>
              </table>
            </article>
          </section>
        ) : null}
      </main>

      {device && selectedId ? (
        <>
          <div className="backdrop open" onClick={() => { setSelectedId(null); setDevice(null); }} />
          <DeviceDrawer
            device={device}
            apps={apps}
            selected={selectedPkgs}
            setSelected={setSelectedPkgs}
            allowedUrls={allowedUrls}
            setAllowedUrls={setAllowedUrls}
            blockMedia={blockMedia}
            setBlockMedia={setBlockMedia}
            screenToday={screenToday}
            screenWeek={screenWeek}
            onClose={() => { setSelectedId(null); setDevice(null); }}
            onRestrict={restrict}
            onUnlock={unlock}
            onSaveWeb={saveWeb}
            toast={showToast}
          />
        </>
      ) : null}

      {toast ? (
        <Toast key={toast.id} message={toast.message} type={toast.type} onDone={() => setToast(null)} />
      ) : null}
    </div>
  );
}

export default function App() {
  const [authed, setAuthed] = useState(Boolean(getToken()));
  if (!authed) return <Login onLogin={() => setAuthed(true)} />;
  return <Console onLogout={() => setAuthed(false)} />;
}
