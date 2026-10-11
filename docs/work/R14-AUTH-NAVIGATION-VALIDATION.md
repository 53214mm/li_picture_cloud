# R14 · 登录注册的迟到跳转与页面生命周期

## 范围与复现

从已发布9cc9cf6ee314acd3359884faaa5d77d37fc0ea11继续。R14要求既有注册登录不能因体验改造失效；本轮只处理现有页面的异步完成与导航竞争，不实现R15身份体系。

真实编译的LoginView/RegisterView配合Vue Router内存历史、RouterView与实际Pinia用户store复现：离开/返回后旧登录请求抢走新页面，旧注册响应在卸载后创建定时器，注册倒计时期间可重复提交，同实例query/hash导航之后旧响应污染状态。初始32项回归在旧实现17失败、15通过；日志`/tmp/nexus-r14-auth-red.log`。独立探针另证实：用户的离开导航被延迟beforeResolve阻塞时，旧登录完成/注册倒计时也会抢先跳转。

## 最小修复

- 处理器在请求进行中拒绝重复提交；注册成功后保留成功确认及提交锁，避免倒计时和同实例query/hash变化再次创建账号
- 每次请求捕获当前路由对象与导航意图；组件卸载或已确认的新路由使旧响应的UI回调失效
- beforeRouteLeave/beforeRouteUpdate一开始就使自动跳转失效，因此用户较新的导航即使仍在等待guard也不会被抢占
- 注册倒计时在卸载或新导航意图时清理；普通成功流程仍保持原1.5秒延迟、一次跳转。取消导航后保留真实成功信息和既有“去登录”链接；自动跳转抛错也可通过该链接继续
- 当前页面的真实失败仍显示并允许重试；已有本地redirect过滤保持。没有取消服务端请求或账号创建，既有Pinia登录成功状态仍会按原逻辑更新
- 只修改两个页面，不改API、认证store、后端、账号归属、密码或身份绑定规则

## 执行证据

- 源代码`6e29ca6a99cc2fe29eb07e4d17232770516ddc4a`，tree `ec34e46d3d624412ac8aff598d62e03f5b48a0c9`；远端映射见PUBLICATION
- 最终专门套件42/42，包含真实SFC、模板事件、RouterView、memory history、Pinia和可控计时器；仅HTTP API边界替换，不是浏览器或真实认证HTTP测试
- Node22.23.3全前端328/328、lint、Companion开/关production build与bundle budget通过；最大chunk390035bytes，CompanionView只在开启模式存在
- 新增5项浏览器用例，重点116项/13文件、默认147项/23文件仅收集，未执行浏览器。已确认的IPC拒绝未重试，也未绕过
- 后端、Redis及HTTP入口本轮未变，不重复运行制造新计数；前轮734总计/730通过/4预期跳过、Redis专项3项、HTTP27业务阶段的证据仍按各自原始运行记录解释
- 独立review通过：稳定源6e29ca6上10/10额外探针、42/42专门套件、328/328全前端、lint与diff-check；无未解决阻断性发现。独立证据`/tmp/lpc-auth-{independent,review-focused,review-full,review-lint}-6e29ca6.log`，基线Node22日志`/tmp/lpc-auth-independent-base-node22.log`
- 自有日志：`/tmp/nexus-r14-auth-{red,green,unit,lint,on-build,on-budget,off-build,off-budget,focused-list,default-list}.log`

## 可复跑命令

在li-picture-cloud-frontend下使用Node22：

```sh
npm test
node --test tests/authViewLifecycle.test.mjs
npm run lint
VITE_COMPANION_ENABLED=true npm run build
npm run check:bundle
VITE_COMPANION_ENABLED=false npm run build
npm run check:bundle
npm run test:e2e -- --config e2e/config/space-recovery.config.js --list
npm run test:e2e -- --list
```

恢复浏览器权限后，去掉`--list`运行对应套件；不能把上述收集结果视为UI验收。

## 下一步的真实门槛

本轮已关闭上一STATE中唯一源码定位的独立缺陷候选；当前没有另一个已复现的独立产品缺陷待修。不要为了继续计数重复同类测试或发明功能。

- R11/R12/R13/R14/R16剩余浏览器验收需要允许正常sandbox/Unix IPC的获准环境。Node22、前端15173和已有隔离JDK21/Maven/Redis6380 DB15/H2/后端18124构成可复跑路径。当前工具链已准备，浏览器权限事实未变
- R12真实API共享抽屉/整页联动仍须浏览器实测；HTTP故事与mock浏览器套件不替代该交叉验收
- 普通COS上传/下载并非创作stub覆盖范围，真实成功验收需要获准的可丢弃测试存储配置或单独明确的隔离替身测试范围；不能声称既有HTTP结果已覆盖
- R15需要用户先确定首个渠道、已有账号归属证明与身份冲突、邮箱手机号同形用户名命名空间、无密码账号与最后登录方式解绑规则。无已验证身份的找回或自动合并不能猜测
- R03初访理解及样片授权是发布前人工核对项。以上依赖未解除时暂停相关工作，保持完整V3与既有各Rxx浏览器验收pending
