# Day 2 Interview Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建完整、可口述复习的 `docs/interview-daily/day2.md`。

**Architecture:** 复用 Day 1 的单文件复盘结构，按当天真实问答顺序组织内容。只新增 Markdown 文件，并使用静态文档检查验收。

**Tech Stack:** Markdown、PowerShell、Git。

## Global Constraints

- 空间容量按软上限解释，不描述为实现缺陷。
- 说明小幅超额依赖单文件大小限制。
- 不提前引入 Day 3 内容。
- 不运行 Maven、npm 测试或构建。
- 不提交工作区其他改动。

---

### Task 1: 创建 Day 2 复盘文档

**Files:**
- Create: `docs/interview-daily/day2.md`
- Reference: `docs/interview-daily/day1.md`
- Reference: `docs/interview/02-核心技术补课.md`
- Reference: `docs/superpowers/specs/2026-08-04-day2-interview-review-design.md`

**Interfaces:**
- Consumes: Day 2 实际问答、评分和纠错内容。
- Produces: 独立可读的 Day 2 面试复盘文档。

- [ ] **Step 1: 编写图片上传与一致性题**

收录上传链路、COS/MySQL 事务边界、补偿方案、上传顺序、重新上传一致性和软额度说明。

- [ ] **Step 2: 编写数据库原理题**

收录 ACID、事务异常、隔离级别、MVCC、快照读/当前读和额度并发控制。

- [ ] **Step 3: 编写排错与总结**

收录慢 SQL、图库分析空数据、前端有数据不渲染、最终评分、重点纠错和复习清单。

- [ ] **Step 4: 静态验证**

```powershell
rg -n "推荐答题结构|标准答案|今日易错点|继续追问|软上限|MVCC|慢 SQL|图库分析" docs/interview-daily/day2.md
git diff --check -- docs/interview-daily/day2.md
```

Expected: 固定栏目、关键主题和软额度口径均存在，Markdown 无空白错误，相对链接有效。不要运行业务测试。

- [ ] **Step 5: 提交**

```powershell
git add -- docs/interview-daily/day2.md
git commit -m "docs: add day two interview review"
```
