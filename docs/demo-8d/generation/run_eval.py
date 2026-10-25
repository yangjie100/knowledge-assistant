# -*- coding: utf-8 -*-
"""Run the 8D generation eval matrix: every record -> ka /api/agent/8d/generate.

Serial; each call takes ~3min with GLM. Results saved per record with timing.
Usage: python run_eval.py [--record QR-2026-1006]
"""
import json
import os
import sys
import time
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RECORDS_DIR = os.path.join(BASE_DIR, "..", "records")
OUT_DIR = os.path.join(BASE_DIR, "..", "eval", "glm")

API = "http://localhost:8080/api/agent/8d/generate"


def run_one(record_id):
    with open(os.path.join(RECORDS_DIR, record_id + ".json"), encoding="utf-8") as f:
        rec = json.load(f)
    body = json.dumps({"qualityRecord": json.dumps(rec, ensure_ascii=False)},
                      ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(API, data=body, headers={
        "Content-Type": "application/json",
    })
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=900) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    elapsed = round(time.time() - t0, 1)
    report = (result.get("data") or {}).get("report", "")
    meta = {"recordId": record_id, "success": result.get("success"),
            "elapsed_s": elapsed, "chars": len(report),
            "message": result.get("message")}
    out_md = os.path.join(OUT_DIR, f"report-{record_id}.md")
    if report:
        with open(out_md, "w", encoding="utf-8") as f:
            f.write(report)
    return meta


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    only = sys.argv[sys.argv.index("--record") + 1] if "--record" in sys.argv else None
    ids = sorted(p[:-5] for p in os.listdir(RECORDS_DIR) if p.endswith(".json"))
    if only:
        ids = [i for i in ids if i == only]
    metas = []
    meta_path = os.path.join(OUT_DIR, "_meta.jsonl")
    with open(meta_path, "a", encoding="utf-8") as meta_f:
        for i, rid in enumerate(ids, 1):
            out_md = os.path.join(OUT_DIR, f"report-{rid}.md")
            if os.path.exists(out_md):
                print(f"[{i}/{len(ids)}] {rid} exists, skip")
                continue
            print(f"[{i}/{len(ids)}] {rid} generating ...", flush=True)
            try:
                meta = run_one(rid)
            except Exception as e:  # noqa: BLE001
                meta = {"recordId": rid, "success": False, "error": str(e)}
            metas.append(meta)
            meta_f.write(json.dumps(meta, ensure_ascii=False) + "\n")
            meta_f.flush()
            print(f"  -> {meta}")
    ok = sum(1 for m in metas if m.get("success"))
    print(f"\nDONE: {ok}/{len(metas)} ok (skipped ones not counted)")


if __name__ == "__main__":
    main()
