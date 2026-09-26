# 预备单元 0.2-C：行锁、CAS 与并发结算

> 基线：`main@11a6182`
>
> 目标：区分“同一个请求重复到达”和“两个合法请求同时修改伙伴”，理解数据库唯一键、`FOR UPDATE` 与 `revision` 各自保护什么。

## 1. 并发不是一种问题

喂养模块至少面对两类并发。

### 同一个 key 并发

```text
请求 A：pictureId=8888, key=feed-key-1
请求 B：pictureId=8888, key=feed-key-1
```

这代表同一次业务操作被重复提交。目标是：只允许一个请求真正分析和成长，另一个等待或回放。

### 不同 key 并发

```text
请求 A：pictureId=8888, key=feed-key-A
请求 B：pictureId=9999, key=feed-key-B
```

这是两次合法喂养。两张图片可以并行分析，但同一个伙伴的最终成长必须按顺序结算，否则每日上限、重复图片和累计经验可能算错。

先分清这两类问题，才能理解为什么代码同时需要唯一键、状态机、行锁和 revision。

## 2. 同 key 并发：数据库唯一键是最终裁判

`CompanionFeedingCoordinator.reserve` 先按以下组合查询：

```text
companionId + idempotencyKey
```

但“先查再插”不是原子操作：

```text
时间 1：A 查询，不存在
时间 2：B 查询，也不存在
时间 3：A 插入
时间 4：B 插入
```

所以 `companion_feed_run` 表还有唯一约束：

```sql
CONSTRAINT uk_companion_feed_key
UNIQUE (companionId, idempotencyKey)
```

并发结果：

```text
A 插入成功
B 插入触发 DuplicateKeyException
B 捕获异常，重新读取 A 创建的 run
```

B 随后根据 run 状态得到：

- `PROCESSING`：返回 `IN_PROGRESS`；
- `COMPLETED`：返回 `REPLAY`；
- `FAILED` 或超时 `PROCESSING`：尝试 CAS restart；
- `REJECTED`：保持拒绝。

因此同 key 并发主要由以下组合解决：

```text
数据库唯一键
+ FeedingRun 状态机
+ run revision CAS
```

## 3. 为什么前端防重复点击不够

前端按钮置灰只能改善用户体验，不能作为正确性保障，因为重复请求还可能来自：

- 浏览器自动重试；
- 网关重试；
- 移动网络重传；
- 用户打开两个页面；
- 恶意客户端绕过前端；
- 两台应用实例同时处理同一个 key。

可靠并发保护必须落在共享的数据库约束和服务端状态上。

## 4. 不同 key 并发为什么会丢失更新

假设伙伴当前经验是 100，两次喂养各增加 10。

没有锁时：

```text
A 读取经验 100
B 读取经验 100
A 计算 100 + 10 = 110
B 计算 100 + 10 = 110
A 写入 110
B 写入 110
```

正确结果应是 120，实际却是 110。B 覆盖了 A 的结果，这叫丢失更新。

即使两次请求喂的是不同图片，也必须保护同一个伙伴聚合的修改。

## 5. 悲观锁：`SELECT ... FOR UPDATE`

`complete` 中使用：

```java
companionRepository.findByOwnerIdForUpdate(run.subjectId())
```

MyBatis 最终执行类似 SQL：

```sql
SELECT *
FROM companion
WHERE userId = ?
LIMIT 1
FOR UPDATE;
```

`FOR UPDATE` 会锁住读取到的伙伴行，直到当前事务提交或回滚。

可以把它理解为结算室的钥匙：

```text
A 拿到伙伴 1001 的结算钥匙
B 想结算同一个伙伴，只能等待
A 更新并提交，释放钥匙
B 获得钥匙，重新读取 A 提交后的最新状态
```

于是不同 key 的两个请求可以：

```text
并行分析图片
→ 串行结算伙伴
```

这样既避免长时间锁住数据库，又保证成长计算基于最新状态。

## 6. 为什么结算每日上限也需要行锁

锁保护的不只是 `lifeExperience` 字段。

`complete` 在拿到伙伴锁后还会读取：

```text
这张图片是否已经完整喂过
今天已经获得多少生命经验
该图片已经获得多少熟悉度经验
```

假设每日剩余额度只有 10，两次请求各申请 10：

没有串行结算时：

```text
A 看到剩余 10
B 也看到剩余 10
A 增加 10
B 也增加 10
最终超出每日上限
```

有行锁时，B 必须在 A 提交后重新计算今日总量，因此 B 会看到剩余额度已经变成 0。

## 7. `FOR UPDATE` 为什么必须处于事务中

行锁的生命周期依赖数据库事务：

```text
事务开始
→ SELECT ... FOR UPDATE，获得锁
→ 查询成长事实
→ 更新伙伴与记录
→ COMMIT / ROLLBACK
→ 释放锁
```

如果在事务外执行 `FOR UPDATE`，语句结束后连接可能立即提交，锁也随即释放。后面的计算和更新就不再受保护。

所以 `findByOwnerIdForUpdate` 使用：

```java
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
```

`MANDATORY` 的含义是：

> 调用这个方法时必须已经存在事务，否则立即抛错。

它不是负责开启事务，而是防止开发者误用“裸行锁查询”。真正的外层事务由 `CompanionFeedingCoordinator.complete` 开启。

调用关系：

```text
complete 的事务开始
→ findByOwnerIdForUpdate 加入已有事务
→ 行锁保持到 complete 结束
```

## 8. 乐观锁：`revision` 与 CAS

伙伴和 `FeedingRun` 都有 `revision` 字段：

```text
初始状态 revision = 0
修改一次 revision = 1
再次修改 revision = 2
```

保存伙伴时，条件类似：

```sql
UPDATE companion
SET lifeExperience = ?,
    revision = 1
WHERE id = ?
  AND revision = 0;
```

含义是：

> 只有数据库仍是我读取到的旧版本 0，才允许写入版本 1。

如果另一个请求已经把数据库更新为 revision 1，旧请求再拿 revision 0 更新时：

```text
受影响行数 = 0
→ CAS 失败
→ 抛错或重试
```

CAS 是 Compare-And-Set：比较旧值仍符合预期，再设置新值。

## 9. 领域对象也限制 revision 只能 `+1`

Repository 保存前还检查：

```java
after.revision() == expectedRevision + 1
```

这防止错误调用者：

- revision 不增加；
- revision 倒退；
- 从 1 直接跳到 99；
- 用错误 expectedRevision 保存快照。

数据库 WHERE 条件防止过期写入，Java 前置检查防止构造不合理的新版本。两者保护的角度不同。

## 10. 为什么已经有行锁，还需要 revision

这是本部分最重要的问题。

### 行锁的作用

```text
让正常的同伙伴结算主动排队
```

### revision 的作用

```text
提交前再次验证当前状态仍是调用者曾读取的版本
```

行锁可能因为以下情况没有覆盖所有路径：

- 未来新增代码忘记调用 `findByOwnerIdForUpdate`；
- 某个后台任务直接调用 save；
- 一个过期领域对象在锁之外被保留很久；
- 测试替身或新的 Repository 实现未正确加锁；
- 其他更新入口修改了 revision。

所以当前设计是：

```text
FOR UPDATE = 主要并发协调机制
revision CAS = 防止过期覆盖的第二道保险
```

这两层在当前成长聚合中有明确价值。

## 11. `FeedingRun` 自己也使用 CAS

运行状态更新条件包含：

```text
id = runId
AND revision = expectedRevision
AND status = expectedStatus
```

例如完成运行：

```sql
UPDATE companion_feed_run
SET status = 'COMPLETED',
    resultGrowthRecordId = ?,
    revision = 1
WHERE id = ?
  AND revision = 0
  AND status = 'PROCESSING';
```

同时检查 `revision` 和 `status`，是因为版本正确但状态不正确同样不能转移。例如 `REJECTED` 不能再被改成 `COMPLETED`。

## 12. 两类并发的完整对照

| 并发类型 | 示例 | 主要保护机制 | 期望结果 |
| --- | --- | --- | --- |
| 同 key、同图片 | 网络重复提交 | feed run 唯一键 + 状态机 | 一个执行，其他 IN_PROGRESS/REPLAY |
| 同 key、不同图片 | 客户端错误复用 key | pictureId + requestFingerprint | 参数冲突，不回放错误结果 |
| 不同 key、同伙伴 | 两次合法喂养 | `FOR UPDATE` + companion revision | 图片可并行分析，成长串行结算 |
| 旧 run 尝试更新新 run | 超时接管后旧请求恢复 | run revision + status CAS | 旧请求更新失败 |

## 13. 时间线示例

```text
请求 A（key-A）                  请求 B（key-B）
      │                                │
      ├─ reserve A                     ├─ reserve B
      ├─ 分析图片 8888                 ├─ 分析图片 9999
      │                                │
      ├─ complete 开始                 │
      ├─ FOR UPDATE，拿到伙伴锁         │
      ├─ 读取经验 100                   ├─ complete 开始
      ├─ 写入经验 110                  ├─ 等待伙伴锁
      ├─ COMMIT，释放锁                │
      │                                ├─ 获得伙伴锁
      │                                ├─ 读取最新经验 110
      │                                ├─ 写入经验 120
      │                                └─ COMMIT
```

最终经验是 120，不会丢失 A 的成长。

## 14. 锁不等于永远不会失败

行锁和 CAS 能保护一致性，但运行时仍可能遇到：

- 锁等待超时；
- 数据库死锁选择某个事务回滚；
- 数据库连接中断；
- CAS 因其他合法修改失败；
- 事务提交阶段失败。

正确目标不是“保证永远没有异常”，而是：

```text
发生异常时不产生半提交
→ 返回安全错误
→ FeedingRun 进入可判断的状态
→ 允许安全重试或明确拒绝
```

## 15. 阅读源码入口

- `CompanionFeedingCoordinator.complete`：完整结算顺序；
- `CompanionMapper.selectByUserIdForUpdate`：实际 `FOR UPDATE` SQL；
- `MybatisCompanionRepository.findByOwnerIdForUpdate`：`MANDATORY` 事务要求；
- `MybatisCompanionRepository.save`：伙伴 revision CAS；
- `MybatisFeedingRunRepository.transition`：run 的 revision + status CAS；
- Liquibase `2026-08-11-companion-life-core.xml`：数据库唯一键。

## 16. 知识分级

### 【必须掌握】

- 同 key 并发与不同 key 同伙伴并发不是同一个问题；
- “先查再插”不能替代数据库唯一约束；
- `FOR UPDATE` 为什么必须放在事务中；
- 行锁负责串行结算，revision 负责拒绝过期写入；
- 每日上限和重复图片规则为什么也需要在锁内重新读取。

### 【需要看懂】

- `MANDATORY` 不创建事务，只要求调用方已经有事务；
- CAS SQL 为什么同时检查旧 revision；
- run 状态更新为什么还检查旧 status；
- 行锁和乐观锁同时存在时各自的价值。

### 【暂时知道即可】

- InnoDB 行锁、间隙锁和 next-key lock 的内部细节；
- MySQL 如何检测死锁并选择回滚事务；
- 不同隔离级别对锁读取的全部影响；
- MyBatis Plus 如何构造最终 UPDATE SQL。

## 17. 自测题

1. 两个相同 key 同时到达时，为什么 Java 的查询判断不够？
2. 两个不同 key 同时喂同一伙伴时，为什么不能只依赖 feed run 唯一键？
3. `FOR UPDATE` 为什么不能在事务外单独查询后再慢慢计算？
4. 行锁已经让请求排队，revision 还能防住哪类错误？
5. 如果每日只剩 10 点经验额度，两次请求同时申请 10 点，锁应该保护哪些读取和写入？
