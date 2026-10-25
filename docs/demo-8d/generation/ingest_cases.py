# -*- coding: utf-8 -*-
"""Batch-ingest generated 8D case files into ka knowledge base.

Idempotent-ish: skips files whose name already appears in /api/knowledge/list.
Usage: python ingest_cases.py
"""
import json
import os
import time
import urllib.request
import urllib.parse

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
GEN_DIR = os.path.join(BASE_DIR, "..", "history", "generated")
OLD_DIR = os.path.join(BASE_DIR, "..", "history")

LIST_API = "http://localhost:8080/api/knowledge/list"
UPLOAD_API = "http://localhost:8080/api/knowledge/upload"


def list_docs():
    with urllib.request.urlopen(LIST_API, timeout=30) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    data = body.get("data") or []
    names = set()
    for d in data:
        # docs come back under different shapes; collect every name-ish field
        for k in ("name", "fileName", "filename", "title", "docName"):
            if isinstance(d, dict) and d.get(k):
                names.add(os.path.basename(str(d[k])))
    return names, len(data)


def upload(path):
    boundary = "----8dIngestBoundary"
    with open(path, "rb") as f:
        content = f.read()
    parts = []
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                 f"filename=\"{os.path.basename(path)}\"\r\n"
                 f"Content-Type: text/markdown\r\n\r\n".encode("utf-8"))
    parts.append(content)
    parts.append(f"\r\n--{boundary}--\r\n".encode("utf-8"))
    body = b"".join(parts)
    req = urllib.request.Request(UPLOAD_API, data=body, headers={
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    existing, total = list_docs()
    print(f"knowledge base currently has {total} docs")
    files = sorted(os.listdir(GEN_DIR)) + [f for f in sorted(os.listdir(OLD_DIR))
                                            if f.startswith("8d-case-")]
    seen, uploaded, skipped = set(), 0, 0
    for fname in files:
        if not fname.endswith(".md") or fname in seen:
            continue
        seen.add(fname)
        path = os.path.join(GEN_DIR if fname in os.listdir(GEN_DIR) else OLD_DIR, fname)
        if fname in existing:
            print(f"  skip (already in KB): {fname}")
            skipped += 1
            continue
        print(f"  uploading {fname} ...", flush=True)
        try:
            result = upload(path)
        except Exception as e:  # noqa: BLE001
            print(f"  FAILED {fname}: {e}")
            continue
        ok = result.get("success")
        print(f"  -> success={ok} {json.dumps(result.get('data'), ensure_ascii=False)[:120]}")
        uploaded += 1
        time.sleep(1)
    _, total_after = list_docs()
    print(f"\nDONE: uploaded={uploaded} skipped={skipped}, KB now {total_after} docs")


if __name__ == "__main__":
    main()
