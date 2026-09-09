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
        html += '<details class="mb-2 think-block"><summary class="cursor-pointer text-xs text-gray-400 hover:text-gray-600 flex items-center gap-1">'
            + '<svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">'
            + '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/></svg>'
            + 'Thinking process</summary>'
            + '<div class="mt-2 p-3 bg-gray-50 rounded-lg text-xs text-gray-500 whitespace-pre-wrap border border-gray-100">'
            + escapeHtml(think) + '</div></details>';
    }
    const mainText = answer || (!think ? rawText : '');
    if (mainText) {
        html += '<div class="markdown-content prose prose-sm max-w-none">' + renderMarkdown(mainText) + '</div>';
    }
    bubble.innerHTML = html;
    if (typeof hljs !== 'undefined') {
        bubble.querySelectorAll('pre code').forEach(function(block) { hljs.highlightElement(block); });
    }
}

function setLoading(loading) {
    sendBtn.disabled = loading;
    sendBtn.textContent = loading ? 'Waiting...' : 'Send';
    questionInput.disabled = loading;
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

// Conversation management
async function loadConversations() {
    try {
        const res = await fetch('/api/chat/conversations', { headers: apiAuth() });
        const data = await res.json();
        if (data.success && data.data) renderConversations(data.data);
    } catch(e) { console.error('Failed to load conversations:', e); }
}

function renderConversations(conversations) {
    conversationList.innerHTML = conversations.map(function(conv) {
        const isActive = conv.conversationId === conversationId;
        const lastQ = conv.lastQuestion || 'New conversation';
        const time = conv.lastMessageAt || conv.createdAt;
        const timeStr = time ? new Date(time).toLocaleString() : '';
        return '<div class="flex items-center justify-between p-2 rounded cursor-pointer '
            + (isActive ? 'bg-blue-50 border border-blue-200' : 'hover:bg-gray-50')
            + '" data-cid="' + conv.conversationId + '">'
            + '<div class="flex-1 min-w-0 conv-item" data-cid="' + conv.conversationId + '">'
            + '<div class="text-sm font-medium text-gray-700 truncate">' + escapeHtml(lastQ) + '</div>'
            + '<div class="text-xs text-gray-400">' + timeStr
            + (conv.messageCount ? ' &middot; ' + conv.messageCount + ' msgs' : '')
            + '</div></div>'
            + '<button class="conv-del ml-2 text-gray-300 hover:text-red-500 text-sm" data-cid="' + conv.conversationId + '">&times;</button></div>';
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
            messagesEl.innerHTML = '';
        }
        loadConversations();
    } catch(e) { console.error('Failed to delete conversation:', e); }
}

function toggleSidebar(show) {
    conversationSidebar.classList.toggle('hidden', !show);
}

historyBtn.addEventListener('click', function() { loadConversations(); toggleSidebar(true); });
closeSidebarBtn.addEventListener('click', function() { toggleSidebar(false); });
