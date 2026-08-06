# Authorization Code Comments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为权限主链的 5 个 Java 文件增加可供初学者阅读的中文教学注释，不改变运行逻辑。

**Architecture:** 注释沿权限调用顺序组织：声明权限、AOP 解析、资源上下文解析、权限决策、Sa-Token 权限加载。类级注释建立地图，方法和分支注释解释输入、输出及设计原因。

**Tech Stack:** Java 21、Spring AOP、SpEL、Sa-Token、Markdown、Git。

## Global Constraints

- 只能修改注释和必要的纯格式。
- 不改变条件、语句顺序、返回值、异常、签名和依赖。
- 不把 `StpInterfaceImpl` 标为废弃代码。
- 不修改或提交工作区其他改动。
- 不运行前端或完整后端测试；仅执行跳过测试的 Maven 编译。

---

### Task 1: 注释声明层与 AOP 层

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/annotation/SpacePermission.java`
- Modify: `src/main/java/com/li/lipicturecloud/aop/SpacePermissionInterceptor.java`

**Interfaces:**
- Consumes: Controller 上的权限码和 SpEL 资源表达式。
- Produces: 传给 `SpaceAuthorizationAccessService.check` 的权限码、资源 ID 和当前请求。

- [ ] **Step 1:** 为注解类补充完整调用示例，解释 `value`、`spaceId`、`pictureId`、`spaceUserId`。
- [ ] **Step 2:** 为拦截器补充类级流程、`#p0/#a0`、只允许一种资源、请求来源和 `joinPoint.proceed()` 注释。
- [ ] **Step 3:** 使用 `git diff --word-diff=porcelain` 检查 Java 词法内容没有变化。

### Task 2: 注释资源解析与权限决策

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationAccessService.java`
- Modify: `src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManager.java`

**Interfaces:**
- Consumes: 用户、权限码以及空间/图片/成员关系之一。
- Produces: 统一授权资源和当前主体最终拥有的权限码集合。

- [ ] **Step 1:** 解释 AccessService 的门面职责及三种 ID 的解析优先级。
- [ ] **Step 2:** 解释公共图片、个人空间、团队空间的权限决策表和平台管理员分支。
- [ ] **Step 3:** 检查新增注释不声称不存在的缓存、数据库约束或授权行为。

### Task 3: 注释 Sa-Token 权限加载路径并验证

**Files:**
- Modify: `src/main/java/com/li/lipicturecloud/manager/auth/StpInterfaceImpl.java`

**Interfaces:**
- Consumes: Sa-Token 的 `loginId/loginType` 与当前 HTTP 请求。
- Produces: Sa-Token 权限校验需要的权限码列表。

- [ ] **Step 1:** 增加类级导航，解释 `getPermissionList`、请求上下文推断和与统一授权服务的区别。
- [ ] **Step 2:** 对公共图片、个人空间、团队空间分支补充原因注释，保留已有教学注释。
- [ ] **Step 3:** 运行静态检查与编译。

```powershell
git diff --check -- src/main/java/com/li/lipicturecloud/annotation/SpacePermission.java src/main/java/com/li/lipicturecloud/aop/SpacePermissionInterceptor.java src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationAccessService.java src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManager.java src/main/java/com/li/lipicturecloud/manager/auth/StpInterfaceImpl.java
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -DskipTests compile
```

Expected: Markdown/Java diff 无空白错误，Maven 输出 `BUILD SUCCESS`。

- [ ] **Step 4:** 只暂存 5 个 Java 文件并提交。

```powershell
git add -- src/main/java/com/li/lipicturecloud/annotation/SpacePermission.java src/main/java/com/li/lipicturecloud/aop/SpacePermissionInterceptor.java src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationAccessService.java src/main/java/com/li/lipicturecloud/manager/auth/SpaceAuthorizationManager.java src/main/java/com/li/lipicturecloud/manager/auth/StpInterfaceImpl.java
git commit -m "docs: explain authorization code flow"
```
