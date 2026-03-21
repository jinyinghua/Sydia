function updateClock() {
    const now = new Date();
    document.getElementById('clock').innerText = now.toLocaleTimeString();
}
setInterval(updateClock, 1000);
updateClock();

async function fetchState() {
    const apiKey = document.getElementById('api-key-input').value;
    try {
        const response = await fetch('/v1/state', {
            headers: { 'X-API-Key': apiKey }
        });
        const data = await response.json();
        
        // 更新任务池
        const taskPool = document.getElementById('task-pool');
        taskPool.innerHTML = '';
        data.task_queue.forEach(task => {
            const div = document.createElement('div');
            div.className = 'task-item';
            if (task === data.current_task) {
                div.classList.add('active');
            }
            div.innerText = task;
            taskPool.appendChild(div);
        });

        // 更新截图
        if (data.screenshot) {
            document.getElementById('viewer-img').src = `data:image/jpeg;base64,${data.screenshot}`;
        }
    } catch (error) {
        console.error('Error fetching state:', error);
    }
}

setInterval(fetchState, 2000);

document.getElementById('ask-btn').addEventListener('click', async () => {
    const input = document.getElementById('ask-input');
    const apiKey = document.getElementById('api-key-input').value;
    const message = input.value;
    if (!message) return;

    appendMessage('User', message, 'ai-msg');
    input.value = '';

    try {
        const response = await fetch('/v1/chat', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-API-Key': apiKey
            },
            body: JSON.stringify({ message })
        });
        const data = await response.json();
        appendMessage('AI', data.reply, 'ai-msg');
        if (data.raw) {
            appendMessage('Raw', data.raw, 'raw-msg');
        }
    } catch (error) {
        console.error('Error chatting:', error);
    }
});

function appendMessage(sender, text, className) {
    const chatHistory = document.getElementById('chat-history');
    const div = document.createElement('div');
    div.className = className;
    div.innerText = `${sender}: ${text}`;
    chatHistory.appendChild(div);
    chatHistory.scrollTop = chatHistory.scrollHeight;
}

async function submitTask() {
    const text = document.getElementById('modal-text').value;
    const apiKey = document.getElementById('api-key-input').value;
    if (!text) return;

    try {
        await fetch('/v1/chat', {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'X-API-Key': apiKey
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
