# LiPictureCloud Interview Preparation Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build six Chinese Markdown workbooks that help a Java backend internship/campus candidate understand, explain, and defend the LiPictureCloud project within a ten-day preparation sprint.

**Architecture:** Organize the material as a dependency chain: learning guide → project narrative → technical lessons → foundation questions → project drill-down questions → ten-day simulations. Every project claim is backed by a repository path, and every learning chapter ends in active-recall exercises rather than passive reading.

**Tech Stack:** Markdown, Java 21, Spring Boot, Spring MVC, MyBatis-Plus, Sa-Token, MySQL, Redis, Lua, WebSocket, ShardingSphere, Spring AI, MCP, Docker Compose, Nginx

## Global Constraints

- Target Java backend internship/campus interviews.
- Assume 1–2 weeks of preparation and 5–6 study hours per day.
- Use beginner-friendly Chinese while retaining English technical terms.
- Treat current repository code as the only source of truth for project claims.
- Clearly distinguish completed features, optional profiles, and future plans.
- Do not invent QPS, concurrency, coverage, latency, or performance-improvement numbers.
- Do not include credentials, server login details, personal contact details, or production `.env` values.
- Each core project lesson must provide repository-relative source entry points without fixed line numbers.
- Each standard answer must include key terms, dangerous overclaims, and an honest fallback response.
- Keep full DDD migration, deep JVM tuning, Spring source walkthroughs, and distributed transaction systems outside the required ten-day scope.

---

## File Map

- Create `docs/interview/00-冲刺使用指南.md`: navigation, priorities, daily method, active-recall and mistake-log templates.
- Create `docs/interview/01-项目全流程与自我介绍.md`: project pitches, domain model, five end-to-end flows, ownership language.
- Create `docs/interview/02-核心技术补课.md`: Spring MVC/Boot, authorization, WebSocket, Redis Lua/Pub/Sub, sharding, Nginx, AI/MCP.
- Create `docs/interview/03-Java与框架高频保命题.md`: Java, concurrency, JVM, Spring, MySQL, Redis high-frequency foundations.
- Create `docs/interview/04-项目追问题库.md`: L1–L4 questions, answers, red flags, source references, honest fallbacks.
- Create `docs/interview/05-十天训练与模拟面试.md`: ten-day schedule, scoring rubric, three mock interviews, review forms.
- Modify `README.md`: add one link to the interview-document entry point under documentation navigation.

## Evidence Sources

- `README.md`: verified project scope and architecture overview.
- `src/main/java/com/li/lipicturecloud/controller/`: HTTP and SSE entry points.
- `src/main/java/com/li/lipicturecloud/service/impl/`: picture, space, member, analysis, and user services.
- `src/main/java/com/li/lipicturecloud/manager/auth/`: authorization model and permission mapping.
- `src/main/java/com/li/lipicturecloud/collaboration/`: WebSocket, state store, event bus, and collaboration services.
- `src/main/resources/redis/`: authoritative collaboration Lua scripts.
- `src/main/java/com/li/lipicturecloud/sharding/` and `src/main/resources/sharding/`: sharding algorithm and rules.
- `src/main/java/com/li/lipicturecloud/AI/`: AI intent, memory, Agent, MCP callback, and generated-image persistence.
- `compose.yaml` and `deploy/nginx/`: production topology and gateway behavior.
- `src/test/java/`: executable evidence for the interview claims.

### Task 1: Create the sprint entry guide

**Files:**
- Create: `docs/interview/00-冲刺使用指南.md`
- Read: `docs/superpowers/specs/2026-08-03-interview-preparation-guide-design.md`

**Interfaces:**
- Consumes: learner profile, priority levels, ten-day order, and scoring rubric from the approved design.
- Produces: the navigation and study contract used by all five later workbooks.

- [ ] **Step 1: Create navigation and a clear minimum-success definition**

Include links to all six files and this minimum bar:

```text
能脱稿讲完 2 分钟项目介绍
能画出请求、上传、协同和 AI 生图的数据流
能回答每个红色技术点的一句话定义和项目用途
每个核心亮点至少完成三层追问
三套模拟卷中至少两套达到 60 分
```

- [ ] **Step 2: Add priority and time-allocation tables**

Copy the approved red/yellow/green/gray priorities exactly. Add the daily allocation:

```text
2 小时教程与源码
1.5 小时脱稿回答
1 小时基础题
1 小时模拟和复盘
0.5 小时改写成自己的表达
```

- [ ] **Step 3: Add active-recall templates**

Provide reusable Markdown tables for:

```text
知识卡：概念 / 项目位置 / 流程 / 原理 / 边界
错题卡：原问题 / 我的回答 / 漏掉关键词 / 下次回答
源码卡：入口 / 下游调用 / 数据变化 / 异常路径
```

Add the rule: read once, close the document, answer aloud, then compare.

- [ ] **Step 4: Validate and commit Task 1**

Run:

```powershell
rg -n "最低完成标准|红色必补|每日时间|主动回忆|错题" docs/interview/00-冲刺使用指南.md
git diff --check -- docs/interview/00-冲刺使用指南.md
```

Expected: all five concepts are present and no whitespace errors occur.

Commit:

```powershell
git add -- docs/interview/00-冲刺使用指南.md
git commit -m "docs: add interview sprint learning guide"
```

### Task 2: Explain the project and its end-to-end flows

**Files:**
- Create: `docs/interview/01-项目全流程与自我介绍.md`
- Read: `README.md`
- Read: `src/main/java/com/li/lipicturecloud/controller/PictureController.java`
- Read: `src/main/java/com/li/lipicturecloud/controller/SpaceUserController.java`
- Read: `src/main/java/com/li/lipicturecloud/controller/AIController.java`
- Read: `src/main/java/com/li/lipicturecloud/service/impl/PictureServiceImpl.java`
- Read: `src/main/java/com/li/lipicturecloud/service/impl/SpaceAnalyzeServiceImpl.java`

**Interfaces:**
- Consumes: repository architecture and verified feature boundaries.
- Produces: the business and code-flow vocabulary assumed by technical lessons and question banks.

- [ ] **Step 1: Write three project pitches**

Create 30-second, 2-minute, and 5-minute versions. Each must cover:

```text
who uses the product
what core problem it solves
the three-space model
one collaboration highlight
one AI highlight
one engineering/deployment highlight
```

The 5-minute version must end with “项目当前边界与下一步”.

- [ ] **Step 2: Explain the domain and database relationships**

Use a Mermaid ER-style diagram for User, Picture, Space, and SpaceUser. Explain public pictures (`spaceId = null`), owned private/team spaces, and team membership roles.

- [ ] **Step 3: Explain the common request path**

Document:

```text
Browser → Nginx → Vue request wrapper → Controller → authorization/AOP → Service → Mapper → MySQL/Redis/COS → response wrapper → Vue
```

Clarify the responsibilities of Controller, Service, Mapper, DTO, Entity, and VO in this repository.

- [ ] **Step 4: Explain five complete business flows**

For each flow provide trigger, request data, key classes, database/cache changes, response, and failure path:

1. Picture upload and COS persistence.
2. Team-space viewing and permission resolution.
3. Collaborative edit command and event delivery.
4. AI chat, MCP image generation, and user-space persistence.
5. Gallery analysis request and aggregation.

- [ ] **Step 5: Add ownership and honesty language**

Provide answers to:

```text
你负责了什么？
哪些代码使用了 AI 辅助？
项目最难的问题是什么？
项目有哪些不足？
如果重做你会改什么？
```

The answer must emphasize requirement breakdown, design decisions, verification, deployment, and bug diagnosis without claiming every line was handwritten.

- [ ] **Step 6: Validate paths and commit Task 2**

Run:

```powershell
rg -n "30 秒|2 分钟|5 分钟|图片上传链路|团队空间|协同编辑链路|AI 生图链路|图库分析链路|你负责了什么" docs/interview/01-项目全流程与自我介绍.md
git diff --check -- docs/interview/01-项目全流程与自我介绍.md
```

Expected: all three pitches, five flows, and ownership section are present.

Commit:

```powershell
git add -- docs/interview/01-项目全流程与自我介绍.md
git commit -m "docs: explain project interview flows"
```

### Task 3: Write the core technology lessons

**Files:**
- Create: `docs/interview/02-核心技术补课.md`
- Read: `src/main/java/com/li/lipicturecloud/manager/auth/`
- Read: `src/main/java/com/li/lipicturecloud/collaboration/`
- Read: `src/main/resources/redis/collaboration-apply.lua`
- Read: `src/main/resources/redis/collaboration-current.lua`
- Read: `src/main/java/com/li/lipicturecloud/sharding/DynamicPictureShardingAlgorithm.java`
- Read: `src/main/resources/sharding/static.yaml`
- Read: `src/main/resources/sharding/dynamic.yaml`
- Read: `src/main/java/com/li/lipicturecloud/AI/`
- Read: `deploy/nginx/lipicturecloud-domain-https.conf`

**Interfaces:**
- Consumes: project flows from Task 2 and repository implementations.
- Produces: technical explanations and answer templates referenced by Task 4 and Task 5.

- [ ] **Step 1: Write Spring MVC, Spring Boot, and authorization lessons**

Cover DispatcherServlet request flow, IOC/AOP roles, Spring Boot startup, `@SpringBootApplication`, auto-configuration imports, conditional configuration, Sa-Token login state, Spring Session, and project permission codes.

- [ ] **Step 2: Write the WebSocket lesson**

Explain HTTP Upgrade, handshake interceptor, WebSocketSession, handler lifecycle, browser reconnect, why WebSocket differs from a message queue, and the exact collaboration path in this project.

- [ ] **Step 3: Write Redis Lua and Pub/Sub lessons**

Explain Redis single-threaded command execution, Lua atomicity limits, expected version, command ID idempotency, state TTL, Pub/Sub non-persistence, and why Lua updates state before Java publishes the event.

- [ ] **Step 4: Write ShardingSphere lessons**

Cover horizontal partitioning, logical/actual tables, sharding key, routing, broadcast/range-query concerns, static YAML routing, dynamic algorithm routing, profiles, and the no-sharding default. Include a small modulo-routing example but do not claim production is sharded.

- [ ] **Step 5: Write Nginx, Docker, and AI/MCP lessons**

Explain reverse proxying, SSE buffering, WebSocket upgrade headers, Docker internal networking, AI memory, Agent/tool flow, MCP callbacks, and the shared-callback user-context bug fixed in the project.

- [ ] **Step 6: Apply the lesson template consistently**

Each of these subjects must contain:

```text
一句话是什么
项目为什么需要
项目位置
完整流程
核心原理
标准回答
三层追问
容易说错
诚实收口
源码入口
```

- [ ] **Step 7: Validate and commit Task 3**

Run:

```powershell
rg -n "DispatcherServlet|自动配置|HTTP Upgrade|WebSocketSession|Lua|expectedVersion|Pub/Sub|分片键|DynamicPictureShardingAlgorithm|proxy_buffering|MCP|诚实收口" docs/interview/02-核心技术补课.md
git diff --check -- docs/interview/02-核心技术补课.md
```

Expected: all high-risk subjects and the fallback section are present.

Commit:

```powershell
git add -- docs/interview/02-核心技术补课.md
git commit -m "docs: add core project technology lessons"
```

### Task 4: Build the Java and framework survival question set

**Files:**
- Create: `docs/interview/03-Java与框架高频保命题.md`

**Interfaces:**
- Consumes: learner gaps defined in the approved design.
- Produces: concise foundation answers used in the ten-day practice schedule.

- [ ] **Step 1: Add Java collections and concurrency questions**

Include ArrayList, LinkedList, HashMap, ConcurrentHashMap, equals/hashCode, thread creation, thread pools and rejection policies, synchronized, ReentrantLock, volatile, JMM, CAS, ThreadLocal, and deadlocks.

- [ ] **Step 2: Add JVM and exception questions**

Include runtime memory areas, object creation, class loading, parent delegation, GC roots, young/old generation basics, common OOM causes, checked/runtime exceptions, and try/finally behavior.

- [ ] **Step 3: Add Spring and Spring Boot questions**

Include IOC, DI, AOP, proxy choice, Bean lifecycle, singleton safety, transaction propagation, rollback rules, self-invocation failure, startup flow, auto-configuration, and conditional annotations.

- [ ] **Step 4: Add MySQL and Redis questions**

Include B+Tree, clustered/secondary indexes, back-to-table lookup, covering index, leftmost prefix, transaction ACID, isolation, MVCC, INNER/LEFT JOIN basics, slow-query diagnosis, Redis structures, persistence, expiration, eviction, penetration, breakdown, avalanche, and consistency.

- [ ] **Step 5: Use two answer depths and project connections**

Every question must have:

```text
一句话回答
60 秒回答
常见追问
项目连接点（when applicable）
```

- [ ] **Step 6: Validate and commit Task 4**

Run:

```powershell
rg -n "HashMap|ConcurrentHashMap|线程池|JMM|类加载|GC Roots|IOC|Bean 生命周期|事务失效|自动配置|B\+Tree|MVCC|LEFT JOIN|缓存穿透" docs/interview/03-Java与框架高频保命题.md
git diff --check -- docs/interview/03-Java与框架高频保命题.md
```

Expected: all red and yellow foundation categories are represented.

Commit:

```powershell
git add -- docs/interview/03-Java与框架高频保命题.md
git commit -m "docs: add Java backend interview survival questions"
```

### Task 5: Build the project drill-down question bank

**Files:**
- Create: `docs/interview/04-项目追问题库.md`
- Read: `src/test/java/`

**Interfaces:**
- Consumes: project flow vocabulary from Task 2 and technical answers from Task 3.
- Produces: scored L1–L4 project questions used by mock interviews in Task 6.

- [ ] **Step 1: Add grouped L1–L4 questions**

Create sections for project overview, picture/COS, permissions, WebSocket collaboration, Redis/Lua, sharding, AI/MCP, analysis, security, deployment, debugging, and authenticity.

- [ ] **Step 2: Use the complete question template**

For each major question include:

```text
问题
参考回答
合格关键词
危险回答
继续追问（at least two）
诚实收口
源码入口
```

- [ ] **Step 3: Include incident-based questions**

Cover real project incidents:

```text
Maven Wrapper permission denied in CI
Redis password mismatch
team viewer could list but not open a picture
viewer needed read-only collaboration
AI progress text appeared for non-image requests
AI images were saved under the wrong user
gallery analysis loaded before current-user hydration
server rebuilt an old frontend image after git pull/build failure
```

- [ ] **Step 4: Add a rapid-fire checklist**

Add 30 short questions without answers first, followed by a separate answer key, so the learner cannot read the answer accidentally during recall.

- [ ] **Step 5: Validate and commit Task 5**

Run:

```powershell
rg -n "L1|L2|L3|L4|合格关键词|危险回答|诚实收口|源码入口|Permission denied|用户上下文|只读协同|hydration|快速问答" docs/interview/04-项目追问题库.md
git diff --check -- docs/interview/04-项目追问题库.md
```

Expected: layered questions, real incidents, and rapid-fire sections are present.

Commit:

```powershell
git add -- docs/interview/04-项目追问题库.md
git commit -m "docs: add project interview drill-down bank"
```

### Task 6: Create the ten-day schedule and mock interviews

**Files:**
- Create: `docs/interview/05-十天训练与模拟面试.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: all five earlier workbooks.
- Produces: daily execution tasks, three scored mock interviews, review forms, and repository navigation.

- [ ] **Step 1: Add ten daily plans**

Each day must include:

```text
learning target
required reading links
source files to inspect
questions to answer aloud
one drawing/explanation exercise
pass/fail acceptance criteria
```

- [ ] **Step 2: Add the 100-point scoring rubric and review form**

Use the approved weights: business/project 25, principles 25, Java/Spring/MySQL 25, design/debugging 15, communication/honesty 10. Add a per-question record with score, missing keywords, corrected answer, and retry date.

- [ ] **Step 3: Add three mock interview papers**

Create:

1. Friendly first-round screening: self-introduction, project, Java/Spring/MySQL basics.
2. Project deep-dive: permissions, collaboration, Redis, sharding, AI, deployment.
3. Pressure interview: alternatives, failure modes, unsupported claims, AI-assisted development, and trade-offs.

Each paper must include interviewer script, follow-up branches, scoring points, and post-interview answer guide.

- [ ] **Step 4: Add panic-control and unknown-question scripts**

Provide a short pre-interview routine and this answer structure:

```text
先确认问题范围
回答确定掌握的定义和项目实践
说明未深入的边界
给出验证或学习方式
不编造源码和数据
```

- [ ] **Step 5: Add README navigation**

Under `README.md` → `文档导航`, add:

```markdown
- [校招面试十天冲刺指南](docs/interview/00-冲刺使用指南.md)
```

- [ ] **Step 6: Validate all six workbooks**

Run:

```powershell
$files = Get-ChildItem docs/interview -File -Filter '*.md'
if ($files.Count -ne 6) { throw "Expected 6 interview files, found $($files.Count)" }
$all = $files | Get-Content -Raw
foreach ($day in 1..10) { if ($all -notmatch "第 $day 天") { throw "Missing day $day" } }
rg -n "友好的一面|项目连续深挖|压力面|业务理解与项目流程|诚实" docs/interview/05-十天训练与模拟面试.md
```

Expected: six files, ten days, three mock papers, scoring, and honesty guidance exist.

- [ ] **Step 7: Validate all repository-relative Markdown links**

Run:

```powershell
$docs = @('README.md') + (Get-ChildItem docs/interview -File -Filter '*.md').FullName
foreach ($doc in $docs) {
  $base = Split-Path $doc
  if (-not $base) { $base = '.' }
  $text = Get-Content -Raw $doc
  foreach ($match in [regex]::Matches($text, '\[[^\]]+\]\((?!https?://|#)([^)]+)\)')) {
    $target = [uri]::UnescapeDataString($match.Groups[1].Value)
    if (-not (Test-Path -LiteralPath (Join-Path $base $target))) { throw "Missing link in $doc`: $target" }
  }
}
```

Expected: exit code 0 with no missing links.

- [ ] **Step 8: Scan for secrets and unsupported claims**

Run:

```powershell
rg -n "1198894955|82\.156\.66\.244|QPS|百万并发|提升[0-9]+%|覆盖率[0-9]+%|SECRET_KEY\s*[:=]|PASSWORD\s*[:=]" docs/interview README.md
```

Expected: no output.

- [ ] **Step 9: Commit and push Task 6**

Run:

```powershell
git diff --check -- docs/interview README.md
git add -- docs/interview/05-十天训练与模拟面试.md README.md
git commit -m "docs: add ten-day interview practice plan"
git push origin main
```

Expected: the final commit contains only the simulation guide and README navigation; all earlier workbook commits are pushed with it.
