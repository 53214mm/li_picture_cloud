# Day 4 Survival Questions Supplement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐《Java 与框架高频保命题》中 Day 4 所需的 TCP、HTTP 与线程安全集合知识，并保持现有1～40题编号稳定。

**Architecture:** 只修改现有面试总册，在Redis章节和最终速记之间插入独立的Day 4专项，使用 `D4-1`～`D4-10` 编号。复用现有“一句话回答、60秒回答、常见追问、项目连接点”结构，最后扩充速记句，不复制Day 4协同业务的完整实现讲解。

**Tech Stack:** Markdown、ripgrep、Git diff检查

## Global Constraints

- 仅修改 `docs/interview/03-Java与框架高频保命题.md`。
- 保留现有1～40题编号。
- 新增题目使用 `D4-1`～`D4-10`。
- 面向Java后端实习/校招，不展开拥塞控制、TLS密码套件或JDK源码逐行分析。
- WebSocket只讲TCP与HTTP Upgrade基础；协同房间、Redis Pub/Sub和Lua留给Day 4专项训练。
- 不改动 `docs/interview-daily/day1.md`、`day2.md` 和 `day3.md`。

---

### Task 1: 新增TCP与HTTP基础题

**Files:**
- Modify: `docs/interview/03-Java与框架高频保命题.md`

**Interfaces:**
- Consumes: 现有Redis章节结尾与最终速记章节边界。
- Produces: `## 七、Day 4 专项：网络与线程安全集合` 中的 `D4-1`～`D4-7`。

- [ ] **Step 1: 在Redis章节后插入网络专项题**

题目严格覆盖：

```text
D4-1 TCP 与 UDP 的区别
D4-2 TCP 三次握手以及为什么不是两次
D4-3 TCP 四次挥手、TIME_WAIT、CLOSE_WAIT
D4-4 HTTP 特点与一次请求过程
D4-5 HTTP 方法、幂等性、状态码、长连接
D4-6 HTTP/1.1、HTTP/2、HTTPS
D4-7 WebSocket 的 HTTP Upgrade
```

每题必须包含“一句话回答”和“60秒回答”；容易被追问的题增加“常见追问”，WebSocket题增加LiPictureCloud项目连接点。

- [ ] **Step 2: 检查网络关键词覆盖**

Run:

```powershell
rg -n "TCP|UDP|三次握手|四次挥手|TIME_WAIT|CLOSE_WAIT|HTTP/1.1|HTTP/2|HTTPS|Upgrade" docs/interview/03-Java与框架高频保命题.md
```

Expected: 每个关键词至少命中一次，并且位于新增Day 4章节。

---

### Task 2: 新增线程安全集合选型题

**Files:**
- Modify: `docs/interview/03-Java与框架高频保命题.md`

**Interfaces:**
- Consumes: 原第4题ConcurrentHashMap、第12题ThreadLocal的详细解释。
- Produces: Day 4专项中的 `D4-8`～`D4-10`，用于集合选型和项目映射。

- [ ] **Step 1: 编写三个线程安全专题题目**

题目严格覆盖：

```text
D4-8 常见线程安全集合如何选型
D4-9 ConcurrentHashMap原理与复合操作边界
D4-10 CopyOnWriteArrayList、BlockingQueue、ThreadLocal的场景与风险
```

必须讲清：

- `Collections.synchronizedXxx` 使用统一互斥包装，迭代仍需按约定同步；
- ConcurrentHashMap适合并发Map，但“先get再put”不是复合原子操作；
- CopyOnWriteArrayList适合读多写少，写入复制数组，不能用于高频写；
- BlockingQueue适合生产者消费者和线程池任务交接，不等同于可靠消息队列；
- ThreadLocal不是集合，放在同题只用于对比“线程隔离”和“共享容器”，并强调 `finally remove()`。

- [ ] **Step 2: 检查集合关键词覆盖**

Run:

```powershell
rg -n "Collections\.synchronized|ConcurrentHashMap|putIfAbsent|compute|CopyOnWriteArrayList|BlockingQueue|ThreadLocal|finally.*remove" docs/interview/03-Java与框架高频保命题.md
```

Expected: 所有关键词命中；原第4题和第12题仍存在，新专项通过引用和选型补充而非删除原题。

---

### Task 3: 更新速记与完成静态校验

**Files:**
- Modify: `docs/interview/03-Java与框架高频保命题.md`

**Interfaces:**
- Consumes: Task 1和Task 2的专项题。
- Produces: 更新后的“八、最后20分钟速记”和可提交的Markdown文档。

- [ ] **Step 1: 调整最终章节编号并扩充速记**

把：

```markdown
## 七、最后 20 分钟速记
```

改为：

```markdown
## 八、最后 20 分钟速记
```

在原12句基础上补入最低记忆点：TCP可靠字节流、三次握手、四次挥手、HTTP无状态请求响应、HTTPS、WebSocket Upgrade、线程安全集合按访问模式选型。

- [ ] **Step 2: 验证原题号没有被修改**

Run:

```powershell
rg -n "^### (1|4|12|24|40)\." docs/interview/03-Java与框架高频保命题.md
```

Expected: 原第1、4、12、24、40题全部存在且标题不变。

- [ ] **Step 3: 验证专项题数量**

Run:

```powershell
(rg "^### D4-[0-9]+\." docs/interview/03-Java与框架高频保命题.md | Measure-Object).Count
```

Expected: `10`。

- [ ] **Step 4: 检查Markdown差异**

Run:

```powershell
git diff --check -- docs/interview/03-Java与框架高频保命题.md
git diff --stat -- docs/interview/03-Java与框架高频保命题.md
```

Expected: `git diff --check` 无输出；diff只涉及目标文档。

- [ ] **Step 5: 单独提交目标文档**

```powershell
git add -- docs/interview/03-Java与框架高频保命题.md
git commit -m "docs: add day4 network and concurrent collections questions"
```
