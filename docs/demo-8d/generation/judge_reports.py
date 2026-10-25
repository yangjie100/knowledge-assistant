# -*- coding: utf-8 -*-
"""Hybrid judge for generated 8D reports: rule-based checks + GLM deep review.

Rule dims (free): structure(D1-D8) / fidelity(5 field values verbatim) /
discipline(pending-confirm markers) / methodology(5Why+3-cause layers).
LLM dims (GLM via Coding endpoint): root_cause_depth, action_quality, grounding (1-5 each).
Output: eval/judge-results.json + eval/SUMMARY.md
"""
import json
import os
import sys
import time
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RECORDS_DIR = os.path.join(BASE_DIR, "..", "records")
REPORTS_DIR = os.path.join(BASE_DIR, "..", "eval", "glm")
OUT_JSON = os.path.join(BASE_DIR, "..", "eval", "judge-results.json")
OUT_MD = os.path.join(BASE_DIR, "..", "eval", "SUMMARY.md")

API = "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions"
MODEL = "glm-5.3"

D_KEYS = ["D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8"]
M_KEYS = ["5Why", "直接原因", "流出原因", "系统原因"]

JUDGE_SYSTEM = (
    "你是军工电子制造行业 8D 报告评审专家。对提交的 AI 生成 8D 报告初稿做严格评分，"
    "只输出 JSON，格式：{\"root_cause_depth\": n, \"action_quality\": n, \"grounding\": n, \"comment\": \"一句话\"}，"
    "n 为 1-5 整数。评分标准："
    "root_cause_depth=D4 工程可信度与直接/流出/系统三层区分清晰度；"
    "action_quality=D5/D7 措施具体、可执行、可验证且回应流出与系统原因的程度；"
    "grounding=对已有数据与历史案例的引用合理性，编造数据扣重分。"
)

JUDGE_USER = """【质量异常记录】
{record}

【AI 生成的 8D 报告】
{report}

请评分。"""


def rule_score(record, report):
    structure = sum(1 for k in D_KEYS if k in report)
    fidelity = 0
    fid_detail = {}
    for field in ["recordId", "productCode", "batchNo"]:
        v = str(record.get(field, ""))
        if v and v in report:
            fidelity += 1
            fid_detail[field] = "OK"
        else:
            fid_detail[field] = "MISS"
    q = record.get("quantity") or {}
    for field in ["defectives", "batchTotal"]:
        v = str(q.get(field, ""))
        if v and v in report:
            fidelity += 1
            fid_detail[field] = "OK"
        else:
            fid_detail[field] = "MISS"
    pending = report.count("（待确认）")
    discipline = 1 if 1 <= pending <= 10 else 0
    methodology = sum(1 for k in M_KEYS if k in report)
    return {"structure": structure, "fidelity": fidelity, "fidelity_detail": fid_detail,
            "pending_markers": pending, "discipline": discipline,
            "methodology": methodology}


def llm_score(record, report, max_retries=3):
    payload = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": JUDGE_SYSTEM},
            {"role": "user", "content": JUDGE_USER.format(
                record=json.dumps(record, ensure_ascii=False), report=report)},
        ],
        "temperature": 0.1,
        "max_tokens": 12288,
    }).encode("utf-8")
    req = urllib.request.Request(API, data=payload, headers={
        "Content-Type": "application/json",
        "Authorization": "Bearer " + os.environ["ZHIPU_API_KEY"],
    })
    for attempt in range(max_retries):
        try:
            with urllib.request.urlopen(req, timeout=600) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            content = body["choices"][0]["message"]["content"].strip()
            # model may wrap JSON in code fences or prose; extract first {...}
            start, end = content.find("{"), content.rfind("}")
            return json.loads(content[start:end + 1]), body.get("usage", {})
        except Exception as e:  # noqa: BLE001
            if attempt == max_retries - 1:
                return {"error": str(e)}, {}
            time.sleep(10 * (attempt + 1))


def main():
    only = sys.argv[sys.argv.index("--record") + 1] if "--record" in sys.argv else None
    ids = sorted(p[:-5] for p in os.listdir(RECORDS_DIR) if p.endswith(".json"))
    if only:
        ids = [i for i in ids if i == only]
    results, total_usage = [], {"prompt_tokens": 0, "completion_tokens": 0}
    for i, rid in enumerate(ids, 1):
        rp = os.path.join(REPORTS_DIR, f"report-{rid}.md")
        if not os.path.exists(rp):
            print(f"[{i}/{len(ids)}] {rid}: no report, skip")
            continue
        with open(os.path.join(RECORDS_DIR, rid + ".json"), encoding="utf-8") as f:
            record = json.load(f)
        with open(rp, encoding="utf-8") as f:
            report = f.read()
        rs = rule_score(record, report)
        print(f"[{i}/{len(ids)}] {rid} rules={rs['structure']}/8 {rs['fidelity']}/5 "
              f"{rs['methodology']}/4 judging ...", flush=True)
        ls, usage = llm_score(record, report)
        total_usage["prompt_tokens"] += usage.get("prompt_tokens", 0)
        total_usage["completion_tokens"] += usage.get("completion_tokens", 0)
        entry = {"recordId": rid, "rule": rs, "llm": ls,
                 "report_chars": len(report)}
        results.append(entry)
        print(f"  llm={ls}")
        time.sleep(1)

    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    n = len(results)
    lines = [
        "# 8D 生成质量评测汇总（GLM 态）", "",
        f"- 样本: {n} 份报告",
        f"- 规则均分: structure {sum(r['rule']['structure'] for r in results) / max(n, 1):.1f}/8 · "
        f"fidelity {sum(r['rule']['fidelity'] for r in results) / max(n, 1):.1f}/5 · "
        f"methodology {sum(r['rule']['methodology'] for r in results) / max(n, 1):.1f}/4",
        f"- LLM 均分: root_cause {sum(r['llm'].get('root_cause_depth', 0) for r in results) / max(n, 1):.1f}/5 · "
        f"action {sum(r['llm'].get('action_quality', 0) for r in results) / max(n, 1):.1f}/5 · "
        f"grounding {sum(r['llm'].get('grounding', 0) for r in results) / max(n, 1):.1f}/5",
        f"- 裁判 token 消耗: prompt={total_usage['prompt_tokens']} completion={total_usage['completion_tokens']}",
        "", "| 记录 | 结构/8 | 保真/5 | 方法/4 | 待确认 | 根因/5 | 措施/5 | 引用/5 | 裁判意见 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for r in results:
        lu = r["llm"]
        lines.append(
            f"| {r['recordId']} | {r['rule']['structure']} | {r['rule']['fidelity']} | "
            f"{r['rule']['methodology']} | {r['rule']['pending_markers']} | "
            f"{lu.get('root_cause_depth', '-')} | {lu.get('action_quality', '-')} | "
            f"{lu.get('grounding', '-')} | {lu.get('comment', lu.get('error', ''))[:40]} |")
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\nDONE {n} judged -> {OUT_MD}")


if __name__ == "__main__":
    main()
