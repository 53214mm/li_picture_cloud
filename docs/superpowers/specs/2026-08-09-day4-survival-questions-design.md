# Day 4 网络与线程安全集合补课设计

## 背景

`docs/interview/05-十天训练与模拟面试.md` 将“TCP 与 HTTP 基础、线程安全集合、ConcurrentHashMap、ThreadLocal”列为 Day 4 八股，但 `docs/interview/03-Java与框架高频保命题.md` 目前只有 ConcurrentHashMap 和 ThreadLocal 单题，缺少 TCP、HTTP 以及线程安全集合的系统选型内容。

## 目标

在不改变现有 1～40 题编号的前提下，为《Java 与框架高频保命题》增加一个可直接用于 Day 4 学习和模拟面试的专项章节，并补充最后 20 分钟速记。

## 内容范围

新增“Day 4 专项：网络与线程安全集合”，使用独立题号 `D4-1`～`D4-10`：

1. TCP 与 UDP 的区别及使用场景。
2. TCP 三次握手及为什么不是两次。
3. TCP 四次挥手、TIME_WAIT 与 CLOSE_WAIT。
4. HTTP 的特点以及一次请求的大致过程。
5. HTTP 常用方法、幂等性、状态码和长短连接。
6. HTTP/1.1、HTTP/2、HTTPS 的关键差异。
7. WebSocket 如何通过 HTTP Upgrade 建立在 TCP 连接之上。
8. Java 常见线程安全集合如何选型。
9. ConcurrentHashMap 的并发原理与复合操作边界。
10. CopyOnWriteArrayList、BlockingQueue、ThreadLocal 的适用场景与风险。

每题沿用现有文档格式：

- 一句话回答；
- 60 秒回答；
- 常见追问；
- 能自然对应时增加项目连接点。

## 编排方式

- 新章节插入 Redis 章节之后、“最后 20 分钟速记”之前。
- 原“七、最后 20 分钟速记”顺延为“八、最后 20 分钟速记”。
- 保留原1～40题编号，避免破坏训练计划中“前24题”和“剩余题目”的引用。
- 速记区从12句扩充，加入TCP、HTTP、WebSocket和线程安全集合的最低记忆点。

## 内容边界

- 面向Java后端实习和校招，不展开拥塞控制算法推导、TLS密码套件细节或JDK源码逐行分析。
- 对三次握手、四次挥手等问题强调目的和异常状态，不要求背报文所有字段。
- 不重复大段复制已有ConcurrentHashMap与ThreadLocal内容；专项题以选型和Day 4项目连接为主，并链接回原题。
- WebSocket只讲网络基础和升级关系，协同房间、Redis Pub/Sub与Lua留给Day 4项目专题和每日复盘。

## 验收标准

1. 文档中可以检索到TCP、UDP、三次握手、四次挥手、HTTP/1.1、HTTP/2、HTTPS和WebSocket Upgrade。
2. 可以检索到ConcurrentHashMap、CopyOnWriteArrayList、BlockingQueue和ThreadLocal，并能回答如何选型。
3. 原1～40题编号保持不变。
4. 新增内容使用项目既有的“一句话回答、60秒回答、常见追问、项目连接点”风格。
5. Markdown格式检查通过，无占位符和未完成项。
