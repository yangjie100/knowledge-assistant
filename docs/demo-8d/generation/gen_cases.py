# -*- coding: utf-8 -*-
"""Batch-generate 22 desensitized historical 8D case docs via Zhipu Coding endpoint.

Serial calls (Coding endpoint is concurrency-sensitive). Usage tokens are logged
to usage.jsonl so quota burn is measurable. Run:
    python gen_cases.py [--only slug1,slug2]
"""
import json
import os
import sys
import time
import urllib.request

from specs import SPECS

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
HISTORY_DIR = os.path.join(BASE_DIR, "..", "history", "generated")
SAMPLE_PATH = os.path.join(BASE_DIR, "..", "history", "8d-case-wave-soldering.md")
USAGE_LOG = os.path.join(BASE_DIR, "usage-cases.jsonl")

API = "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions"
MODEL_CANDIDATES = ["glm-5.3", "glm-5.2"]

SYSTEM = (
    "你是一名军工电子制造行业的资深质量工程师，精通 8D 方法论、GJB 质量管理体系。"
    "你正在整理历史已结案的 8D 案例档案。只输出案例正文 markdown，不要任何解释或代码块包裹。"
)

TEMPLATE = """请按下方【格式样板】的结构与详略程度，撰写一份历史已结案 8D 案例档案。

案例要素（全部为脱敏虚构数据，请基于这些要素合理展开细节）：
- 案例编号: {rid}
- 产品: {product}
- 涉及工序: {process}
- 发现环节: {stage}
- 缺陷现象: {defect}
- 直接原因方向: {direct}
- 流出原因方向: {escape}
- 系统原因方向: {system}
- 纠正与预防措施方向: {actions}

要求：
1. 结构与样板一致：案例头信息表、问题描述（5W2H）、围堵措施、根因分析（直接/流出/系统三层）、
   纠正措施、预防固化、验证与结案。
2. 数字细节（数量/工时/参数值）在要素基础上合理补充，保持工程可信。
3. 全文 1500-2500 字，不出现真实单位/人名，产品只用给定代号。
4. 用中文，标题层级与样板对齐。

【格式样板】
{sample}"""


def call_llm(model, system, user, max_retries=3):
    payload = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.7,
        # GLM-5.3 has mandatory reasoning; 4096 gets eaten by thinking alone.
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
            return body["choices"][0]["message"]["content"], body.get("usage", {})
        except Exception as e:  # noqa: BLE001 - retry any transport/5xx error
            if attempt == max_retries - 1:
                raise
            print(f"  retry {attempt + 1}: {e}")
            time.sleep(10 * (attempt + 1))


def main():
    only = None
    if len(sys.argv) > 2 and sys.argv[1] == "--only":
        only = set(sys.argv[2].split(","))
    os.makedirs(HISTORY_DIR, exist_ok=True)
    with open(SAMPLE_PATH, encoding="utf-8") as f:
        sample = f.read()

    model = MODEL_CANDIDATES[0]
    total_usage = {"prompt_tokens": 0, "completion_tokens": 0}
    done, failed = 0, []
    with open(USAGE_LOG, "a", encoding="utf-8") as usage_f:
        for i, spec in enumerate(SPECS, 1):
            if only and spec["slug"] not in only:
                continue
            out_path = os.path.join(HISTORY_DIR, f"8d-case-{spec['slug']}.md")
            if os.path.exists(out_path):
                print(f"[{i}/{len(SPECS)}] {spec['slug']} exists, skip")
                continue
            user = TEMPLATE.format(sample=sample, **spec)
            print(f"[{i}/{len(SPECS)}] generating {spec['slug']} ({spec['title']}) ...", flush=True)
            try:
                content, usage = call_llm(model, SYSTEM, user)
            except Exception as e:  # noqa: BLE001
                # model fallback: if 5.3 unavailable, drop to 5.2 for the rest
                if model != MODEL_CANDIDATES[1]:
                    print(f"  model {model} failed ({e}); falling back to {MODEL_CANDIDATES[1]}")
                    model = MODEL_CANDIDATES[1]
                    content, usage = call_llm(model, SYSTEM, user)
                else:
                    print(f"  FAILED: {e}")
                    failed.append(spec["slug"])
                    continue
            if not content.strip().startswith("#"):
                content = f"# {spec['rid']} {spec['title']}（历史结案案例）\n\n" + content
            with open(out_path, "w", encoding="utf-8") as f:
                f.write(content)
            pt = usage.get("prompt_tokens", 0)
            ct = usage.get("completion_tokens", 0)
            total_usage["prompt_tokens"] += pt
            total_usage["completion_tokens"] += ct
            usage_f.write(json.dumps({"slug": spec["slug"], "model": model,
                                      "prompt_tokens": pt, "completion_tokens": ct},
                                     ensure_ascii=False) + "\n")
            usage_f.flush()
            done += 1
            print(f"  saved {len(content)} chars, tokens p={pt} c={ct}")
            time.sleep(2)

    print(f"\nDONE: {done} generated, {len(failed)} failed {failed if failed else ''}")
    print(f"TOTAL tokens: prompt={total_usage['prompt_tokens']} "
          f"completion={total_usage['completion_tokens']}")


if __name__ == "__main__":
    main()
