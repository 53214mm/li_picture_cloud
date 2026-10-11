# 绫页记忆召回、识图与平台模型（2026-10-10）

## 为什么改

试聊发现：问"骑士""少女举剑""我的世界"她都想不起来，问"我喂你的图你能记住吗"她说记不住。原因是召回只靠代码里约 25 个写死的关键词，没命中时提示词里也不说她有记忆；喂图记忆只存了画面描述，没有图片名和标签。

## 现在怎么工作

1. 喂图：视觉模型输出三段——可见观察、可能的意义（推测）、可能的角色或出处（推测），都写进记忆。开启"识图联网"且用百炼千问时，先强制联网搜索作品或角色再描述。
2. 聊天：已确认的记忆和图片名、分类、标签一起进 pgvector；本轮按用户这句话做语义检索，相似度 ≥ 0.45 才算相关，并与"已授权、已确认"记忆求交集。点名图片编号（"图片 #303"）直接命中。
3. 向量库或向量模型不可用：退回字面两字匹配，聊天照常；失败后冷却 1 分钟再试。健康但索引尚未补全时，也对全部已授权、已确认候选补字面匹配，覆盖名字、分类、标签与内容；图片编号直查始终优先。索引完整后仍沿用语义阈值。
4. 状态里永远有一行"你记得对方喂过的 N 张图；问起或聊到相关的才用"。用户问"喂过哪些图"时，额外给最近 6 张的名字和简述。

## 本地启用 pgvector

```
docker compose -f compose.pgvector.yaml up -d
```

`application-local.yaml`：

```
pgvector:
  datasource:
    url: jdbc:postgresql://localhost:5433/picture
    username: postgres
    password: lingye-dev
```

向量模型默认用 `spring.ai.dashscope.api-key`（平台百炼 Key），模型 `qwen3.7-text-embedding`。表 `companion_memory_vector` 首次使用时自动创建，已有记忆在聊天时按需补索引（每轮最多 20 条）。

## 识图联网

模型连接页「识图联网」开关，存于 `model_vision_preference`。只有百炼千问支持带图联网：走 `/api/v1/services/aigc/multimodal-generation/generation`，`enable_search` + `search_options.forced_search`，流式返回后解析 JSON。识图走其他厂商时开关保留但不生效，页面会说明。联网识图更慢，默认超时改为 45 秒。

## 平台模型

平台聊天与识图都是 `qwen3.8-max`。qwen3.8 是多模态模型，平台聊天通过 `spring.ai.dashscope.chat.options.multi-model: true` 走多模态接口。

## 未验证

- 真实 qwen3.8-max、qwen3.7-text-embedding、联网识图都未用真实 Key 调用过；请求格式依据百炼官方文档，用本地桩测试校验。
- pgvector 未连真实 PostgreSQL 跑过（本机没有 Docker 环境），SQL 未经实库验证。
- 相似度阈值 0.45 是经验值，需要用真实记忆试聊后调整（`COMPANION_MEMORY_MIN_SIMILARITY`）。
