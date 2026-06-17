#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对比两个 eval 结果，输出 Markdown 胜率表。用法: python compare.py baseline reranker-on"""
import argparse, json, os, sys


def load(label, base):
    p = os.path.join(base, "results-%s.json" % label)
    if not os.path.exists(p):
        sys.exit("找不到 " + p)
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("a"); ap.add_argument("b")
    ap.add_argument("--dir", default=os.path.dirname(os.path.abspath(__file__)))
    args = ap.parse_args()
    ja, jb = load(args.a, args.dir), load(args.b, args.dir)
    ra = {r["id"]: r.get("score") for r in ja["results"]}
    rb = {r["id"]: r.get("score") for r in jb["results"]}
    ids = sorted(set(ra) | set(rb))
    aw = bw = tie = 0
    print("# %s vs %s\n" % (args.a, args.b))
    print("| 题 | %s | %s | 胜方 |" % (args.a, args.b))
    print("|----|------|------|------|")
    for i in ids:
        x, y = ra.get(i), rb.get(i)
        if x is None or y is None:
            mark = "-"
        elif x > y:
            aw += 1; mark = "%s +%d" % (args.a, x - y)
        elif y > x:
            bw += 1; mark = "%s +%d" % (args.b, y - x)
        else:
            tie += 1; mark = "平"
        print("| %s | %s | %s | %s |" % (i, x if x is not None else "-", y if y is not None else "-", mark))
    print("\n**平均分**: %s=%.2f/8  vs  %s=%.2f/8" % (args.a, ja["summary"]["avg_score"], args.b, jb["summary"]["avg_score"]))
    print("**胜率**: %s=%d胜  %s=%d胜  平=%d" % (args.a, aw, args.b, bw, tie))


if __name__ == "__main__":
    main()
