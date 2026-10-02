# R14 · 批量编辑后的列表缓存一致性

## 已复现的缺陷与最小范围

本轮从已发布165070ed9d84dfaa5b9451f4125df263dbbc063c继续。空间页批量编辑成功后通过`/picture/list/page/vo/cache`刷新。该接口缓存包括已授权空间查询；批量编辑控制器原先遗漏了其它图片变更路径已有的`clearListCache()`。

真实隔离HTTP已复现：预热同一个space10查询后批量修改picture102，写入成功且详情读到新分类，但原列表查询仍返回“旅行”。证据`/tmp/lpc-api-e2e-joJ4zn`；失败断言为`Successful batch must invalidate the same cached list query`。这是成功后的陈旧读取，不是API约定猜测。

修复只在事务服务成功返回后调用已有缓存失效方法。正常Spring事务代理先完成提交，再回到控制器；异常直接传播，不进入失效调用。沿用当前本地Caffeine失效和版本化Redis key，不FLUSH、不删除共享Redis数据、不改API形状、权限、配额或批量编辑规则。

不扩大到缓存架构重写：当前版本计数是进程本地的，已有多实例/重启和并发在途读取的限制不由此一行修复解决，也不声明已解决。

## 针对性验证

- 真实HTTP预热未筛选、原分类、新分类三个查询；权限拒绝与数据库拒绝后内容不变；成功后原查询重复读取、分类成员关系和最终恢复均应立即一致
- 仍用现有test,e2e自有H2/Redis入口，不需要真实模型、COS或浏览器；执行命令见R14-API-VALIDATION
- H2分类列VARCHAR(64)的超长值用来触发实际数据库拒绝；它只证明拒绝后的可见数据不变，不冒称“前一条成功写入之后的部分批量回滚”
- 控制器回归使用真实Caffeine/版本化key逻辑与内存Redis边界，覆盖成功后两个缓存层、操作/权限失败不推进缓存、命中缓存仍授权

## 剩余验收边界

- R11：浏览器展开详情、异常快照、暂停/恢复、窄屏和视觉核对仍待执行
- R12：HTTP Home/history/SSE已验证；共享抽屉与整页的真实浏览器中断/切换和API联动仍未执行。既有quick-chat浏览器文件使用mock，单跑它不能证明真实API共享视图集成
- R13：组件生命周期已验证；浏览器网络无atlas、布局、断点、触控/键盘和主要操作可达性仍待验证
- R16：文案/读取状态组件证据已通过；页面布局与键盘走查仍待验证
- R14：当前后端/Redis/27阶段HTTP与前端自动证据不替代浏览器全回归。普通上传/下载仍调用真实CosManager，创作artwork stub不覆盖这些普通文件路径；不能据此声称COS真实上传/下载通过
- 浏览器最低缺失条件：正常sandbox/Unix IPC的获准环境。Node22+前端端口15173可运行mock套件；真实后端故事还需完整JDK21/Maven、隔离Redis6380 DB15、H2与后端18124。已拒绝的浏览器路径不重试、不改安全设置

## R15评估结论

R15尚无实现：现有用户名/BCrypt密码和稳定User ID保留，身份表、验证码、绑定、微信和账号安全页均缺失。需要先明确首个渠道、现有账号归属证明/身份冲突、邮箱手机号与同形旧用户名的命名空间、无密码账号及最后登录方式解绑规则。真实邮件/短信/微信接入另需相应供应商与获准测试配置；这些不妨碍设计和隔离测试，但不能宣称真实接入已完成。

不先搭未使用的验证码框架或猜合并/身份校验语义。已有密码安全决策继续有效：不恢复MD5、不在无已验证身份时发明找回。当前仅处理上述R14缺陷。

另一个R14候选是Login/Register异步完成后的迟到路由跳转：注册定时器无卸载清理、登录响应无页面生命周期判断。该候选仅经源码发现，尚未运行复现，未混入本轮修复或已验证结果。

## 最终证据

- 源代码a220a02d8429e2ca99364882f306917437a59ccf，对应远端对象bf13623d1d72367dba1b9f915c34cea0f6bbe0b0，tree abf615cf36a0054fb4aa2169ddd868ab858375a3；发布状态见STATE/PUBLICATION
- 控制器红灯：6项中2断言失败、4通过、0初始化错误；一行修复后6/6通过。报告分存在 `/workspace/shared/nexus-tooling/r14-cache-validation/{red-controller,green-controller}`
- HTTP原查询红灯 `/tmp/lpc-api-e2e-joJ4zn`；修复后的完整新实例 `/tmp/lpc-api-e2e-S4Gq45` **27业务阶段+4协议+4父容器=TAP35全部通过**，所有自有进程关闭；本轮扩展已有批量阶段，不新增业务阶段计数
- 新后端clean verify **734项，730通过，4预期跳过，0失败/错误**；144份XML，编译/打包及原JaCoCo门通过。airuntime447/504=88.69%，companion541/626=86.42%。独立新target数据位于r14-cache-validation/clean-verify，前轮728证据单独保存
- 前端286/286、lint、双模式build/budget通过，最大390035bytes；CompanionView只在开启时存在。111重点/142默认浏览器仅收集，未执行浏览器
- 4项跳过仍为3项Redisopt-in及1项操作者真实AI smoke；Redis专项此前单独3/3通过，不拼成当前734的单次执行统计
- 独立review通过，无阻断性发现；BatchCache+TeamAccess专项9/9，证据 `/tmp/lpc-cache-independent-4DKZtV`；独立完整HTTP `/tmp/lpc-api-e2e-t3qPHX` TAP35通过且自有进程全部关闭。独立确认超长分类的真实DataIntegrityViolationException与错误后的原值读取，审核144份全量报告/覆盖率计数
