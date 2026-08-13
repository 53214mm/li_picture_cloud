# 伙伴情绪、关系状态与来源化记忆指南

> 对应三年规划「未来 90 天执行清单」第 2—4 条，设计见[伙伴情绪、关系状态与来源化记忆设计](../superpowers/specs/2026-08-14-companion-mood-relationship-memory-design.md)。

## 这次交付了什么

伙伴在"生命核心 + 真实视觉"之后补齐了三块第一年 Q1 的基础能力：

1. **当前情绪**：五轴强度（精力、愉悦、孤独、灵感、烦躁）。每次喂养按确定性规则带来短暂波动；每满 1 小时每轴向 0 回落 5 点。情绪是快状态，只影响展示与未来的主动机会，不参与成长审计。
2. **关系状态**：按 `(伙伴, 主体)` 独立保存（熟悉度、信任、亲密度、默契、近期反馈）。首次完整喂养与重复熟悉感分别推进关系，不随时间衰减。
3. **来源化记忆**：真实视觉理解（或 Demo 测试档）的喂养产生一条带来源图片、置信度和状态的**记忆候选**；用户可以确认、纠正、忽略或删除；来源图片撤权或消失后，记忆自动失效，内容不再展示。

喂养完成后，成长、情绪、关系和记忆候选在**同一事务**内原子提交；幂等回放不会重复应用任何一项。

## 数据模型

- `companion_mood`：每伙伴一行，五轴 `DECIMAL(6,2)`，`revision` 乐观锁，唯一键 `companionId`。
- `companion_relationship`：每伙伴每主体一行，唯一键 `(companionId, subjectId)`。
- `companion_memory`：追加式事实；`growthRecordId` 唯一键保证一次成长至多一条候选；状态 `PENDING/CONFIRMED/DISMISSED/INVALIDATED/DELETED`。

三张表加入两份 ShardingSphere `!SINGLE` 清单；迁移沿用"每 DDL 一个 changeSet + 可恢复 precondition"模式，只有物理 MySQL 可运行 Liquibase。

## 情绪与关系规则（V1）

| 规则 | 值 |
| --- | --- |
| 情绪衰减 | 每满 1 小时，每轴 -5.00，最小 0 |
| 情绪单次喂养每轴变化上限 | ±15.00 |
| 首次完整喂养关系影响 | 熟悉度 +5、信任 +2、亲密度 +1、默契 +1、近期反馈 +5 |
| 重复图片熟悉感影响 | 熟悉度 +2、默契 +1、近期反馈 +2 |
| 关系单轴单次变化上限 | ±10.00 |

数值规则集中在 `CompanionMoodRules` 与 `CompanionRelationshipRules`，调整时直接改常量并同步本文档。

## 记忆 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/companion/memories?limit=50` | 列表 + 惰性撤权失效传播；失效/已删除不返回内容原文 |
| POST | `/companion/memories/{id}/confirm` | 待确认/已忽略 → 已确认 |
| POST | `/companion/memories/{id}/correct` | 改写文案并置已确认（body: `{content}`，≤300 字纯文本） |
| POST | `/companion/memories/{id}/dismiss` | 待确认/已确认 → 已忽略 |
| DELETE | `/companion/memories/{id}` | 软删为已删除（终态） |

所有操作只允许记忆所属伙伴的主人；状态机非法转换返回参数错误。记忆只保存经校验的伙伴独白或 Demo 固定文案，不保存用户简介原文、图片 URL 或模型原始响应。

## 页面

伙伴主页（`/companion`）新增：

- **当前情绪**：五轴强度条 + 服务端生成的伙伴口吻摘要（聊天气泡展示）。
- **关系状态**：五轴展示，近期反馈支持负向显示。
- **伙伴记忆**：列表（状态徽章、置信度、时间、来源图片链接），内联纠正、确认、忽略、删除；失效记忆显示"来源图片已不可用"。

伙伴的所有"发言"（喂养故事、情绪摘要、记忆）统一走 `CompanionMessageBubble`，为后续站内对话保留同一展示语言。

## 验证命令

后端聚焦验证：

```powershell
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionMoodTest,CompanionRelationshipTest,CompanionMemoryTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionMemoryServiceTest,CompanionMoodMemoryPersistenceIntegrationTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionFeedingCoordinatorTest,CompanionLifeServiceTest,CompanionControllerTest" test
.\scripts\mvnw-java21.ps1 "-Dtest=CompanionSchemaMigrationTest" test
.\scripts\mvnw-java21.ps1 -B "-Dspring.profiles.active=test" verify
```

前端验证：

```powershell
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
```

## 隐私与边界

- 情绪、关系与记忆都不保存图片字节、Data URL、用户简介原文或模型原始响应。
- 撤权失效是读取路径的惰性传播：记忆列表每次读取都重新校验来源图片的 `PICTURE_VIEW` 权限；基础设施异常不会被误判为撤权。
- 记忆候选只在真实视觉理解（或 Demo 测试档）时生成，元数据喂养与显式降级不产生候选，避免把"未理解内容"包装成记忆。
