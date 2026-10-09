# 绫页初版发布记录 · 2026-10-09

仅活动分支 `nexus/mainline-r11`，未修改claude/main、开PR、合并或部署。

## 首稳定增量

- 父提交：`0ef97c080eaa4e3edc6caffd6c6cc57b84f28ea1`
- 本地源提交：`fc692dccfcb839fb653a31319953324521a582b6`
- 已发布代码提交：`63ff77a296913d7ee87c9bbe35043a808b94bb6f`
- 完全相同tree：`22bd0aa7e2f2ff407434a363b3ed68f35fc7790a`
- connector生成提交元信息不同，故SHA映射；写入使用expected_sha租约且force=false
- 回读远端ref成功，fetch后比较tree完全相同，活动本地分支soft对齐远端，工作区干净；不续写旧源分叉
- 65文件：隔离试演台、有限事件决策、人格/客观视觉、保守图片记忆词项召回和测试文档

## 验证范围

前端366/366、严格lint、双模式build/budget通过；后端clean verify842项中838通过、4预期跳过，JaCoCo/package通过；隔离真实HTTP40项TAP通过（31业务阶段、4协议、5父容器）。独立静态review及首跑失败→修复轨迹见LINGYE-REVIEW.md。

真实模型/浏览器/生产外部服务尚未验收；CI不是上述本地验证的别名，本记录不宣称CI运行或通过。实时站内事件接线、完整长期记忆治理、成长美术仍未完成。

## 恢复

以远端活动分支最新STATE与历史为准。若使用后续Library备份，先验证SHA256与git bundle verify，再在新目录clone、核对精确HEAD/tree；不要以旧main覆盖本分支。
