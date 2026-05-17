function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

let conversationId = generateUUID();
let streamMode = true;
let currentEventSource = null;
const convIdEl = document.getElementById('convId');
const messagesEl = document.getElementById('messages');
const chatForm = document.getElementById('chatForm');
const questionInput = document.getElementById('questionInput');
const newChatBtn = document.getElementById('newChatBtn');
const streamToggle = document.getElementById('streamToggle');
const sendBtn = document.getElementById('sendBtn');

function updateConvDisplay() {
    convIdEl.textContent = 'Conversation: ' + conversationId.substring(0, 8) + '...';
}
updateConvDisplay();

streamToggle.addEventListener('click', function() {
    streamMode = !streamMode;
    streamToggle.textContent = 'Stream: ' + (streamMode ? 'ON' : 'OFF');
    streamToggle.className = streamMode
        ? 'px-3 py-2 bg-green-500 text-white text-sm rounded hover:bg-green-600'
        : 'px-3 py-2 bg-gray-200 text-gray-700 text-sm rounded hover:bg-gray-300';
});

function parseThinkBlock(text) {
    const THINK_OPEN = '<thinkthink>';
    const THINK_CLOSE = '</thinkthink>';
    const openIdx = text.indexOf(THINK_OPEN);
    if (openIdx === -1) {
        return { think: null, answer: text };
    }
    const closeIdx = text.indexOf(THINK_CLOSE);
    if (closeIdx !== -1) {
        const thinkContent = text.substring(openIdx + THINK_OPEN.length, closeIdx);
        const answer = text.substring(closeIdx + THINK_CLOSE.length).trim();
        return { think: thinkContent, answer: answer };
    }
    // Still streaming — think block not closed yet
    const thinkContent = text.substring(openIdx + THINK_OPEN.length);
    return { think: thinkContent, answer: '' };
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function appendMessage(role, text) {
    const wrapper = document.createElement('div');
    wrapper.className = 'flex ' + (role === 'user' ? 'justify-end' : 'justify-start');

    const bubble = document.createElement('div');
    bubble.className = role === 'user'
        ? 'max-w-[70%] bg-blue-500 text-white rounded-2xl px-4 py-2'
        : 'max-w-[70%] bg-white text-gray-800 rounded-2xl px-4 py-2 shadow';
    bubble.style.whiteSpace = 'pre-wrap';

    if (role === 'assistant') {
        bubble.dataset.rawText = '';
    } else {
        bubble.textContent = text;
    }

    wrapper.appendChild(bubble);
    messagesEl.appendChild(wrapper);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return bubble;
}

function renderAssistantBubble(bubble, rawText) {
    bubble.dataset.rawText = rawText;
    const { think, answer } = parseThinkBlock(rawText);

    let html = '';
    if (think) {
        html += '<details class="mb-2"><summary class="cursor-pointer text-xs text-gray-400 hover:text-gray-600">'
            + 'Thinking process</summary><div class="mt-1 p-2 bg-gray-50 rounded text-xs text-gray-500 whitespace-pre-wrap">'
            + escapeHtml(think) + '</div></details>';
    }
    if (answer) {
        html += '<div>' + escapeHtml(answer) + '</div>';
    }
    if (!think && !answer) {
        html = escapeHtml(rawText);
    }
    bubble.innerHTML = html;
}

function setLoading(loading) {
    sendBtn.disabled = loading;
    sendBtn.textContent = loading ? 'Waiting...' : 'Send';
    questionInput.disabled = loading;
}

function sendSync(question) {
    fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: question, conversationId: conversationId })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success && data.data) {
            conversationId = data.data.conversationId || conversationId;
            updateConvDisplay();
            const bubble = appendMessage('assistant', '');
            renderAssistantBubble(bubble, data.data.answer || 'No response');
        } else {
            appendMessage('assistant', 'Error: ' + (data.message || 'Unknown error'));
        }
        setLoading(false);
    })
    .catch(err => {
        appendMessage('assistant', 'Network error: ' + err.message);
        setLoading(false);
    });
}

function sendStream(question) {
    const bubble = appendMessage('assistant', '');
    const url = '/api/chat/stream?question=' + encodeURIComponent(question)
        + '&conversationId=' + encodeURIComponent(conversationId);
    const eventSource = new EventSource(url);
    currentEventSource = eventSource;

    eventSource.onmessage = function(event) {
        renderAssistantBubble(bubble, bubble.dataset.rawText + event.data);
        messagesEl.scrollTop = messagesEl.scrollHeight;
    };

    eventSource.onerror = function() {
        eventSource.close();
        currentEventSource = null;
        if (!bubble.dataset.rawText) {
            bubble.textContent = '(No response)';
        }
        setLoading(false);
    };
}

chatForm.addEventListener('submit', function(e) {
    e.preventDefault();
    const question = questionInput.value.trim();
    if (!question) return;

    if (currentEventSource) {
        currentEventSource.close();
        currentEventSource = null;
    }

    appendMessage('user', question);
    questionInput.value = '';
    setLoading(true);

    if (streamMode) {
        sendStream(question);
    } else {
        sendSync(question);
    }
});

newChatBtn.addEventListener('click', function() {
    if (currentEventSource) {
        currentEventSource.close();
        currentEventSource = null;
    }
    conversationId = generateUUID();
    updateConvDisplay();
    messagesEl.innerHTML = '';
});
