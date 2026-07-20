'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import QRCode from 'qrcode';

function useToast() {
  const [toast, setToast] = useState(null);
  const show = useCallback((msg, err = false) => {
    setToast({ msg, err });
    setTimeout(() => setToast(null), 3200);
  }, []);
  return { toast, show };
}

function stateClass(s) {
  return (s || 'disconnected').toLowerCase();
}

function fmtTime(ms) {
  const d = new Date(ms);
  return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function ago(ms) {
  if (!ms) return '—';
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return `${s}s ago`;
  if (s < 3600) return `${Math.round(s / 60)}m ago`;
  return `${Math.round(s / 3600)}h ago`;
}

function fmtCountdown(ms) {
  if (ms <= 0) return 'expired';
  const s = Math.ceil(ms / 1000);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

// A once-per-second clock, only ticking while `active` so idle cards stay still.
function useNow(active) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return undefined;
    setNow(Date.now());
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [active]);
  return now;
}

export default function Dashboard() {
  const [snap, setSnap] = useState({ devices: [], events: [], pairingCodes: [], enrollments: [] });
  const [connected, setConnected] = useState(false);
  const [selectedId, setSelectedId] = useState(null);
  const [origin, setOrigin] = useState('');
  const { toast, show } = useToast();
  const feedRef = useRef(null);
  const requestedStatus = useRef(new Map());

  useEffect(() => {
    setOrigin(window.location.origin);
    const es = new EventSource('/api/stream');
    es.onopen = () => setConnected(true);
    es.onerror = () => setConnected(false);
    es.onmessage = (e) => {
      try {
        setSnap(JSON.parse(e.data));
      } catch {
        /* keep-alive */
      }
    };
    return () => es.close();
  }, []);

  const devices = snap.devices || [];
  const selected = useMemo(
    () => devices.find((d) => d.deviceId === selectedId) || devices[0] || null,
    [devices, selectedId],
  );

  useEffect(() => {
    if (feedRef.current) feedRef.current.scrollTop = feedRef.current.scrollHeight;
  }, [snap.events]);

  // Keep each READY device's status (and thus its SIM list) fresh automatically:
  // pull get_status on first READY, and again whenever the SIM set implied by the
  // heartbeat changes — e.g. right after phone permission is granted on the node.
  useEffect(() => {
    if (!connected) return;
    for (const d of devices) {
      if (!d.online || d.state !== 'READY') continue;
      const subs = (d.heartbeat?.signals || [])
        .map((s) => s.subscriptionId)
        .sort()
        .join(',');
      const sig = `${d.connectedAt}:${subs}`;
      if (requestedStatus.current.get(d.deviceId) === sig) continue;
      requestedStatus.current.set(d.deviceId, sig);
      fetch(`/api/devices/${d.deviceId}/command`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'get_status', payload: {} }),
      }).catch(() => { });
    }
  }, [devices, connected]);

  const wsUrl = origin.replace(/^http/, 'ws') + '/ws';

  const genCode = async (options = {}) => {
    const r = await fetch('/api/pairing', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(options),
    });
    const j = await r.json();
    if (!r.ok) return show(j.error || 'could not mint code', true);
    show(`Pairing code ${j.code} ready — scan the QR or type it`);
  };

  const decideEnrollment = async (id, action) => {
    const r = await fetch(`/api/enrollments/${id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action }),
    });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) show(j.error || `could not ${action}`, true);
    else show(action === 'approve' ? 'Device approved' : 'Device denied');
  };

  const sendCommand = async (type, payload) => {
    if (!selected) return;
    const r = await fetch(`/api/devices/${selected.deviceId}/command`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type, payload }),
    });
    const j = await r.json();
    if (!r.ok) show(j.error || 'command failed', true);
    else show(`Sent ${type}`);
  };

  return (
    <div className="wrap">
      <header className="top">
        <h1>Luno</h1>
        <span className="tag">test backend</span>
        <span style={{ flex: 1 }} />
        <span className={`live ${connected ? '' : 'off'}`}>
          <span className="dot" /> {connected ? 'live' : 'reconnecting…'}
        </span>
      </header>
      <p className="sub">
        A reference Luno-protocol server: pair the Android node, then drive it live over WSS.
      </p>

      <div className="grid">
        <div className="col">
          <ConnectionCard origin={origin} wsUrl={wsUrl} />
          <PairingCard codes={snap.pairingCodes} onGenerate={genCode} />
          <PendingCard enrollments={snap.enrollments} onDecide={decideEnrollment} />
          <DevicesCard devices={devices} selected={selected} onSelect={setSelectedId} />
        </div>

        <div className="col">
          <CommandCard device={selected} onSend={sendCommand} />
          <FeedCard events={snap.events} feedRef={feedRef} devices={devices} />
        </div>
      </div>

      {toast && <div className={`toast ${toast.err ? 'err' : ''}`}>{toast.msg}</div>}
    </div>
  );
}

function ConnectionCard({ origin, wsUrl }) {
  return (
    <div className="card">
      <h2>Connection details</h2>
      <div className="kv">
        <span className="k">Backend URL</span>
        <span className="v">{origin || '…'}</span>
      </div>
      <div className="kv">
        <span className="k">Enroll endpoint</span>
        <span className="v">{origin ? `${origin}/enroll` : '…'}</span>
      </div>
      <div className="kv">
        <span className="k">WebSocket</span>
        <span className="v">{wsUrl || '…'}</span>
      </div>
      <p className="hint" style={{ marginTop: 10 }}>
        In the app&apos;s pairing screen enter the <b>Backend URL</b> above (must be https) plus a
        pairing code. Locally, put an https tunnel (ngrok / cloudflared) in front so the node gets
        wss.
      </p>
    </div>
  );
}

const EXPIRY_OPTIONS = [
  { label: '5 min', ms: 5 * 60 * 1000 },
  { label: '10 min', ms: 10 * 60 * 1000 },
  { label: '1 hour', ms: 60 * 60 * 1000 },
  { label: 'Never', ms: null },
];

const SEAT_OPTIONS = [
  { label: '1 device', n: 1 },
  { label: '5 devices', n: 5 },
  { label: 'Unlimited', n: null },
];

// Renders the SDK-minted qrUri (a versioned luno://pair payload) as a scannable
// image. We encode the exact string @luno-oss/core handed us — the dashboard never
// builds a pairing payload of its own.
function QrImage({ uri }) {
  const [src, setSrc] = useState(null);
  useEffect(() => {
    let alive = true;
    if (!uri) {
      setSrc(null);
      return undefined;
    }
    QRCode.toDataURL(uri, { margin: 1, width: 220, errorCorrectionLevel: 'M' })
      .then((url) => alive && setSrc(url))
      .catch(() => alive && setSrc(null));
    return () => {
      alive = false;
    };
  }, [uri]);
  if (!src) return null;
  return <img className="qr" src={src} alt="Scan to pair" width={200} height={200} />;
}

function PairingCard({ codes, onGenerate }) {
  const active = !!(codes && codes.length > 0);
  const now = useNow(active);

  const [expiryIdx, setExpiryIdx] = useState(1);
  const [seatIdx, setSeatIdx] = useState(0);
  const [requireApproval, setRequireApproval] = useState(false);

  const generate = () =>
    onGenerate({
      expiresInMs: EXPIRY_OPTIONS[expiryIdx].ms,
      maxEnrollments: SEAT_OPTIONS[seatIdx].n,
      requireApproval,
    });

  return (
    <div className="card">
      <h2>Pairing</h2>

      <div className="row">
        <label className="field" style={{ flex: 1 }}>
          <span>Expiry</span>
          <select value={expiryIdx} onChange={(e) => setExpiryIdx(Number(e.target.value))}>
            {EXPIRY_OPTIONS.map((o, i) => (
              <option key={o.label} value={i}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label className="field" style={{ flex: 1 }}>
          <span>Seats</span>
          <select value={seatIdx} onChange={(e) => setSeatIdx(Number(e.target.value))}>
            {SEAT_OPTIONS.map((o, i) => (
              <option key={o.label} value={i}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label
        className="field"
        style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 12 }}
      >
        <input
          type="checkbox"
          checked={requireApproval}
          onChange={(e) => setRequireApproval(e.target.checked)}
          style={{ width: 'auto' }}
        />
        <span style={{ margin: 0 }}>Require approval before the device enrolls</span>
      </label>

      {active ? (
        codes.map((c) => {
          const remaining = c.expiresAt === null ? null : c.expiresAt - now;
          const expired = remaining !== null && remaining <= 0;
          return (
            <div key={c.code} className="pairing-live">
              {c.qrUri && !expired && (
                <div className="qr-wrap">
                  <QrImage uri={c.qrUri} />
                </div>
              )}
              <div className="pcode">{c.code}</div>
              <p className="hint">
                {expired
                  ? 'Expired — generate a new one.'
                  : `Scan the QR or type the code${c.requireApproval ? ' (needs approval)' : ''
                  } · ${remaining === null ? 'never expires' : `expires in ${fmtCountdown(remaining)}`}.`}
              </p>
            </div>
          );
        })
      ) : (
        <p className="hint" style={{ marginBottom: 10 }}>
          No active code. Generate one, then scan the QR from the node&apos;s pairing screen —
          or type the code by hand.
        </p>
      )}
      <button className="primary" onClick={generate} style={{ width: '100%' }}>
        Generate pairing code
      </button>
    </div>
  );
}

// Only shown when a session has requireApproval and a device is waiting. The node
// sits polling /enroll/status until an operator clicks Approve or Deny here.
function PendingCard({ enrollments, onDecide }) {
  const pending = enrollments || [];
  if (pending.length === 0) return null;
  return (
    <div className="card">
      <h2>Awaiting approval ({pending.length})</h2>
      <div className="col" style={{ gap: 10 }}>
        {pending.map((e) => (
          <div key={e.id} className="device">
            <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <span className="id">{e.info?.manufacturer} {e.info?.model}</span>
              <span className="badge disconnected">
                <span className="dot" /> pending
              </span>
            </div>
            <div className="meta">
              SDK {e.info?.androidSdk} · v{e.info?.appVersion} · requested {ago(e.createdAt)}
            </div>
            <div className="row" style={{ marginTop: 10 }}>
              <button className="primary sm" onClick={() => onDecide(e.id, 'approve')}>
                Approve
              </button>
              <button className="sm danger" onClick={() => onDecide(e.id, 'deny')}>
                Deny
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function DevicesCard({ devices, selected, onSelect }) {
  return (
    <div className="card">
      <h2>Paired devices ({devices.length})</h2>
      {devices.length === 0 ? (
        <p className="empty">No devices yet. Pair one to begin.</p>
      ) : (
        <div className="col" style={{ gap: 10 }}>
          {devices.map((d) => (
            <div
              key={d.deviceId}
              className={`device ${selected && selected.deviceId === d.deviceId ? 'active' : ''}`}
              onClick={() => onSelect(d.deviceId)}
            >
              <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="id">{d.deviceId}</span>
                <span className={`badge ${stateClass(d.state)}`}>
                  <span className="dot" /> {d.state}
                </span>
              </div>
              {d.info && (
                <div className="meta">
                  {d.info.manufacturer} {d.info.model} · SDK {d.info.androidSdk} · v{d.info.appVersion}
                </div>
              )}
              {d.heartbeat && (
                <div className="stat-row">
                  <span className="stat">
                    <span>battery </span>
                    <b>{d.heartbeat.battery ?? '—'}%</b>
                  </span>
                  <span className="stat">
                    <span>queue </span>
                    <b>{d.heartbeat.queueDepth ?? 0}</b>
                  </span>
                  <span className="stat">
                    <span>signal </span>
                    <b>
                      {d.heartbeat.signals && d.heartbeat.signals.length
                        ? d.heartbeat.signals.map((s) => `${s.level}/4`).join(' ')
                        : '—'}
                    </b>
                  </span>
                  <span className="stat">
                    <span>seen </span>
                    <b>{ago(d.lastSeen)}</b>
                  </span>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function CommandCard({ device, onSend }) {
  const [to, setTo] = useState('');
  const [body, setBody] = useState('Hello from the Luno test backend 👋');
  const [deliveryReport, setDeliveryReport] = useState(true);
  const [subId, setSubId] = useState('');
  const [hbSec, setHbSec] = useState('30');
  const [rate, setRate] = useState('');
  const [allow, setAllow] = useState('');

  const online = device && device.online;
  const ready = device && device.state === 'READY';
  const sims = (device && device.status && device.status.sims) || [];

  if (!device) {
    return (
      <div className="card">
        <h2>Commands</h2>
        <p className="empty">Select a device to send commands.</p>
      </div>
    );
  }

  const sendSms = () => {
    if (!to.trim()) return;
    onSend('send_sms', {
      to: to.trim(),
      body,
      deliveryReport,
      ...(subId.trim() ? { subscriptionId: Number(subId.trim()) } : {}),
      ref: `dash-${Date.now()}`,
    });
  };

  const applyConfig = () => {
    const payload = {};
    if (hbSec.trim()) payload.heartbeatSec = Number(hbSec.trim());
    if (rate.trim()) payload.rateLimitPerMinute = Number(rate.trim());
    if (allow.trim()) payload.allowlist = allow.split(',').map((s) => s.trim()).filter(Boolean);
    onSend('config_update', payload);
  };

  return (
    <div className="card">
      <h2>
        Commands · <span className="mono">{device.deviceId}</span>{' '}
        <span className={`badge ${stateClass(device.state)}`} style={{ marginLeft: 6 }}>
          <span className="dot" /> {device.state}
        </span>
      </h2>

      {!ready && (
        <p className="hint" style={{ marginBottom: 12 }}>
          {online ? 'Handshaking…' : 'Device offline.'} Commands need a READY connection.
        </p>
      )}

      <label className="field">
        <span>send_sms · to</span>
        <input value={to} onChange={(e) => setTo(e.target.value)} placeholder="+15551234567" />
      </label>
      <label className="field">
        <span>body</span>
        <textarea value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      <div className="row">
        <label className="field" style={{ flex: 1 }}>
          <span>SIM</span>
          {sims.length > 0 ? (
            <select value={subId} onChange={(e) => setSubId(e.target.value)}>
              <option value="">Default SIM</option>
              {sims.map((s) => (
                <option key={s.subscriptionId} value={s.subscriptionId}>
                  {s.carrierName || s.displayName || 'SIM'} · slot {s.slotIndex} (sub {s.subscriptionId})
                </option>
              ))}
            </select>
          ) : (
            <input value={subId} onChange={(e) => setSubId(e.target.value)} placeholder="default SIM" />
          )}
        </label>
        <label className="field" style={{ display: 'flex', alignItems: 'center', gap: 8, alignSelf: 'end' }}>
          <input
            type="checkbox"
            checked={deliveryReport}
            onChange={(e) => setDeliveryReport(e.target.checked)}
            style={{ width: 'auto' }}
          />
          <span style={{ margin: 0 }}>delivery report</span>
        </label>
      </div>
      {sims.length === 0 && (
        <p className="hint" style={{ marginTop: -4, marginBottom: 10 }}>
          Run <b>get_status</b> below to list this device&apos;s SIMs here.
        </p>
      )}
      <button className="primary" onClick={sendSms} disabled={!ready} style={{ width: '100%' }}>
        Send SMS
      </button>

      <hr className="sep" />

      <div className="row">
        <button className="sm" onClick={() => onSend('get_status', {})} disabled={!ready}>
          get_status
        </button>
        <button className="sm danger" onClick={() => onSend('revoke', {})} disabled={!online}>
          revoke
        </button>
        <button className="sm danger" onClick={() => onSend('wipe', {})} disabled={!online}>
          wipe
        </button>
      </div>

      <hr className="sep" />

      <h2 style={{ marginTop: 0 }}>config_update</h2>
      <div className="row">
        <label className="field" style={{ flex: 1 }}>
          <span>heartbeat sec</span>
          <input value={hbSec} onChange={(e) => setHbSec(e.target.value)} />
        </label>
        <label className="field" style={{ flex: 1 }}>
          <span>rate / min</span>
          <input value={rate} onChange={(e) => setRate(e.target.value)} placeholder="unset" />
        </label>
      </div>
      <label className="field">
        <span>allowlist (comma-separated, optional)</span>
        <input value={allow} onChange={(e) => setAllow(e.target.value)} placeholder="+15551234567, +15559876543" />
      </label>
      <button onClick={applyConfig} disabled={!ready} style={{ width: '100%' }}>
        Apply config
      </button>
    </div>
  );
}

function FeedCard({ events, feedRef, devices }) {
  const arrow = (dir) => (dir === 'in' ? '←' : dir === 'out' ? '→' : '·');
  const single = devices.length <= 1;
  return (
    <div className="card">
      <h2>Live protocol feed</h2>
      <div className="feed" ref={feedRef}>
        {(!events || events.length === 0) && <p className="empty">Frames will appear here in real time.</p>}
        {events &&
          events.map((e) => (
            <div key={e.id} className={`evt dir-${e.direction}`}>
              <span className="time">{fmtTime(e.at)}</span>
              <span className="arrow">{arrow(e.direction)}</span>
              <span>
                <span className="type">{e.type}</span>{' '}
                <span className="payload">
                  {!single ? `${e.deviceId?.slice(0, 10)} ` : ''}
                  {e.payload ? JSON.stringify(e.payload) : ''}
                </span>
              </span>
              <span className="acked">{e.acked ? '✓ acked' : ''}</span>
            </div>
          ))}
      </div>
      <p className="hint" style={{ marginTop: 10 }}>
        <span style={{ color: 'var(--accent)' }}>→</span> backend → node ·{' '}
        <span style={{ color: 'var(--green)' }}>←</span> node → backend (auto-acked)
      </p>
    </div>
  );
}
