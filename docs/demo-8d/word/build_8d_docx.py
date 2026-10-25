# -*- coding: utf-8 -*-
"""8D demo: wrap merge_to_docx.py with demo-specific CONFIG (cover, single chapter)."""
import os
import sys

BASE = r"C:\Users\admin\AppData\Local\Temp\8d-word"
sys.path.insert(0, BASE)
os.chdir(BASE)

import merge_to_docx as m

m.CONFIG.update({
    "chapter_files": ["8d-report.md"],
    "output_file": "8D-QR-2026-1001-AI初稿.docx",
    "cover": {
        "title": "8D 质量异常分析报告\n（AI 辅助生成初稿）",
        "subtitle": "QR-2026-1001 · ZK-A5 接口板组件",
        "info_lines": [
            "生成链路：knowledge-assistant（本地模型 qwen3.8:27b · 全程离线）",
            "知识库：3 份历史 8D 案例（RAG 检索命中 5 段）",
            "",
            "2026年10月",
            "密级：内部资料 · 演示数据（已脱敏）",
        ],
    },
})

m.main()
