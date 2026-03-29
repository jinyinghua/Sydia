/**
 * Sydia - 前端交互逻辑
 * 连接 /v1/... API, 实现聊天、任务管理、截图刷新、日志轮询
 */

// ═══ DOM 引用 ═══
const chatMessages = document.getElementById('chatMessages');
const chatForm = document.getElementById('chatForm');
const chatInput = document.getElementById('chatInput');
const chatSend = document.getElementById('chatSend');
const statusBadge = document.getElementById('statusBadge');
const currentUrlEl = document.getElementById('currentUrl');
const clockEl = document.getElementById('clock');
const screenshotImg = document.getElementById('screenshotImg');
const viewerPlaceholder = document.getElementById('viewerPlaceholder');
const taskList = document.getElementById('taskList');
const logArea = document.getElementById('logArea');
const addTaskBtn = document.getElementById('addTaskBtn');
const refreshScreenshot = document.getElementById('refreshScreenshot');

// Modal
const modalOverlay = document.getElementById('modalOverlay');
const modalTitle = document.getElementById('modalTitle');
const modalDetail = document.getElementById('modalDetail');
const modalCancel = document.getElementById('modalCancel');
const modalConfirm = document.getElementById('modalConfirm');

// ═══ 时钟 ═══
function updateClock() {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    const s = String(now.getSeconds()).padStart(2, '0');
    clockEl.textContent = `${h}:${m}:${s}`;
}
setInterval(updateClock, 1000);
updateClock();

// ═══ 聊天功能 (SSE 流式) ═══

function appendMsg(role, text) {
    const div = document.createElement('div');
    div.className = `msg ${role}`;
    div.textContent = text;
    chatMessages.appendChild(div);
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return div;
}

chatForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const text = chatInput.value.trim();
    if (!text) return;

    appendMsg('user', text);
    chatInput.value = '';
    chatSend.disabled = true;

    // 创建 AI 回复占位
    const aiDiv = appendMsg('assistant', '');

    try {
        const res = await fetch('/v1/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: text }),
        });

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { value, done } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const data = line.slice(6);
                    if (data === '[DONE]') break;
                    aiDiv.textContent += data;
                    chatMessages.scrollTop = chatMessages.scrollHeight;
                }
            }
        }
    } catch (err) {
        aiDiv.textContent = `[网络错误] ${err.message}`;
    }

    chatSend.disabled = false;
    chatInput.focus();

    // 聊天后刷新状态
    setTimeout(pollState, 500);
});

// ═══ 系统状态轮询 ═══

async function pollState() {
    try {
        const res = await fetch('/v1/state');
        const data = await res.json();

        // 状态徽章
        const status = data.execution_status || 'IDLE';
        statusBadge.textContent = status;
        if (status.includes('执行中')) {
            statusBadge.className = 'status-badge running';
        } else {
            statusBadge.className = 'status-badge';
        }

        // 当前 URL
        currentUrlEl.textContent = data.current_url || '—';

        // 任务池
        renderTasks(data.task_pool || []);

        // 日志
        renderLogs(data.recent_logs || []);

    } catch (err) {
        console.error('pollState error:', err);
    }
}

// ═══ 任务渲染 ═══

function renderTasks(tasks) {
    if (!tasks.length) {
        taskList.innerHTML = '<div class="task-empty">暂无任务</div>';
        return;
    }

    taskList.innerHTML = '';
    for (const t of tasks) {
        const div = document.createElement('div');
        div.className = `task-item ${t.status}`;
        div.innerHTML = `
            <div class="task-title">${escapeHtml(t.title)}</div>
            <div class="task-meta">
                <span class="task-status ${t.status}">${t.status}</span>
                <span class="task-id">${t.id}</span>
                <button class="task-delete" data-id="${t.id}" title="删除">✕</button>
            </div>
            ${t.result ? `<div style="font-size:11px;color:var(--on-surface-variant);margin-top:6px;opacity:0.8;">${escapeHtml(t.result)}</div>` : ''}
        `;
        taskList.appendChild(div);
    }

    // 绑定删除按钮
    taskList.querySelectorAll('.task-delete').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const id = btn.dataset.id;
            await fetch(`/v1/tasks/${id}`, { method: 'DELETE' });
            pollState();
        });
    });
}

// ═══ 日志渲染 ═══

function renderLogs(logs) {
    logArea.innerHTML = '';
    for (const l of logs) {
        const div = document.createElement('div');
        div.className = 'log-entry';
        div.innerHTML = `<span class="log-event">${escapeHtml(l.event)}</span><span class="log-msg">${escapeHtml(l.msg)}</span>`;
        logArea.appendChild(div);
    }
    logArea.scrollTop = logArea.scrollHeight;
}

// ═══ 截图轮询 ═══

async function pollScreenshot() {
    try {
        const res = await fetch('/v1/screenshot');
        const data = await res.json();
        if (data.screenshot) {
            screenshotImg.src = `data:image/jpeg;base64,${data.screenshot}`;
            screenshotImg.classList.add('visible');
            viewerPlaceholder.style.display = 'none';
        }
    } catch (err) {
        console.error('pollScreenshot error:', err);
    }
}

refreshScreenshot.addEventListener('click', pollScreenshot);

// ═══ 添加任务模态弹窗 ═══

addTaskBtn.addEventListener('click', () => {
    modalOverlay.classList.add('active');
    modalTitle.value = '';
    modalDetail.value = '';
    modalTitle.focus();
});

modalCancel.addEventListener('click', () => {
    modalOverlay.classList.remove('active');
});

modalOverlay.addEventListener('click', (e) => {
    if (e.target === modalOverlay) {
        modalOverlay.classList.remove('active');
    }
});

modalConfirm.addEventListener('click', async () => {
    const title = modalTitle.value.trim();
    if (!title) {
        modalTitle.style.borderColor = 'var(--error)';
        return;
    }
    const detail = modalDetail.value.trim();

    await fetch('/v1/tasks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, detail: detail || title }),
    });

    modalOverlay.classList.remove('active');
    pollState();
});

// ═══ 工具函数 ═══

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ═══ 启动轮询 ═══
pollState();
pollScreenshot();
setInterval(pollState, 4000);
setInterval(pollScreenshot, 8000);
