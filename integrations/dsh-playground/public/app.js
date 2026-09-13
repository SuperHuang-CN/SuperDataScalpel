const $ = id => document.getElementById(id);
let syncVersion = 0;
let user, current, state, socket, reconnect, refreshTimer, history = [], liveText = '', attempts = 0, filesPath = '', sessionOffset = 0, pendingSend;
function node(tag, text, className) { const el = document.createElement(tag); if (text !== undefined) el.textContent = text; if (className) el.className = className; return el; }
function toast(text) { $('toast').textContent = text; $('toast').hidden = false; setTimeout(() => $('toast').hidden = true, 4000); }
function error(e) { $('error').textContent = e.message; $('error').hidden = false; toast(e.message); }
async function api(path, body) {
  const response = await fetch(`/api${path}`, { credentials: 'same-origin', ...(body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) });
  const data = await response.json();
  if (!response.ok) { if (response.status === 401 && path !== '/login') showLogin(); throw Object.assign(new Error(data.detail ?? data.code ?? '请求失败'), { code: data.code }); }
  return data;
}
const bridge = (path, body) => api(`/bridge${path}`, body);
function disconnect() { clearTimeout(reconnect); clearTimeout(refreshTimer); if (socket) { socket.onclose = null; socket.close(); socket = null; } }
function showLogin() { disconnect(); user = null; current = null; state = null; history = []; liveText = ''; $('app').hidden = true; $('login-page').hidden = false; }
async function enter(name) {
  user = name; $('login-page').hidden = true; $('app').hidden = false; $('user-badge').textContent = `${name === 'alice' ? 'A' : 'B'} · ${name}`;
  $('workspace-title').textContent = `${name} 的工作区`;
  const workspace = await bridge('/workspaces/actions/ensure', {}); $('workspace-path').textContent = workspace.path;
  await Promise.all([loadSessions(), loadFiles('')]);
}
$('login-form').onsubmit = async event => {
  event.preventDefault(); const f = new FormData(event.target), button = event.target.querySelector('button'); button.disabled = true;
  try { const result = await api('/login', { username: f.get('username'), password: f.get('password') }); event.target.reset(); await enter(result.user); } catch(e) { toast(e.message); } finally { button.disabled = false; }
};
$('logout').onclick = async () => { try { await api('/logout', {}); showLogin(); location.reload(); } catch(e) { error(e); } };
async function loadSessions(append = false) {
  const expectedUser = user;
  const data = await bridge(`/sessions?offset=${append ? sessionOffset : 0}&limit=20`);
  if (expectedUser !== user) return;
  if (!append) { $('sessions').replaceChildren(); sessionOffset = 0; }
  for (const value of data.items) {
    const button = node('button', `对话 ${value.sessionId.slice(0, 8)}`, `session${current === value.sessionId ? ' active' : ''}`);
    button.dataset.id = value.sessionId;
    button.append(node('small', `${new Date(value.createdAt).toLocaleString()} · ${value.runtimeState}`));
    button.onclick = () => selectSession(value.sessionId).catch(error); $('sessions').append(button);
  }
  sessionOffset += data.items.length; $('more-sessions').hidden = !data.hasMore;
  if (!sessionOffset) $('sessions').append(node('div', '还没有对话，点击上方新建。', 'empty'));
}
$('refresh-sessions').onclick = () => loadSessions().catch(error);
$('more-sessions').onclick = () => loadSessions(true).catch(error);
$('new-session').onclick = async () => {
  $('new-session').disabled = true;
  try { const value = await bridge('/sessions', { clientSessionId: crypto.randomUUID() }); await selectSession(value.sessionId); await loadSessions(); }
  catch(e) { error(e); } finally { $('new-session').disabled = false; }
};
async function selectSession(id) {
  disconnect(); current = id; state = null; history = []; liveText = ''; pendingSend = null; $('questions').replaceChildren(); $('error').hidden = true;
  $('session-title').textContent = `对话 ${id.slice(0, 8)}`;
  for (const el of $('sessions').children) el.classList.toggle('active', el.dataset.id === id);
  await sync(id); if (current === id) connect(id);
}
async function sync(id) {
  const version = ++syncVersion;
  const [snapshot, messages] = await Promise.all([bridge(`/sessions/${id}`), allMessages(id)]);
  if (current !== id || version !== syncVersion) return;
  state = snapshot; history = messages; if (state.runtimeState !== 'RUNNING') liveText = '';
  renderHistory(); renderState(); renderQuestions();
}
async function allMessages(id) {
  const result = []; let offset = 0;
  while (true) { const value = await bridge(`/sessions/${id}/messages?offset=${offset}&limit=200`); result.push(...value.items); if (!value.hasMore) return result; offset += value.items.length; }
}
function renderState() {
  const busy = ['RUNNING', 'WAITING_FOR_INPUT', 'CANCELLING'].includes(state?.runtimeState);
  const labels = { IDLE: '可发送消息', UNLOADED: '会话未加载，请点击恢复会话', RUNNING: '正在执行', WAITING_FOR_INPUT: '等待你的回答', CANCELLING: '正在停止' };
  $('session-state').textContent = `${labels[state?.runtimeState] ?? '读取状态中'}${state?.lastOutcome ? ` · ${state.lastOutcome}` : ''}`;
  $('resume').disabled = !state || state.runtimeState !== 'UNLOADED'; $('cancel').disabled = !busy || state.runtimeState === 'CANCELLING';
  $('send').disabled = !state || state.runtimeState !== 'IDLE';
}
function contentText(content) {
  return content.map(b => b.type === 'text' ? b.text : JSON.stringify(b, null, 2)).join('\n');
}
function renderHistory() {
  const target = $('transcript'), nearBottom = target.scrollHeight - target.scrollTop - target.clientHeight < 150;
  target.replaceChildren();
  const calls = new Map();
  for (const item of history) {
    const box = node('div', undefined, `message ${item.role}`); box.append(node('div', item.role === 'user' ? user : item.role === 'tool' ? '工具结果' : 'DSH', 'role'));
    for (const block of item.content) {
      if (block.type === 'text') box.append(node('div', block.text, 'body'));
      if (block.type === 'tool-call') {
        calls.set(block.id, block.name);
        const detail = node('details'); detail.append(node('summary', `调用 ${block.name}`), node('pre', pretty(block.arguments), 'body')); box.append(detail);
      }
      if (block.type === 'tool-result') {
        const detail = node('details'); detail.append(node('summary', `${block.isError ? '未完成' : '已返回'} · ${calls.get(block.toolCallId) ?? '工具'}`));
        detail.append(node('pre', (block.content ?? []).map(b => b.type === 'text' ? pretty(b.text) : JSON.stringify(b)).join('\n'), 'body')); box.append(detail);
      }
    }
    target.append(box);
  }
  if (liveText) { const box = node('div', undefined, 'message live'); box.append(node('div', 'DSH · 生成中', 'role'), node('div', liveText, 'body')); target.append(box); }
  if (!history.length && !liveText) target.append(node('div', '对话已准备好，可以发送消息。', 'empty'));
  if (nearBottom || history.length < 3) target.scrollTop = target.scrollHeight;
}
function pretty(value) { try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return String(value); } }
function scheduleSync(id) { clearTimeout(refreshTimer); refreshTimer = setTimeout(() => sync(id).catch(error), 250); }
function connect(id) {
  if (current !== id || !user) return;
  const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/events?sessionId=${id}`); socket = ws;
  $('connection').textContent = '连接实时事件中…';
  ws.onopen = () => { attempts = 0; $('connection').textContent = '实时事件已连接'; sync(id).catch(error); };
  ws.onmessage = ({ data }) => {
    if (current !== id) return;
    const event = JSON.parse(data);
    if (event.type === 'session.snapshot') { state = event.data; renderState(); renderQuestions(); }
    if (event.type === 'assistant.started') { liveText = ''; }
    if (event.type === 'assistant.delta') { liveText += event.data.text; renderHistory(); }
    if (event.type === 'assistant.completed') { liveText = ''; scheduleSync(id); }
    if (event.type === 'tool.started') $('connection').textContent = `执行工具：${event.data.name}`;
    if (event.type === 'run.failed' || event.type === 'session.persistence-failed') error(new Error(event.data.detail ?? event.data.code ?? '执行失败，请检查状态。'));
    if (event.type.startsWith('run.') || event.type.startsWith('interaction.') || event.type === 'tool.completed' || event.type === 'message.accepted') scheduleSync(id);
    if (event.type.startsWith('run.')) { $('connection').textContent = '实时事件已连接'; loadFiles(filesPath).catch(error); loadSessions().catch(error); }
  };
  ws.onclose = () => { if (current !== id || !user) return; $('connection').textContent = '连接中断，正在重连并同步历史…'; reconnect = setTimeout(() => connect(id), Math.min(1000 * 2 ** attempts++, 15000)); };
  ws.onerror = () => { $('connection').textContent = '实时连接不可用，正在重试'; };
}
$('message-form').onsubmit = async event => {
  event.preventDefault(); const text = $('message').value.trim(), id = current;
  if (!text || !id || state?.runtimeState !== 'IDLE') return;
  if (!pendingSend || pendingSend.text !== text || pendingSend.session !== id) pendingSend = { text, session: id, clientMessageId: crypto.randomUUID() };
  $('send').disabled = true; $('error').hidden = true;
  try { await bridge(`/sessions/${id}/messages`, { clientMessageId: pendingSend.clientMessageId, text }); if (current === id) { $('message').value = ''; pendingSend = null; await sync(id); } }
  catch(e) { error(e); await sync(id).catch(() => {}); }
};
$('message').onkeydown = e => { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); $('message-form').requestSubmit(); } };
$('resume').onclick = async () => { const id = current; try { await bridge(`/sessions/${id}/actions/resume`, {}); await sync(id); } catch(e) { error(e); } };
$('cancel').onclick = async () => { const id = current; try { await bridge(`/sessions/${id}/actions/cancel`, {}); await sync(id); } catch(e) { error(e); } };
for (const button of document.querySelectorAll('[data-prompt]')) button.onclick = () => { $('message').value = button.dataset.prompt; $('message').focus(); };
function renderQuestions() {
  const pending = state?.pendingInteractions ?? [], target = $('questions');
  if (target.dataset.id === pending[0]?.interactionId) return;
  target.replaceChildren(); target.dataset.id = pending[0]?.interactionId ?? '';
  for (const interaction of pending) {
    const form = node('form', undefined, 'question'); const answers = [];
    for (const q of interaction.questions) {
      form.append(node('h3', q.question ?? q.title ?? q.id)); const controls = [];
      for (const option of q.options ?? []) { const label = node('label'), input = node('input'); input.type = q.multiSelect ? 'checkbox' : 'radio'; input.name = q.id; input.value = option.label; label.append(input, node('span', option.label)); form.append(label); controls.push(input); }
      const custom = node('input'); custom.placeholder = '也可以输入自己的回答'; custom.setAttribute('aria-label', q.question ?? '自由回答'); custom.oninput = () => { if (custom.value && !q.multiSelect) controls.forEach(c => c.checked = false); }; controls.forEach(c => c.onchange = () => { if (!q.multiSelect) custom.value = ''; }); form.append(custom);
      answers.push(() => ({ id: q.id, selected: controls.filter(c => c.checked).map(c => c.value), ...(custom.value.trim() ? { custom: custom.value.trim() } : {}) }));
    }
    const submit = node('button', '提交回答', 'primary'); submit.type = 'submit'; form.append(submit);
    form.onsubmit = async e => { e.preventDefault(); const id = current; submit.disabled = true; try { await bridge(`/sessions/${id}/interactions/${interaction.interactionId}/actions/respond`, { answers: answers.map(f => f()) }); await sync(id); } catch(e) { error(e); submit.disabled = false; } };
    target.append(form);
  }
}
async function loadFiles(path) {
  const expectedUser = user, data = await bridge(`/files?path=${encodeURIComponent(path)}`); if (expectedUser !== user) return;
  if (data.kind === 'file') { $('file-editor').hidden = false; $('file-name').value = data.path; $('file-text').value = data.text; return; }
  filesPath = path; $('file-path').textContent = `/${path}`; $('files').replaceChildren();
  for (const entry of data.entries) { const button = node('button', `${entry.kind === 'directory' ? '▸' : '·'} ${entry.name}`, 'file'); button.onclick = () => loadFiles(entry.path).catch(error); $('files').append(button); }
  if (!data.entries.length) $('files').append(node('div', '当前目录没有文件。', 'empty'));
}
$('refresh-files').onclick = () => loadFiles(filesPath).catch(error);
$('file-up').onclick = () => loadFiles(filesPath.split('/').slice(0, -1).join('/')).catch(error);
$('file-new').onclick = () => { $('file-editor').hidden = false; $('file-name').value = filesPath ? `${filesPath}/` : ''; $('file-text').value = ''; $('file-name').focus(); };
$('file-save').onclick = async () => { try { await bridge('/files', { path: $('file-name').value, text: $('file-text').value }); toast('个人文件已保存'); await loadFiles(filesPath); } catch(e) { error(e); } };
api('/me').then(value => enter(value.user)).catch(e => { showLogin(); if (e.code !== 'LOGIN_REQUIRED') toast(e.message); });
