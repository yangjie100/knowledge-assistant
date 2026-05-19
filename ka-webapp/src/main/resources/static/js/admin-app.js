const fileInput = document.getElementById("fileInput");
const uploadBtn = document.getElementById("uploadBtn");
const uploadProgress = document.getElementById("uploadProgress");
const progressBar = document.getElementById("progressBar");
const uploadMsg = document.getElementById("uploadMsg");
const docList = document.getElementById("docList");
const batchDeleteBtn = document.getElementById("batchDeleteBtn");
const statDocs = document.getElementById("statDocs");
const statChunks = document.getElementById("statChunks");
const statSize = document.getElementById("statSize");
const statTypes = document.getElementById("statTypes");

uploadBtn.addEventListener("click", async function() {
    const files = fileInput.files;
    if (!files || files.length === 0) {
        uploadMsg.textContent = "Please select file(s).";
        uploadMsg.className = "mt-1 text-sm text-red-500";
        uploadProgress.classList.remove("hidden");
        return;
    }
    uploadBtn.disabled = true;
    uploadProgress.classList.remove("hidden");
    let uploaded = 0, failed = 0;
    for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const ext = file.name.split(".").pop().toLowerCase();
        if (!["txt","md","pdf","html","htm"].includes(ext)) { failed++; continue; }
        const formData = new FormData();
        formData.append("file", file);
        try {
            uploadMsg.textContent = "Uploading " + (i+1) + "/" + files.length + ": " + file.name;
            progressBar.style.width = ((i+1) / files.length * 100) + "%";
            const res = await fetch("/api/knowledge/upload", { method: "POST", body: formData });
            const data = await res.json();
            if (data.success) uploaded++; else failed++;
        } catch (err) { failed++; }
    }
    uploadMsg.textContent = uploaded + " uploaded" + (failed > 0 ? ", " + failed + " failed" : "");
    uploadMsg.className = "mt-1 text-sm " + (failed > 0 ? "text-yellow-600" : "text-green-500");
    fileInput.value = "";
    uploadBtn.disabled = false;
    loadDocuments();
    loadStats();
});

async function loadStats() {
    try {
        const res = await fetch("/api/knowledge/stats");
        const data = await res.json();
        if (data.success && data.data) {
            statDocs.textContent = data.data.totalDocuments || 0;
            statChunks.textContent = data.data.totalChunks || 0;
            if (statSize) {
                const bytes = data.data.totalSizeBytes || 0;
                statSize.textContent = bytes > 1048576 ? (bytes / 1048576).toFixed(1) + ' MB' : bytes > 1024 ? (bytes / 1024).toFixed(1) + ' KB' : bytes + ' B';
            }
            if (statTypes && data.data.documentsByType) {
                statTypes.textContent = Object.entries(data.data.documentsByType).map(function(e) { return e[0] + ': ' + e[1]; }).join(', ');
            }
        }
    } catch(e) {}
}

async function loadDocuments() {
    try {
        const res = await fetch("/api/knowledge/list");
        const data = await res.json();
        if (data.success && data.data) renderDocuments(data.data);
        else docList.innerHTML = '<p class="text-gray-400 text-sm">Failed to load.</p>';
    } catch (err) {
        docList.innerHTML = '<p class="text-red-400 text-sm">Error: ' + err.message + '</p>';
    }
}

function formatSize(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / 1048576).toFixed(1) + " MB";
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

function renderDocuments(docs) {
    if (!docs || docs.length === 0) {
        docList.innerHTML = '<p class="text-gray-400 text-sm py-4 text-center">No documents yet.</p>';
        batchDeleteBtn.classList.add("hidden");
        return;
    }
    docList.innerHTML = docs.map(function(doc) {
        return '<div class="flex items-center justify-between border rounded px-4 py-3 hover:bg-gray-50">'
            + '<div class="flex items-center gap-3">'
            + '<input type="checkbox" class="doc-checkbox" data-id="' + doc.id + '">'
            + '<div>'
            + '<span class="font-medium text-gray-800">' + escapeHtml(doc.title || doc.id) + '</span>'
            + '<div class="flex gap-3 mt-1 text-xs text-gray-400">'
            + '<span>' + escapeHtml(doc.sourceType || "") + '</span>'
            + '<span>' + (doc.chunkCount || 0) + ' chunks</span>'
            + (doc.fileSize ? '<span>' + formatSize(doc.fileSize) + '</span>' : "")
            + (doc.createTime ? '<span>' + new Date(doc.createTime).toLocaleDateString() + '</span>' : "")
            + '</div></div></div>'
            + '<button onclick="deleteDoc(\'' + doc.id + '\')" class="px-3 py-1 bg-red-100 text-red-600 text-xs rounded hover:bg-red-200">Delete</button>'
            + '</div>';
    }).join("");
    updateBatchDeleteBtn();
    document.querySelectorAll(".doc-checkbox").forEach(function(cb) {
        cb.addEventListener("change", updateBatchDeleteBtn);
    });
}

function updateBatchDeleteBtn() {
    const checked = document.querySelectorAll(".doc-checkbox:checked");
    if (checked.length > 0) {
        batchDeleteBtn.classList.remove("hidden");
        batchDeleteBtn.textContent = "Delete Selected (" + checked.length + ")";
    } else {
        batchDeleteBtn.classList.add("hidden");
    }
}

batchDeleteBtn.addEventListener("click", async function() {
    const checked = document.querySelectorAll(".doc-checkbox:checked");
    if (checked.length === 0) return;
    if (!confirm("Delete " + checked.length + " document(s)?")) return;
    const ids = Array.from(checked).map(function(cb) { return cb.dataset.id; });
    try {
        const res = await fetch("/api/knowledge/batch", {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(ids)
        });
        const data = await res.json();
        if (data.success) { loadDocuments(); loadStats(); }
        else alert("Failed: " + (data.message || "Unknown"));
    } catch (err) { alert("Network error: " + err.message); }
});

async function deleteDoc(id) {
    if (!confirm("Delete this document?")) return;
    try {
        const res = await fetch("/api/knowledge/" + id, { method: "DELETE" });
        const data = await res.json();
        if (data.success) { loadDocuments(); loadStats(); }
        else alert("Failed: " + (data.message || "Unknown"));
    } catch (err) { alert("Network error: " + err.message); }
}

loadDocuments();
loadStats();
