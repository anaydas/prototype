/**
 * app.js — SSE client for GitHub Actions-style log viewer
 *
 * Reads ?runId=<id> from the URL and connects to /logs/<id>/stream.
 * Falls back gracefully if no runId is present (e.g., on the home page).
 */

const params = new URLSearchParams(window.location.search);
const RUN_ID  = params.get('runId');

if (!RUN_ID) {
  console.log('[SSE] No runId in URL — viewer not activated.');
} else {
  initViewer(RUN_ID);
}

function initViewer(runId) {
  const LOG_CONTAINER   = document.getElementById('logContainer');
  const LOG_PLACEHOLDER = document.getElementById('logPlaceholder');
  const STATUS_BADGE    = document.getElementById('statusBadge');
  const STATUS_TEXT     = document.getElementById('statusText');
  const STATUS_DOT      = STATUS_BADGE.querySelector('.status-dot');
  const JOB_ICON        = document.getElementById('jobIcon');
  const AUTO_SCROLL_CHK = document.getElementById('autoScrollToggle');
  const CLEAR_BTN       = document.getElementById('clearBtn');
  const WORKFLOW_NAME   = document.getElementById('workflowName');
  const JOB_NAME        = document.getElementById('jobName');
  const LOG_PANEL_TITLE = document.getElementById('logPanelTitle');
  const RUN_ID_LABEL    = document.getElementById('runIdLabel');

  if (RUN_ID_LABEL) RUN_ID_LABEL.textContent = runId;
  document.title = `Run #${runId} · GitHub Actions`;

  let lineNumber = 1;
  let autoScroll = true;

  AUTO_SCROLL_CHK.addEventListener('change', () => {
    autoScroll = AUTO_SCROLL_CHK.checked;
  });

  CLEAR_BTN.addEventListener('click', () => {
    document.querySelectorAll('.log-row, .loop-divider').forEach(el => el.remove());
    lineNumber = 1;
  });

  function scrollBottom() {
    if (autoScroll) LOG_CONTAINER.scrollTop = LOG_CONTAINER.scrollHeight;
  }

  function hidePlaceholder() {
    if (LOG_PLACEHOLDER) LOG_PLACEHOLDER.style.display = 'none';
  }

  function appendLoopDivider(loop) {
    if (loop <= 1) return;
    const div = document.createElement('div');
    div.className = 'loop-divider';
    div.textContent = `── replaying from start ──`;
    LOG_CONTAINER.appendChild(div);
    scrollBottom();
  }

  function appendLogRow(data) {
    const { line, type } = data;
    const row  = document.createElement('div');
    row.className = `log-row ${type} new`;

    const num  = document.createElement('span');
    num.className   = 'log-num';
    num.textContent = lineNumber++;

    const text = document.createElement('span');
    text.className   = 'log-text';
    text.textContent = line;

    row.appendChild(num);
    row.appendChild(text);
    LOG_CONTAINER.appendChild(row);
    row.addEventListener('animationend', () => row.classList.remove('new'), { once: true });
    scrollBottom();
  }

  function setWorkflowMeta(runId) {
    const names = {
      '1842': { workflow: 'CI/CD Pipeline',     job: 'build-and-test' },
      '1843': { workflow: 'Docker Build & Push', job: 'docker-build'   },
    };
    const meta = names[runId] || { workflow: `Run #${runId}`, job: 'job' };
    if (WORKFLOW_NAME)   WORKFLOW_NAME.textContent   = meta.workflow;
    if (JOB_NAME)        JOB_NAME.textContent        = meta.job;
    if (LOG_PANEL_TITLE) LOG_PANEL_TITLE.textContent = meta.job;
  }

  function setStatus(state) {
    STATUS_DOT.className   = 'status-dot';
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

  function connect() {
    const url       = `/logs/${runId}/stream`;
    const evtSource = new EventSource(url);
    setStatus('running');
    console.log(`[SSE] Connecting to ${url}`);

    evtSource.addEventListener('start', (e) => {
      const { loop, runId: rid } = JSON.parse(e.data);

      if (loop > 1) {
        document.querySelectorAll('.log-row').forEach(el => el.remove());
        lineNumber = 1;
        appendLoopDivider(loop);
      }

      hidePlaceholder();
      setWorkflowMeta(rid || runId);
    });

    evtSource.addEventListener('log', (e) => {
      appendLogRow(JSON.parse(e.data));
    });

    evtSource.onerror = () => {
      setStatus('error');
      console.warn('[SSE] Connection lost. Retrying in 3s…');
      evtSource.close();
      setTimeout(connect, 3000);
    };
  }

  connect();
}
