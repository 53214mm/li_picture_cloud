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

## 第二稳定增量

- 父提交：`cef2be11ae8724e1b07c3196652a3b4ba25fc645`
- 本地源提交：`31a4ca22f7c67be97d97cb9c32faad88b776335a`
- 已发布代码提交：`c6856a6854beb7bbb046acd2acb1d4cb97453aea`
- 完全相同tree：`4c6f2ccd9bcc6ee8809f28715354eeab959b984d`
- 租约expected_sha发布成功；回读ref、fetch、同tree对齐本地均通过，无其他分支改写
- 54文件：默认关闭的真实站内DEMO事件/挂件，详情页生命周期保护，静态称呼与展示，记忆查询/撤权后自身删除修复，测试与三阶段概念草案（未作为运行时资产）
- 前端430/430、lint、双build/budget；后端849总845通过4可选跳过及JaCoCo/package；最终隔离HTTP40/40且清理成功。独立定向111项前端通过并完成记忆只读review
- 新浏览器脚本170项仅收集。管理式云浏览器能用，但测试服务器跨执行环境连接拒绝；同执行HTTP200证据与清理见LINGYE-CLOUD-BROWSER.md。没有使用不安全浏览器参数/改网络/隧道
- 真实模型人格、完整长期记忆/忘记治理、完整图片上下文、运行时成长立绘仍未完成；不能把本次视作产品DONE
