#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""knowledge-assistant RAG eval (LLM-as-judge). 调 /rag-chain 拿答案，GLM 按 rubric 打分。
仅用标准库。用法: python judge.py --label baseline"""
import argparse, json, os, re, sys, time, urllib.request

RUBRIC = """你是 RAG 系统答案的严格评估员，按以下标准对系统回答打分。
- accuracy 准确性(0-2): 2=事实完全正确无编造, 1=大体正确轻微瑕疵, 0=关键事实错误或编造
- relevance 相关性(0-2): 2=完全切题, 1=部分跑题, 0=答非所问
- completeness 完整性(0-2): 2=覆盖参考要点全部, 1=覆盖部分, 0=遗漏核心
- grounding 接地性(0-2): 2=答案由语料支撑无臆造, 1=混入少量语料外内容, 0=大量臆造

问题: {q}
参考要点: {exp}
系统回答: {ans}

只输出一行 JSON，禁止任何其他文字:
{{"accuracy":0,"relevance":0,"completeness":0,"grounding":0,"reason":"扣分点说明"}}"""


def http_json(url, payload, headers, timeout):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def ask_rag(api, question, timeout=120):
    try:
        body = http_json(api, {"question": question},
                         {"Content-Type": "application/json; charset=utf-8"}, timeout)
        d = body.get("data") or {}
        if not d.get("success", True) and d.get("errorMessage"):
            return None, d.get("errorMessage")
        return d.get("content", ""), None
    except Exception as e:
        return None, str(e)


def judge_one(judge_url, model, tok, q, exp, ans, timeout=60):
    prompt = RUBRIC.format(q=q, exp=exp, ans=ans)
    base = {"model": model, "messages": [{"role": "user", "content": prompt}], "temperature": 0.0}
    headers = {"Content-Type": "application/json; charset=utf-8", "Authorization": "Bearer " + tok}

    def call(extra):
        p = dict(base, **extra)
        try:
            b = http_json(judge_url, p, headers, timeout)
            t = b["choices"][0]["message"]["content"]
            m = re.search(r"\{.*\}", t, re.S)
            return (json.loads(m.group(0)) if m else None), (None if m else "no-json")
        except Exception as e:
            return None, str(e)

    sc, err = call({"response_format": {"type": "json_object"}})
    if sc:
        return sc, None
    sc, err2 = call({})  # 降级：去掉 response_format 重试
    return sc, (err2 if not sc else None)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True, help="结果标签，如 baseline / reranker-on")
    ap.add_argument("--api", default="http://localhost:8080/api/agent/rag-chain")
    ap.add_argument("--qa", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "golden-qa.jsonl"))
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--judge-model", default="glm-5.2")
    ap.add_argument("--judge-base", default="https://open.bigmodel.cn/api/coding/paas")
    args = ap.parse_args()

    tok = os.environ.get("ZHIPU_API_KEY", "").strip()
    if not tok:
        sys.exit("错误: 环境变量 ZHIPU_API_KEY 未设置（裁判密钥，与 ka 服务共用同一个）")
    judge_url = args.judge_base.rstrip("/") + "/v4/chat/completions"

    cases = [json.loads(l) for l in open(args.qa, encoding="utf-8") if l.strip()]
    if args.limit:
        cases = cases[:args.limit]
    print("评估 %d 题 | api=%s | judge=%s | label=%s\n" % (len(cases), args.api, args.judge_model, args.label))

    results, total = [], 0
    for i, c in enumerate(cases, 1):
        q, exp = c["question"], c["expected"]
        t0 = time.time()
        ans, err = ask_rag(args.api, q)
        rag_t = time.time() - t0
        if err:
            print("[%d/%d] %s RAG错误: %s" % (i, len(cases), c["id"], err))
            results.append(dict(c, answer=None, error=err, score=None)); continue
        sc, jerr = judge_one(judge_url, args.judge_model, tok, q, exp, ans)
        if jerr or not sc:
            print("[%d/%d] %s 评分失败: %s" % (i, len(cases), c["id"], jerr))
            results.append(dict(c, answer=ans, judge_error=jerr, score=None)); continue
        s = sum(sc.get(k, 0) for k in ("accuracy", "relevance", "completeness", "grounding"))
        total += s
        print("[%d/%d] %s 总分 %d/8 (rag %.1fs) - %s" % (i, len(cases), c["id"], s, rag_t, sc.get("reason", "")))
        results.append(dict(c, answer=ans, score=s, detail=sc, rag_seconds=round(rag_t, 1)))

    scored = [r for r in results if r.get("score") is not None]
    avg = total / len(scored) if scored else 0
    out = {"summary": {"label": args.label, "model": args.judge_model, "total": len(cases),
                       "scored": len(scored), "avg_score": round(avg, 2), "max": 8 * len(scored)},
           "results": results}
    out_path = os.path.join(os.path.dirname(os.path.abspath(args.qa)), "results-%s.json" % args.label)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print("\n平均分: %.2f/8 (%d/%d 题成功评分)" % (avg, len(scored), len(cases)))
    print("结果已写: " + out_path)


if __name__ == "__main__":
    main()
