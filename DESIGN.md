# Knowledge Assistant — DESIGN.md

> AI 知识库问答系统的视觉语言规范。**Terminal-Lab Dark** 调性：把对话界面做成「实验日志」，每一次问答都是一条可追溯的检索 → 推理 → 生成记录。

**技术上下文**：Spring AI 1.1.4 + Ollama(deepseek-r1:32b) + GLM-5.2 + Redis VectorStore，混合检索(RRF 融合)，前端 Tailwind + marked.js + highlight.js + SSE 流式。本规范指导 `ka-webapp` 静态前端（index.html / admin.html）的重塑与后续页面生成。

---

## 1. Visual Theme & Atmosphere

把 knowledge-assistant 想象成一个**深夜里的检索实验台**：深炭黑的画布像熄灯的实验室，磷光绿的 accent 像示波器和终端光标，等宽字体像实验记录本的栏位。每一次问答不是「聊天」，而是一条**实验日志条目**——检索了哪些来源 `[1][2]`、推理了多久（deepseek-r1 trace）、生成了什么，全部可追溯。

**气质关键词**：精准、冷峻、可信、开发者向、克制。不是温暖的「AI 伙伴」，而是可靠的「检索仪器」。

**密度**：中等偏高。信息密集但层级清晰——元数据（模型 / 耗时 / source type）用 mono 小字贴边，正文留呼吸。绝不空旷到像「产品营销页」，也绝不拥挤到像 IDE。

**氛围细节**：
- 背景隐含**细密网格纹理**（1px line @ 32px，opacity 0.02），暗示坐标 / 实验台
- Header 底边有一条 1px phosphor green 发光线（opacity 0.6），像设备待机指示灯
- 代码块、think 块用**左边框 + 微微下沉的背景**区分层级，不用厚重阴影

---

## 2. Color Palette & Roles

全部 token 用 CSS 变量，语义命名。**深色优先（dark-first）**，所有页面默认深色。

```css
:root {
  /* ── Surfaces（深度层级，越往下越深）── */
  --ka-canvas:    #0A0E14;  /* 最底层画布 */
  --ka-surface:   #11161E;  /* 卡片 / 面板 */
  --ka-surface-2: #161B22;  /* user 气泡 / raised */
  --ka-sunken:    #0D1117;  /* 代码块（比 canvas 略深，内陷感） */

  /* ── Borders ── */
  --ka-border:        #21262D;
  --ka-border-strong: #30363D;

  /* ── Text ── */
  --ka-text:        #C9D1D9;  /* 正文 */
  --ka-text-muted:  #8B949E;  /* 次要 / 元数据 */
  --ka-text-faint:  #6E7681;  /* 占位 / 禁用 */
  --ka-text-bright: #F0F6FC;  /* 强调标题 */

  /* ── Accents（语义）── */
  --ka-accent: #7EE787;  /* 磷光绿 · 主操作 / 链接 / 来源编号 / 成功 */
  --ka-info:   #58A6FF;  /* 青 · 信息 / 外部链接 */
  --ka-tool:   #D2A8FF;  /* 紫 · 工具调用（Tool Calling）*/
  --ka-think:  #F0883E;  /* 琥珀 · 推理 trace / think 块 */
  --ka-danger: #FF7B72;  /* 红 · 错误 / 删除 */
  --ka-warn:   #E3B341;  /* 黄 · 警告（如上传失败）*/
}
```

**语义角色映射**（生成新组件时按此映射，勿自由发挥）：

| 角色 | token | 用途 |
|---|---|---|
| Primary action | `--ka-accent` (green) | 发送按钮、确认、活跃状态、来源编号 `[1]` |
| Info | `--ka-info` (cyan) | 提示、外部文档链接、SSE 连接状态 |
| Tool | `--ka-tool` (purple) | ReAct 工具调用气泡、`@Tool` 元数据 |
| Think | `--ka-think` (amber) | deepseek-r1 推理 trace 折叠块 |
| Danger | `--ka-danger` (red) | 删除对话、上传失败、错误消息 |

**对比度**：正文 `--ka-text` on `--ka-canvas` ≥ 12:1（AAA）。muted 文本仅用于非关键元数据。

---

## 3. Typography Rules

两套字体，职责分明。**禁止** Inter / Roboto / Arial / system-ui（AI slop 标配）。

```css
:root {
  --ka-font-sans: "IBM Plex Sans", "Noto Sans SC", system-ui, sans-serif;
  --ka-font-mono: "JetBrains Mono", "Noto Sans Mono CJK SC", ui-monospace, monospace;
}
```

- **IBM Plex Sans** — 标题 + 正文。带微弱人文气质（不像 Inter 那么中性无个性），有工程感。中文回退 Noto Sans SC（思源黑体）。
- **JetBrains Mono** — 代码、来源编号、trace、session id、时间戳、模型名、耗时、按钮 label 点缀。中文等宽回退 Noto Sans Mono CJK SC。

**Google Fonts 引入**（免费可商用，替换现状 system 字体）：
```html
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
```

**Type scale**（8px baseline）：

| Token | size / line | weight | 用途 |
|---|---|---|---|
| `--ka-fs-display` | 28px / 1.3 | 600 | 空状态大标题 |
| `--ka-fs-h1` | 20px / 1.4 | 600 | 页面标题（Header logo） |
| `--ka-fs-h2` | 16px / 1.4 | 600 | 对话区块标题 |
| `--ka-fs-body` | 15px / 1.7 | 400 | 正文（markdown 回答） |
| `--ka-fs-small` | 13px / 1.6 | 400 | 次要文本 |
| `--ka-fs-label` | 11px / 1.4 | 500 | mono · uppercase · letter-spacing 0.08em · 元数据 / 按钮点缀 |

**排版铁律**：
- 行高：正文 1.7（长回答阅读舒适），代码 1.5，元数据 label 1.4
- mono 与 sans 混排时，mono 字号比同级 sans 小 1px（视觉对齐基线）
- 数字、ID、时间戳、模型名**一律 mono**（`deepseek-r1:32b`、`1.2s`、`#abc123`、`conv-a1b2`）
- 段落间距 `margin: 0 0 .6em`，紧凑但不挤

---

## 4. Component Stylings

### 对话消息（核心）
不像传统「左右气泡」。**assistant 消息是日志条目**，user 消息是右对齐的简洁输入回显。

- **user 消息**：右对齐，`--ka-surface-2` 背景，4px 圆角，max-width 75%，前缀 mono `user ▍`（muted）
- **assistant 消息**：全宽，无气泡背景，前缀 mono `ka ▸`（accent green），下方 markdown 正文
- **元数据栏**（assistant 消息底部，mono · `--ka-fs-label` · muted）：`deepseek-r1:32b · 1.2s · 4 sources`
- **SSE 流式光标**：流式输出时末尾闪烁 `▍`（accent green，`@keyframes ka-blink`）

### 来源引用（招牌特性）
RAG 回答必须带来源编号——这是 knowledge-assistant 的核心可信度体现。
- **行内引用**：`[1]` `[2]` 上标，`--ka-accent` 色，可点击锚点跳到底部 References 区，hover 显示 tooltip（文档标题 + docId）
- **References 区**（assistant 消息末尾）：mono 编号列表 `[1] 知识库文档A.pdf · docId:abc · chunk 3/12`，每条 hover 高亮、点击打开原文片段

### think 块（推理 trace）
deepseek-r1 的推理过程默认折叠，不刷屏。
```
▸ reasoning trace · 1.2s     [展开 / 折叠]
```
- 折叠条：`--ka-think`（amber）左边框 2px，mono label，hover 展开手势
- 展开后：内嵌 `--ka-sunken` 背景，mono 推理文本，muted 色，左侧 amber 竖线

### 代码块
替换现状 `github.min.css` 为 **github-dark** 主题。
- 背景 `--ka-sunken`，JetBrains Mono，13px，line-height 1.5
- 左侧 2px `--ka-border` gutter，行号 muted
- 圆角 4px，右上角 mono「copy」按钮（hover 显形）

### 输入 dock（底部）
终端风，不是圆角搜索框。
- 单行 input + 多行自动扩展，背景 `--ka-surface`，1px `--ka-border`，4px 圆角
- focus 时底部边框变 2px `--ka-accent`，微微 glow `0 0 0 3px rgba(126,231,135,.12)`
- placeholder：mono · muted · `▍ ask anything…`（带光标字符）
- 发送按钮：`--ka-accent` 实心，mono uppercase `SEND`，hover 加亮 + glow
- Stream toggle：ghost 按钮，ON 时文本 `--ka-accent`，OFF 时 muted

### Header
- `--ka-surface` 背景，底部 1px `--ka-border`，再下 1px `--ka-accent` 发光线（opacity 0.6）
- logo：mono `▌ KA` + sans `Knowledge Assistant`，`--ka-text-bright`
- 右侧：ghost 按钮（History / Admin），mono label

### 侧边栏（对话列表）
- `--ka-surface` 背景，右边框 `--ka-border`
- 每条对话：mono 时间戳（muted，`06-26 14:33`）+ 首 question 摘要（sans，单行截断）
- hover / active：左侧 2px `--ka-accent` 竖条 + 背景 `--ka-surface-2`

### 加载 / 空状态
- 检索中：磷光绿扫描线动画（`@keyframes ka-scan`，横向 2s 循环）+ mono `retrieving…`
- 空对话：居中 mono `▌ awaiting input` + sans 副标题，背景细网格更明显

---

## 5. Layout Principles

**三段式骨架**（index.html）：`<header>` / `<main messages>` / `<footer input dock>`，主区 flex-1 滚动。

- **阅读宽度**：messages 区内容 max-width **820px** 居中（长 markdown 回答舒适），左右 auto margin；代码块允许横向溢出滚动
- **侧边栏**：固定 288px（w-72），可折叠收成图标条；展开时主区让出空间
- **间距 scale**（8px baseline）：`4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`，全局只用这些值
- **圆角**：克制——`4px`（按钮 / 输入 / 代码）、`6px`（卡片 / 气泡）。**禁止 > 8px 圆角**（破坏终端冷峻感）
- **对齐**：所有元素对齐 8px grid；mono 与 sans 混排时按基线（baseline）对齐而非中线

---

## 6. Depth & Elevation

深色主题**不用厚重阴影**（深色上阴影不可见且显脏）。用「背景层级 + 边框 + 微 glow」表达深度。

**层级栈**（从底到顶）：
1. `canvas` (-0) — 最底层，网格纹理
2. `surface` (+1) — header / sidebar / dock，`1px border`
3. `surface-2` (+2) — user 气泡 / hover 态，`1px border-strong`
4. `sunken` (-1) — 代码块 / think 块，**比 canvas 更深**（内陷），靠左边框区分

**Glow（accent 元素专属）**：聚焦 / 活跃 / 重要 accent 元素加 ring + soft glow：
```css
.ka-focus-ring {
  box-shadow: 0 0 0 1px var(--ka-accent), 0 0 12px rgba(126, 231, 135, .15);
}
```
仅用于：发送按钮 focus、活跃来源编号、SSE 连接指示灯。**不要全屏 glow**（廉价感）。

---

## 7. Do's and Don'ts

**Do**：
- ✅ 所有元数据（模型 / 耗时 / ID / 时间 / 数字）用 **mono** 字体
- ✅ RAG 回答**必带来源编号** `[1][2]`，底部必有 References 区
- ✅ think 推理块**默认折叠**，琥珀色标示
- ✅ 深色背景配 light text，对比度 ≥ AAA
- ✅ 用背景层级 + 边框表达深度，不用厚阴影
- ✅ accent 绿用于「可交互 / 可追溯」元素（链接 / 编号 / 确认）

**Don't**：
- ❌ **禁止**蓝紫渐变白底（白底 `#FFF` + `linear-gradient(purple)`）——典型 AI slop
- ❌ **禁止** Inter / Roboto / Arial / system-ui 字体
- ❌ **禁止**圆角 > 8px（破坏终端冷峻）
- ❌ **禁止**无编号的来源（「根据文档」必须改成 `[1]`）
- ❌ **禁止**把 deepseek-r1 推理直接展开刷屏（必须折叠）
- ❌ **禁止**用厚重的 box-shadow（深色主题显脏）
- ❌ **禁止**把对话做成「营销首页」式的空旷大留白

---

## 8. Responsive Behavior

| 断点 | 宽度 | 行为 |
|---|---|---|
| Desktop | ≥ 1024px | 侧边栏常驻 + messages 820px 居中 |
| Tablet | 768–1023px | 侧边栏改抽屉（汉堡触发，overlay） |
| Mobile | < 768px | 单列；header 精简（只留 logo + menu）；input dock 全宽；messages 取消 max-width；代码块横向滚动 |

**触控**：所有可点元素 ≥ 44×44px 命中区（移动端按钮加大）。来源编号 `[1]` 在移动端加大命中 padding。

**SSE**：流式输出在移动端关闭光标 blink 动画（`prefers-reduced-motion` + 移动端省电）。

---

## 9. Agent Prompt Guide

> 本段嵌入未来生成的 `SKILL.md`。当 Claude 为 knowledge-assistant 生成任何新页面 / 组件时，必须遵循。

**生成指令模板**：
> 为 knowledge-assistant 生成 [页面 / 组件]。遵循 DESIGN.md 的 **Terminal-Lab Dark** 调性：深炭黑画布(#0A0E14)、磷光绿 accent(#7EE787)、IBM Plex Sans + JetBrains Mono。把界面当成「实验日志」——元数据用 mono、来源必编号、推理必折叠。禁止白底蓝紫渐变、禁止 Inter 字体、禁止大圆角。

**自检清单**（生成后逐条核对）：
- [ ] 字体是 IBM Plex Sans + JetBrains Mono？（非 Inter / Roboto / system）
- [ ] RAG 回答带了 `[1][2]` 来源编号 + References 区？
- [ ] think 推理块默认折叠、琥珀色？
- [ ] 元数据（模型 / 耗时 / ID）用了 mono？
- [ ] 配色是深炭黑 + 磷光绿，**没有**白底蓝紫渐变？
- [ ] 圆角 ≤ 6px？
- [ ] 深度靠背景层级 + 边框，不是厚阴影？

**招牌记忆点**（每个页面至少命中一个）：
1. 来源编号可点击脚注（RAG 可信度）
2. 推理 trace 折叠块（deepseek-r1 透明性）
3. SSE 终端打字光标 `▍`（流式生命力）
4. 网格 / 扫描线背景纹理（实验台氛围）

---

*Version 1.0 · 2026-06-26 · Terminal-Lab Dark · 参考 Ollama / Warp / Vercel / Linear 的开发者工具气质，为 knowledge-assistant 的 RAG + ReAct 本质量身定制。*
