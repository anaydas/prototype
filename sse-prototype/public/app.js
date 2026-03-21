/**
 * app.js — SSE client for GitHub Actions-style log viewer
 * Connects to /logs/stream, renders log lines live.
 */

const LOG_CONTAINER   = document.getElementById('logContainer');
const LOG_PLACEHOLDER = document.getElementById('logPlaceholder');
const STATUS_BADGE    = document.getElementById('statusBadge');
const STATUS_TEXT     = document.getElementById('statusText');
const STATUS_DOT      = STATUS_BADGE.querySelector('.status-dot');
const JOB_ICON        = document.getElementById('jobIcon');
const LOOP_BADGE      = document.getElementById('loopBadge');
const LOOP_COUNT_EL   = document.getElementById('loopCount');
const AUTO_SCROLL_CHK = document.getElementById('autoScrollToggle');
const CLEAR_BTN       = document.getElementById('clearBtn');

let lineNumber = 1;
let autoScroll = true;

AUTO_SCROLL_CHK.addEventListener('change', () => {
  autoScroll = AUTO_SCROLL_CHK.checked;
});

CLEAR_BTN.addEventListener('click', () => {
  // Remove all log rows (keep placeholder hidden)
  document.querySelectorAll('.log-row, .loop-divider').forEach(el => el.remove());
  lineNumber = 1;
});

// ── Helpers ─────────────────────────────────────────────
function scrollBottom() {
  if (autoScroll) {
    LOG_CONTAINER.scrollTop = LOG_CONTAINER.scrollHeight;
  }
}

function hidePlaceholder() {
  if (LOG_PLACEHOLDER) LOG_PLACEHOLDER.style.display = 'none';
}

function appendLoopDivider(loop) {
  if (loop <= 1) return; // skip divider for first run
  const div = document.createElement('div');
  div.className = 'loop-divider';
  div.textContent = `── Loop ${loop} restarted ──`;
  LOG_CONTAINER.appendChild(div);
  scrollBottom();
}

function appendLogRow(data) {
  const { line, type, index } = data;

  const row = document.createElement('div');
  row.className = `log-row ${type} new`;

  const num = document.createElement('span');
  num.className = 'log-num';
  num.textContent = lineNumber++;

  const text = document.createElement('span');
  text.className = 'log-text';
  text.textContent = line;

  row.appendChild(num);
  row.appendChild(text);
  LOG_CONTAINER.appendChild(row);

  // Remove animation class after it plays so hover doesn't re-trigger
  row.addEventListener('animationend', () => row.classList.remove('new'), { once: true });

  scrollBottom();
}

function setStatus(state) {
  STATUS_DOT.className = 'status-dot';
  STATUS_BADGE.className = 'status-badge';

  if (state === 'running') {
    STATUS_DOT.classList.add('spinning');
    STATUS_TEXT.textContent = 'In progress';
    JOB_ICON.innerHTML = `<svg class="spin-anim" viewBox="0 0 16 16" width="16" height="16" fill="none">
      <circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="2"
        stroke-dasharray="22 22" stroke-linecap="round"/>
    </svg>`;
  } else if (state === 'error') {
    STATUS_BADGE.classList.add('error');
    STATUS_TEXT.textContent = 'Failed';
    JOB_ICON.innerHTML = `<svg viewBox="0 0 16 16" width="16" height="16" fill="currentColor" style="color:#f85149">
      <path d="M8 0a8 8 0 100 16A8 8 0 008 0zm3.78 10.72L10.72 11.78 8 9.06l-2.72 2.72L4.22 10.72 6.94 8 4.22 5.28 5.28 4.22 8 6.94l2.72-2.72 1.06 1.06L9.06 8l2.72 2.72z"/>
    </svg>`;
  }
}

// ── SSE Connection ───────────────────────────────────────
function connect() {
  const evtSource = new EventSource('/logs/stream');
  setStatus('running');

  evtSource.addEventListener('start', (e) => {
    const { loop } = JSON.parse(e.data);

    // Clear logs on restart (loop > 1)
    if (loop > 1) {
      document.querySelectorAll('.log-row').forEach(el => el.remove());
      lineNumber = 1;
      appendLoopDivider(loop);
    }

    hidePlaceholder();

    // Update loop badge
    LOOP_BADGE.style.display = 'inline-flex';
    LOOP_COUNT_EL.textContent = loop;
  });

  evtSource.addEventListener('log', (e) => {
    const data = JSON.parse(e.data);
    appendLogRow(data);
  });

  evtSource.onerror = () => {
    setStatus('error');
    console.warn('[SSE] Connection lost. Reconnecting in 3s…');
    evtSource.close();
    setTimeout(connect, 3000);
  };
}

// Kick off
connect();
