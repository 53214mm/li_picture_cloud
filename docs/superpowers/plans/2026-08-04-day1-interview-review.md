# Day 1 Interview Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 `docs/interview-daily/day1.md`，完整沉淀 Day 1 模拟面试的题目、答题方法、标准答案和复习重点。

**Architecture:** 使用单一 Markdown 文件作为每日复盘单元。正文按真实面试顺序组织 10 道正式题和 1 道最终纠错题，每道正式题采用相同结构，末尾集中提供项目介绍与复习清单。

**Tech Stack:** Markdown、PowerShell 文本检查、Git。

## Global Constraints

- 文件路径必须为 `docs/interview-daily/day1.md`。
- 中文为主，术语保留英文名称。
- 只包含 Day 1 实际问过的内容，不提前引入 Day 2。
- 不夸大线上数据、分表成果或个人贡献。
- 标准答案适合口述，但不要求逐字背诵。

---

### Task 1: 创建 Day 1 面试复盘

**Files:**
- Create: `docs/interview-daily/day1.md`
- Reference: `docs/interview/01-项目全流程与自我介绍.md`
- Reference: `docs/interview/03-Java与框架高频保命题.md`
- Reference: `docs/superpowers/specs/2026-08-04-day1-interview-review-design.md`

**Interfaces:**
- Consumes: 本次对话中的 10 道正式题、用户回答、逐题纠正和最终 90 秒项目介绍。
- Produces: 可独立阅读和每日复习的 `docs/interview-daily/day1.md`。

- [ ] **Step 1: 创建文档骨架**

写入标题、使用方法、成绩总结，以及编号 1～10 的正式题和“最终纠错题”标题。每道正式题固定包含“原题、推荐答题结构、标准答案、今日易错点、继续追问”。

- [ ] **Step 2: 填充项目与数据库题**

完成项目介绍、`space_user`、联合唯一索引、请求链、分层职责、DTO/Entity/VO、联合索引七道题。标准答案必须明确：事务不能代替唯一约束，`SELECT *` 通常不能形成覆盖索引。

- [ ] **Step 3: 填充 Java 基础题**

完成 B+Tree、`HashMap.put`、`ArrayList` 与 `LinkedList` 三道题。必须包含树高与磁盘 I/O、树化条件、负载因子、随机访问和 CPU 缓存局部性。

- [ ] **Step 4: 填充最终复盘**

加入最终 90 秒项目介绍参考版、四个重点纠错项和进入 Day 2 前的复习清单。

- [ ] **Step 5: 验证结构与链接**

Run:

```powershell
rg -n "^## 第 (1|2|3|4|5|6|7|8|9|10) 题|^## 最终纠错题|推荐答题结构|标准答案|今日易错点|继续追问|90 秒项目介绍|复习清单" docs/interview-daily/day1.md
git diff --check -- docs/interview-daily/day1.md
```

Expected: 10 道正式题和最终纠错题都可检索；Markdown 无空白错误；所有相对链接指向现有文件。

- [ ] **Step 6: 提交文档**

```powershell
git add -- docs/interview-daily/day1.md
git commit -m "docs: add day one interview review"
```
