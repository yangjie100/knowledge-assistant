function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

// H-1: attach the API key when one is configured server-side (KA_API_KEY).
// Set it once via localStorage.setItem('ka_api_key', '<your key>'). The helper also
// mirrors it into a SameSite=Strict cookie so the EventSource SSE stream — which
// cannot send custom headers — passes the API-key gate.
function apiAuth() {
    const key = localStorage.getItem('ka_api_key');
    document.cookie = key ? 'ka_api_key=' + encodeURIComponent(key) + '; path=/; SameSite=Strict' : '';
    return key ? { 'X-API-Key': key } : {};
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
const historyBtn = document.getElementById('historyBtn');
const closeSidebarBtn = document.getElementById('closeSidebarBtn');
const conversationSidebar = document.getElementById('conversationSidebar');
const conversationList = document.getElementById('conversationList');

if (typeof marked !== 'undefined') {
    marked.setOptions({
        highlight: function(code, lang) {
            if (typeof hljs !== 'undefined' && lang && hljs.getLanguage(lang)) return hljs.highlight(code, { language: lang }).value;
            if (typeof hljs !== 'undefined') return hljs.highlightAuto(code).value;
            return code;
        },
        breaks: true, gfm: true
    });
}

function renderMarkdown(text) {
    if (typeof marked !== 'undefined') { try { return marked.parse(text); } catch(e) { return escapeHtml(text); } }
    return escapeHtml(text);
}

function updateConvDisplay() {
    convIdEl.textContent = 'conv: ' + conversationId.substring(0, 8);
}
updateConvDisplay();

streamToggle.addEventListener('click', function() {
    streamMode = !streamMode;
    streamToggle.textContent = 'STREAM ' + (streamMode ? 'ON' : 'OFF');
    streamToggle.className = streamMode ? 'ka-ghost-btn on' : 'ka-ghost-btn';
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

/* ── 空状态：▌ awaiting input ── */
function showEmpty() {
    messagesEl.innerHTML = '<div class="ka-empty">'
        + '<div class="ka-empty-mark">▌ awaiting input</div>'
        + '<div class="ka-empty-sub">检索 · 推理 · 生成 —— 每次问答都是一条可追溯的实验日志</div>'
        + '</div>';
}

/* 日志条目式消息。返回正文容器（assistant 场景由调用方继续填充）。 */
function appendMessage(role, text) {
    const empty = messagesEl.querySelector('.ka-empty');
    if (empty) empty.remove();

    const entry = document.createElement('div');
    entry.className = role === 'user' ? 'ka-msg-user' : 'ka-msg-ka';

    const prefix = document.createElement('span');
    prefix.className = 'ka-prefix';
    prefix.textContent = role === 'user' ? 'user ▍' : 'ka ▸';

    const body = document.createElement('div');
    body.className = 'ka-body';

    entry.appendChild(prefix);
    entry.appendChild(body);

    if (role === 'assistant') {
        body.dataset.rawText = '';
        body.kaStart = Date.now();
        const meta = document.createElement('div');
        meta.className = 'ka-meta';
        meta.textContent = 'conv: ' + conversationId.substring(0, 8);
        entry.appendChild(meta);
        body.kaMetaEl = meta;
    } else {
        body.textContent = text;
    }

    messagesEl.appendChild(entry);
    messagesEl.scrollTop = messagesEl.scrollHeight;
    return body;
}

/* 元数据栏收口：耗时 + 模式（mono） */
function finalizeMeta(body, mode) {
    if (!body.kaMetaEl || !body.kaStart) return;
    const elapsed = Math.max(0.1, (Date.now() - body.kaStart) / 1000).toFixed(1) + 's';
    body.kaMetaEl.innerHTML = 'conv: ' + conversationId.substring(0, 8)
        + '<span class="ka-meta-sep">·</span>' + elapsed
        + '<span class="ka-meta-sep">·</span>' + mode;
}

function renderAssistantBubble(bubble, rawText) {
    bubble.dataset.rawText = rawText;
    const { think, answer } = parseThinkBlock(rawText);

    let html = '';
    if (think) {
        html += '<details class="think-block"><summary>'
            + '<svg width="10" height="10" fill="none" stroke="currentColor" viewBox="0 0 24 24">'
            + '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>'
            + '▸ reasoning trace</summary>'
            + '<div class="think-body">' + escapeHtml(think) + '</div></details>';
    }
    const mainText = answer || (!think ? rawText : '');
    if (mainText) {
        html += '<div class="markdown-content">' + renderMarkdown(mainText) + '</div>';
    }
    if (bubble.dataset.streaming === '1') {
        html += '<span class="ka-cursor">▍</span>';
    }
    bubble.innerHTML = html;
    if (typeof hljs !== 'undefined') {
        bubble.querySelectorAll('pre code').forEach(function(block) { hljs.highlightElement(block); });
    }
}

function setLoading(loading) {
    sendBtn.disabled = loading;
    sendBtn.textContent = loading ? 'retrieving…' : 'SEND';
    questionInput.disabled = loading;
    messagesEl.classList.toggle('is-loading', loading);
}

function sendSync(question) {
    fetch('/api/chat', {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, apiAuth()),
        body: JSON.stringify({ question: question, conversationId: conversationId })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success && data.data) {
            conversationId = data.data.conversationId || conversationId;
            updateConvDisplay();
            const bubble = appendMessage('assistant', '');
            renderAssistantBubble(bubble, data.data.answer || 'No response');
            finalizeMeta(bubble, 'sync');
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
    apiAuth(); // sync cookie for EventSource (no custom headers possible)
    const bubble = appendMessage('assistant', '');
    bubble.dataset.streaming = '1';
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
        bubble.dataset.streaming = '0';
        if (!bubble.dataset.rawText) {
            bubble.textContent = '(No response)';
        } else {
            // 重新渲染以移除流式光标
            renderAssistantBubble(bubble, bubble.dataset.rawText);
        }
        finalizeMeta(bubble, 'stream');
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
    showEmpty();
});

// Conversation management
async function loadConversations() {
    try {
        const res = await fetch('/api/chat/conversations', { headers: apiAuth() });
        const data = await res.json();
        if (data.success && data.data) renderConversations(data.data);
    } catch(e) { console.error('Failed to load conversations:', e); }
}

/* mono 时间戳：MM-DD HH:mm */
function formatConvTime(t) {
    if (!t) return '';
    const d = new Date(t);
    if (isNaN(d.getTime())) return '';
    const p = function(n) { return (n < 10 ? '0' : '') + n; };
    return p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
}

function renderConversations(conversations) {
    conversationList.innerHTML = conversations.map(function(conv) {
        const isActive = conv.conversationId === conversationId;
        const lastQ = conv.lastQuestion || 'New conversation';
        const timeStr = formatConvTime(conv.lastMessageAt || conv.createdAt);
        return '<div class="conv-item-wrapper flex items-center justify-between p-2 cursor-pointer'
            + (isActive ? ' ka-active' : '') + '" data-cid="' + conv.conversationId + '">'
            + '<div class="flex-1 min-w-0 conv-item" data-cid="' + conv.conversationId + '">'
            + '<div class="conv-title truncate">' + escapeHtml(lastQ) + '</div>'
            + '<div class="conv-meta">' + timeStr
            + (conv.messageCount ? ' &middot; ' + conv.messageCount + ' msgs' : '')
            + '</div></div>'
            + '<button class="conv-del ml-2" aria-label="Delete conversation" data-cid="' + conv.conversationId + '">&times;</button></div>';
    }).join('');
    conversationList.querySelectorAll('.conv-item').forEach(function(el) {
        el.addEventListener('click', function() { switchConversation(this.dataset.cid); });
    });
    conversationList.querySelectorAll('.conv-del').forEach(function(el) {
        el.addEventListener('click', function(e) { e.stopPropagation(); deleteConversation(this.dataset.cid); });
    });
}

async function switchConversation(convId) {
    conversationId = convId;
    updateConvDisplay();
    messagesEl.innerHTML = '';
    try {
        const res = await fetch('/api/chat/history/' + convId, { headers: apiAuth() });
        const data = await res.json();
        if (data.success && data.data) {
            data.data.forEach(function(msg) {
                const content = msg.content || '';
                const type = msg.messageType || msg.type || '';
                const role = (type === 'USER' || type === 'user') ? 'user' : 'assistant';
                const bubble = appendMessage(role, '');
                if (role === 'assistant') renderAssistantBubble(bubble, content);
                else bubble.textContent = content;
            });
        }
    } catch(e) { console.error('Failed to load history:', e); }
    messagesEl.scrollTop = messagesEl.scrollHeight;
    toggleSidebar(false);
    loadConversations();
}

async function deleteConversation(convId) {
    if (!confirm('Delete this conversation?')) return;
    try {
        await fetch('/api/chat/conversations/' + convId, { method: 'DELETE', headers: apiAuth() });
        if (convId === conversationId) {
            conversationId = generateUUID();
            updateConvDisplay();
            showEmpty();
        }
        loadConversations();
    } catch(e) { console.error('Failed to delete conversation:', e); }
}

function toggleSidebar(show) {
    conversationSidebar.classList.toggle('hidden', !show);
}

historyBtn.addEventListener('click', function() { loadConversations(); toggleSidebar(true); });
closeSidebarBtn.addEventListener('click', function() { toggleSidebar(false); });

// 初始空状态
showEmpty();
