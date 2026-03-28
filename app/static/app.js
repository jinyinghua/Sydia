// ===== 时钟 =====
function updateClock() {
    const now = new Date();
    document.getElementById('clock').innerText = now.toLocaleTimeString();
}
setInterval(updateClock, 1000);
updateClock();

// ===== 获取 API Key =====
function getApiKey() {
    return document.getElementById('api-key-input').value;
}

// ===== 轮询系统状态 =====
async function fetchState() {
    try {
        const response = await fetch('/v1/state', {
            headers: { 'X-API-Key': getApiKey() }
        });
        const data = await response.json();
        
        // 更新任务池
        const taskPool = document.getElementById('task-pool');
        taskPool.innerHTML = '';

        // 修复：先显示当前正在执行的任务（带 active 高亮）
        if (data.current_task) {
            const activeDiv = document.createElement('div');
            activeDiv.className = 'task-item active';
            activeDiv.innerText = '▶ ' + data.current_task;
            taskPool.appendChild(activeDiv);
        }

        // 再显示队列中排队的任务
        data.task_queue.forEach(task => {
            const div = document.createElement('div');
            div.className = 'task-item';
            div.innerText = task;
            taskPool.appendChild(div);
        });

        // 更新截图
        if (data.screenshot) {
            document.getElementById('viewer-img').src = `data:image/png;base64,${data.screenshot}`;
        }
    } catch (error) {
        console.error('Error fetching state:', error);
    }
}

setInterval(fetchState, 2000);

// ===== 发送聊天消息 =====
async function sendChat() {
    const input = document.getElementById('ask-input');
    const message = input.value.trim();
    if (!message) return;

    appendMessage('User', message, 'user-msg');  // 修复：使用 user-msg 类
    input.value = '';

    try {
        const response = await fetch('/v1/chat', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-API-Key': getApiKey()
            },
            body: JSON.stringify({ message })
        });
        const data = await response.json();
        appendMessage('AI', data.reply, 'ai-msg');
        if (data.raw && data.raw !== data.reply) {
            appendMessage('Raw', data.raw, 'raw-msg');
        }
        // 发送后立即刷新状态
        fetchState();
    } catch (error) {
        appendMessage('Error', error.message, 'raw-msg');
        console.error('Error chatting:', error);
    }
}

// 点击 Send 按钮
document.getElementById('ask-btn').addEventListener('click', sendChat);

// 修复：监听 Enter 键发送消息
document.getElementById('ask-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendChat();
    }
});

// ===== 聊天消息渲染 =====
function appendMessage(sender, text, className) {
    const chatHistory = document.getElementById('chat-history');
    const div = document.createElement('div');
    div.className = className;
    div.innerText = `${sender}: ${text}`;
    chatHistory.appendChild(div);
    chatHistory.scrollTop = chatHistory.scrollHeight;
}

// ===== 弹窗提交任务 =====
async function submitTask() {
    const text = document.getElementById('modal-text').value.trim();
    if (!text) return;

    try {
        await fetch('/v1/chat', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-API-Key': getApiKey()
            },
            body: JSON.stringify({ message: `Add task: ${text}` })
        });
        document.getElementById('blur-modal').style.display = 'none';
        document.getElementById('modal-text').value = '';
        fetchState();
    } catch (error) {
        console.error('Error adding task:', error);
    }
}

// 修复：绑定提交按钮点击事件
document.getElementById('submit-task-btn').addEventListener('click', submitTask);

// 弹窗中 textarea 也支持 Ctrl+Enter 提交
document.getElementById('modal-text').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        submitTask();
    }
});
