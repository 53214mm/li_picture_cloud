# Day 3：认证授权、Spring MVC、AOP 与 Spring 核心原理

> 日期：2026-08-07
> 最终结论：通过，综合约 8 分水平。权限主链路已经能够讲清，当前最需要补强的是 Filter、HandlerInterceptor、AOP 的边界，以及自动装配和事务失效条件。
> 使用方式：先遮住标准答案，每题口述 60～90 秒；回答时先给结论，再讲执行链路，最后落到本项目。

## 今日能力概览

| 能力 | 今日表现 | 下一步 |
| --- | ---: | --- |
| 认证与授权区分 | 7.5/10 | 认证不只是“是否登录”，而是建立可信身份 |
| 空间权限完整链路 | 8.5/10 | 区分授权主体与授权资源 |
| 权限注解与 AOP | 7.5/10 | 牢记 `@Aspect` 加在切面类，而非 Controller |
| SpEL 动态取参 | 5.5/10 | 分开“从参数取 ID”和“查询资源归属” |
| Spring MVC 请求链 | 8.5/10 | 补上 HandlerMapping、HandlerAdapter 与异常解析 |
| AOP 代理 | 8/10 | 准确区分 JDK 动态代理与 CGLIB |
| IOC 与依赖注入 | 7.5/10 | 构造器注入是尽早暴露循环依赖，不是消灭它 |
| Bean 生命周期 | 9/10 | 已达到校招面试可用水平 |
| Spring 事务 | 7/10 | 牢记默认回滚规则和异常必须传播到代理 |
| Spring Boot 自动装配 | 7.5/10 | `@ConditionalOnMissingBean` 检查的是 Bean |
| 权限覆盖与越权防护 | 8.5/10 | 以可信数据库关系确定资源归属 |
| Filter / Interceptor / AOP | 4.5/10 | 今日最薄弱项，需要重点复习 |

---

## 第 1 题：认证和授权有什么区别？

### 原题

什么是认证，什么是授权？用户已经登录，为什么仍可能收到 `NO_AUTH`？前端隐藏编辑按钮能否代替后端授权？

### 你的原回答

> 认证是用户是否登录的校验，授权是用户可否对内部资源操作的校验。用户登录但仍可能没有团队角色所需的权限，前端隐藏按钮也不能阻止请求被篡改。

### 纠错

- 认证不只是得到一个布尔值，而是确认“当前请求者是谁”，并建立可信身份。
- `viewer` 通常具有 `picture:view`，更合适的拒绝示例是 viewer 尝试编辑，或者非成员尝试查看团队图片。
- 前端按钮属于交互控制，后端才是安全边界。

### 标准答案

> 认证解决“你是谁”，例如通过账号密码建立 Session，再从 Session 中恢复当前登录用户；授权解决“你能做什么”，需要结合平台角色、空间类型、成员角色和资源归属判断具体操作。用户登录只能证明身份有效，不代表他有权操作所有图片。例如团队 viewer 可以查看但不能编辑。前端隐藏按钮可以改善体验，但攻击者仍能用 Postman 或脚本构造请求，因此每个敏感操作都必须由后端授权。

> 记忆：认证决定你是谁，授权决定你能做什么。

---

## 第 2 题：团队 viewer 编辑图片时，权限链路如何执行？

### 原题

从当前登录用户开始，说明 `AuthorizationSubject`、`SpaceAuthorizationResource`、角色权限映射以及 `NO_AUTH` 的完整链路。

### 你的原回答

> 从 Session 取得用户 ID 和身份，查询图片、空间和成员关系，构造请求主体和资源主体，通过权限决策器得到权限码；viewer 不含编辑权限，因此抛出 `NO_AUTH`。

### 纠错

- 不是所有请求都会自动进入权限注解切面，必须存在注解入口或显式调用权限服务。
- `AuthorizationSubject` 表示“谁在操作”，`SpaceAuthorizationResource` 表示“正在操作什么资源以及它的上下文”，后者不是第二个主体。
- 一次校验通常从注解指定的 `pictureId`、`spaceId` 或 `spaceUserId` 定位资源，不是无条件把所有 ID 都查询一遍。

### 标准答案

> 后端先从可信 Session 取得当前用户，构造 `AuthorizationSubject`。如果注解提供的是图片 ID，就查询 Picture，并沿 `picture.spaceId` 查询 Space；对于团队空间，再用当前用户 ID 和空间 ID 查询 `SpaceUser`，获得 viewer、editor 或 admin 角色，组成 `SpaceAuthorizationResource`。`SpaceAuthorizationManager` 根据用户身份、空间类型、资源归属和成员角色计算权限集合。若本次要求 `picture:edit`，但 viewer 的集合只有查看等权限，检查失败并抛出 `NO_AUTH`，目标业务方法不会执行。

---

## 第 3 题：`@Around("@annotation(permission)")` 是什么？

### 你的原回答

> 它定义参数为 permission 的 AOP 注解，Controller 方法上打 `@Aspect` 后会在方法前加入切点；通过后调用 `proceed()`，否则抛异常，不调用就不会进入 Controller。

### 纠错

- `@Aspect` 加在切面类上，Controller 方法加的是 `@SpacePermission`。
- `permission` 是当前目标方法上 `@SpacePermission` 的注解实例，可读取权限码和 SpEL 配置。
- `ProceedingJoinPoint` 表示被拦截的方法调用，不是“在方法前打上的切点”。

### 标准答案

> `@Aspect` 声明一个切面类；`@Around` 声明环绕通知；`@annotation(permission)` 匹配所有添加了 `@SpacePermission` 的方法，并把该注解实例绑定到 `permission` 参数。`ProceedingJoinPoint` 包含目标对象、方法和实参。切面先读取注解并校验权限，成功后调用 `joinPoint.proceed()` 执行原方法；失败则抛出异常。如果不调用 `proceed()`，Controller 原始方法就不会执行。

---

## 第 4 题：SpEL `#p0.id` 如何取得图片 ID？

### 你的原回答

> `#p0.id` 是请求第一个方法参数的 ID。权限切面从请求中提取可能存在的各种 ID，再查询数据库；不能写死是因为不能相信前端。如果为空就换空间或成员 ID 查询，都为空再报错。

### 纠错

- `#p0.id` 的第一部分答对了。
- SpEL 只负责根据目标方法实参计算表达式，不负责查询数据库。
- 不能写死的直接原因是每次请求操作的图片不同；前端不可信是取得 ID 后仍须查库的原因。
- 注解声明按图片 ID 校验时，ID 缺失应参数报错，不能擅自降级成另一种资源校验。

### 标准答案

> `#p0` 表示目标方法第一个实际参数，`.id` 表示读取它的 `id` 属性。切面建立 SpEL 上下文，把方法参数放进去并解析表达式，得到本次请求的图片 ID。随后授权服务根据这个 ID 查询 Picture、Space 和 SpaceUser，确认真实资源归属。表达式不能写死，因为每次请求 ID 不同；前端传入的 ID 仍不可信，必须用数据库关系验证。如果请求对象或 ID 为 null，应拒绝请求并返回参数错误，不能默认放行或任意切换校验语义。

> 记忆：SpEL 负责取 ID，数据库负责认资源，权限管理器负责作决定。

---

## 第 5 题：Spring MVC 请求链路

### 你的原回答

> 浏览器到 Nginx，再到 Servlet、DispatcherServlet，通过 HandlerMapping 定位 Controller，经过 HandlerInterceptor和权限注解，之后执行 Controller、Service、Mapper；异常由全局异常处理器捕获。权限切面抛错后后三层不执行。

### 纠错

- `DispatcherServlet` 本身就是 Servlet，不必把二者写成两个并列业务阶段。
- 普通 `HandlerInterceptor` 放行后，`HandlerAdapter` 调用 Controller Bean；若它是代理对象，才先进入权限 AOP。
- 异常时 `postHandle` 通常不执行，`afterCompletion` 一般仍有机会执行。

### 标准答案

```text
浏览器 → Nginx → Servlet 容器 → DispatcherServlet
→ HandlerMapping 定位方法和拦截器链
→ HandlerInterceptor.preHandle
→ HandlerAdapter 调用 Controller 代理
→ @SpacePermission 环绕通知
→ Controller 原方法 → Service → Mapper → MySQL
→ 结果序列化并返回
```

> 权限切面如果抛出 `NO_AUTH`，它不会调用 `proceed()`，因此 Controller 原方法、Service和Mapper都不执行。异常返回 `DispatcherServlet`，由异常解析器找到全局异常处理方法并转换成统一响应。

---

## 第 6 题：Spring AOP 为什么需要代理？

### 你的原回答

> `new` 出来的对象无法修改方法本身。代理通过接口反射或子类继承无侵入地添加切面；调用代理切面生效，调用原对象不生效。后置处理器生成代理，同类 `this` 调用因为没经过代理而失效。

### 纠错

- 准确说法是 JDK 动态代理和 CGLIB 子类代理，“接口反射”不够准确。
- Bean后置处理器不仅“调用代理”，而是判断是否需要增强，并把最终代理对象交给容器和调用方。

### 标准答案

> Spring AOP不修改原始类，而是在对象外创建代理。JDK动态代理让代理实现相同接口，CGLIB动态生成目标类的子类。调用代理时先执行权限、事务或日志增强，再调用原始方法。Spring在Bean创建过程中通过后置处理器生成代理，并让容器最终暴露这个代理。自己 `new` 或持有原始对象都会绕过代理；同类中的 `this.method()` 也是原对象内部调用，所以方法上的事务或切面可能失效。

---

## 第 7 题：IOC与依赖注入

### 你的原回答

> 自己 `new` 无法被Spring统一管理，写死实现不利测试，还可能重复创建对象并且无法代理。IOC把对象和依赖交给容器，依赖注入给对象填充属性。构造器注入不会循环依赖。

### 纠错

- 依赖注入不是只填充字段，也可以通过构造器和 Setter 完成。
- 重复占用内存不是最主要的问题，脱离AOP、事务、缓存、生命周期和配置管理才是关键。
- 构造器注入不会消灭循环依赖，而是让循环依赖在启动阶段尽早暴露。

### 标准答案

> IOC是控制反转：对象创建、组装和生命周期管理从业务代码转交给Spring容器。依赖注入是IOC的主要实现方式，容器创建Controller时把它需要的Service通过构造器传入。自己 `new PictureServiceImpl()` 会写死具体实现，不方便替换和Mock，也无法稳定获得 `@Transactional`、缓存、异步、权限AOP和生命周期回调。构造器注入能明确必要依赖、配合 `final` 保证对象完整、方便测试，并尽早暴露循环依赖。

> 记忆：IOC是对象归容器管，DI是容器把依赖送进来。

---

## 第 8 题：Spring Bean生命周期

### 你的原回答

> 反射实例化、依赖注入、Aware、BeanPostProcessor前置处理、`@PostConstruct` 初始化、后置处理并生成AOP代理、投入使用，关闭时执行 `@PreDestroy`。

### 标准答案

```text
读取 BeanDefinition
→ 实例化（构造器注入在这一阶段参与）
→ 属性赋值（字段/Setter注入）
→ Aware 回调
→ BeanPostProcessor.before
→ @PostConstruct / afterPropertiesSet / init-method
→ BeanPostProcessor.after，通常在此产生AOP代理
→ Bean可用
→ 容器正常关闭时执行 @PreDestroy / destroy
```

> `@PostConstruct` 在依赖注入完成后执行，适合校验配置和准备资源；`@PreDestroy` 用来关闭线程池、连接等资源。进程被强杀时不保证能够执行销毁回调。严格来说，循环依赖场景可能提前暴露代理，但校招回答“通常在后置处理阶段创建代理”即可。

---

## 第 9 题：`@Transactional` 为什么失效？

### 你的原回答

> 同类调用没有经过代理。其他情况包括不是Spring Bean、方法不可代理、异常被吞。Spring收到可回滚异常时回滚。

### 纠错

- 必须说明“可回滚异常”的默认范围：`RuntimeException` 和 `Error`。
- 受检异常默认不回滚，常用 `rollbackFor = Exception.class` 调整。
- 即使配置了回滚规则，异常被捕获后不再抛出，代理仍可能提交。

### 标准答案

> `uploadPicture()` 经代理进入原对象后，再通过 `this.savePicture()` 内部调用，不会重新经过事务代理，因此 `savePicture()` 上的事务通知不执行。常见失效情况包括：对象由 `new` 创建；同类内部调用；`private`、`static`、`final` 等方法不能按预期代理；异常被捕获且不再抛出；抛出受检异常但未设置 `rollbackFor`；异步线程或多数据源未纳入同一事务管理器。Spring默认对 `RuntimeException` 和 `Error` 回滚。事务方法通常应是由外部调用的 `public` Spring Bean方法。

---

## 第 10 题：Spring Boot自动装配

### 你的原回答

> `@Configuration`、`@EnableAutoConfiguration`、`@ComponentScan`。自动读取配置；条件注解检查classpath和配置。`@ConditionalOnMissingBean` 是不存在特定类时生效，用户配置优先。

### 纠错

- `@EnableAutoConfiguration` 的核心是导入自动配置候选类，再由条件注解决定是否生效，不只是读取配置文件。
- `@ConditionalOnMissingBean` 检查容器里是否缺少某个Bean，不是检查classpath是否缺少类。

### 标准答案

> `@SpringBootApplication` 主要组合了 `@Configuration`、`@ComponentScan` 和 `@EnableAutoConfiguration`。前者声明配置类，组件扫描从启动类所在包寻找业务Bean，自动配置则导入框架和Starter提供的候选配置。Spring Boot根据classpath依赖、配置属性和容器已有Bean决定是否生效。`@ConditionalOnClass` 判断类路径存在指定类，`@ConditionalOnMissingBean` 判断容器缺少指定Bean，`@ConditionalOnProperty` 判断配置项满足条件。默认配置通常带 `@ConditionalOnMissingBean`，因此用户声明同类型Bean后，自动配置会主动让步。

> 记忆：Starter带来依赖和候选配置，条件注解决定是否生效，用户Bean覆盖默认方案。

---

## 第 11 题：没有 `@SpacePermission` 就没有权限校验吗？

### 你的原回答

> 不一定。还可能有 `@AuthCheck`、Controller显式方法检查和Service显式检查。前者判断管理员，后者判断资源操作权限。若资源需要权限而整条链路没有校验，就是漏洞。

### 纠错

- `@AuthCheck` 不只是管理员判断，也可表达登录或平台角色要求。
- 还要检查WebSocket握手与消息处理等非HTTP入口。
- `StpInterfaceImpl` 提供权限集合不等于它会自动保护所有Controller；仍需要真正触发Sa-Token检查。

### 标准答案

> 没有权限注解不一定没有校验。项目还可能使用 `@AuthCheck`、Controller显式调用 `authorizationAccessService.check(...)`、Service显式检查，以及非HTTP入口自己的权限逻辑。`@AuthCheck`主要解决登录状态和平台角色，`@SpacePermission`解决具体图片或空间资源授权。注解适合声明式、统一的入口校验；显式调用适合动态业务分支。判断公开接口还是漏洞，要先确认产品规则，再沿Controller、Service和查询条件检查整个调用链。如果受保护资源的所有入口都没有授权，则是权限覆盖缺口。

---

## 第 12 题：水平越权（IDOR）

### 你的原回答

> 用户修改图片ID属于水平越权。登录不代表有权限，按钮隐藏也能被绕过。后端查询用户、图片、空间和成员关系再计算权限。个人图片可能只比较 `picture.userId`，团队空间还要查角色。权限应在方法开始或事务结束前检查。

### 纠错

- `picture.userId` 可能只是上传者，尤其不能代表团队资源的当前权限。
- `spaceId` 和所有者等关系应从数据库Picture记录推出，不应相信前端同时传来的关联参数。
- 权限必须在任何数据库、COS、Redis发布或WebSocket广播等副作用之前完成；“事务结束前”太晚。

### 标准答案

> 这是水平越权，也叫IDOR。服务端应以请求中的 `pictureId` 为定位入口，查询真实Picture，再从数据库取得所属空间和资源关系。个人空间按所有权规则判断；团队空间查询 `SpaceUser` 成员关系和viewer/editor/admin角色；公共图库按上传者、管理员、审核状态等明确规则判断。只有权限集合包含 `picture:edit` 后才能执行修改。客户端的userId、spaceId和角色都不能作为可信授权依据，且权限检查必须发生在所有业务副作用之前。

---

## 第 13 题：为什么只在Controller校验仍可能不安全？

### 你的原回答

> Service复用时可能没有权限校验。不必每层重复，复用性高的Service在内部校验，低复用的用注解；Service是最终安全边界。

### 纠错

- 不能只按“当前复用性高不高”决定，因为未来可能新增Controller、MQ或定时任务入口。
- 不是所有底层工具方法都需要授权，应该保护会产生敏感业务副作用的应用服务或用例方法。
- 多层可以调用同一个授权组件，但不要复制多套权限规则和重复查库。

### 标准答案

> 只在Controller校验会让Service被其他Controller、消息消费者、定时任务或其他Service调用时绕过权限。Controller注解负责声明接口权限和尽早失败；敏感业务操作的应用Service作为最终安全边界，在产生副作用前做权威检查；具体规则集中在 `SpaceAuthorizationAccessService` 和 `SpaceAuthorizationManager` 等统一组件中。这样可以多入口复用同一套规则，而不是在每层手写一遍判断。

> 记忆：Controller保护入口，Service保护业务，授权组件统一规则。

---

## 第 14 题：全局异常处理

### 你的原回答

> 注册全局异常捕获类并定义方法捕获的异常。每个Controller写 `try-catch` 可读性差，还可能吞异常。未登录401、无权限403、参数错误400、资源不存在404，服务器异常500。错误提示应友好且不能暴露SQL、路径和堆栈。

### 纠错

- 异常返回 `DispatcherServlet` 后，由 `HandlerExceptionResolver` 体系寻找匹配的异常处理方法。
- `try-catch` 本身不会使AOP失效；事务方法捕获异常后不再抛出，才可能导致事务错误提交。
- 普通业务失败不要全部归为500，状态冲突可以使用409，限流可以使用429。

### 标准答案

> `@RestControllerAdvice` 声明全局Controller增强，等价于面向响应体的 `@ControllerAdvice`；`@ExceptionHandler` 指定某个方法处理的异常类型。异常从AOP、Controller或其下游抛出后回到 `DispatcherServlet`，异常解析器找到最匹配的处理方法，将异常转换成统一JSON。HTTP状态码表达协议层结果，业务码表达具体原因，例如未登录401/40101、无权限403/40301、参数错误400/40001、资源不存在404/40401、冲突409/40901、未知服务错误500/50001。客户端只返回安全且可理解的信息，完整堆栈、SQL和路径记录到服务端日志，并可附追踪ID。

---

## 第 15 题：Filter、HandlerInterceptor、AOP怎么选？

### 你的原回答

> 三者分别在Servlet、DispatcherServlet和Bean方法附近。能取得什么信息不知道。请求日志、空间权限和Service事务可以用AOP；WebSocket消息有额外身份权限限制。

### 纠错

这是今日最薄弱项，需要直接背熟下表：

| 机制 | 所属层 | 能拿到什么 | 典型用途 |
| --- | --- | --- | --- |
| Filter | Servlet规范，DispatcherServlet之前 | 请求、响应、请求头、Cookie、URI、IP | 跨域、编码、通用Token解析、访问日志 |
| HandlerInterceptor | Spring MVC，Controller前后 | 请求、响应、目标Controller和方法 | 登录检查、接口注解、Controller级日志 |
| Spring AOP | Spring Bean方法调用 | Bean、方法、参数、注解、返回值、异常 | 事务、缓存、资源权限、业务日志 |

### 标准答案

> Filter位于Spring MVC外层，适合处理所有Servlet请求层面的通用逻辑；HandlerInterceptor由Spring MVC管理，在找到目标Controller后执行，能感知处理器和方法；AOP拦截Spring容器中的Bean方法，不限于HTTP入口。跨域优先使用Filter、MVC统一CORS配置或Nginx；登录可由安全框架、Filter或MVC拦截器完成；HTTP访问日志适合Filter，Controller方法日志适合Interceptor，业务方法日志可用AOP；空间权限可使用Controller注解AOP配合Service权威检查；`@Transactional` 是典型的Service AOP。

> 记忆：Filter管Servlet请求，Interceptor管MVC接口，AOP管Spring Bean方法。

---

## 第 16 题：HTTP权限失败综合链路

### 原题

用户B请求编辑用户A的私有图片，Controller带 `@SpacePermission`，Service带 `@Transactional`。从 `DispatcherServlet` 开始说明到403响应的完整链路，并判断事务是否开启。

### 你的原回答

> 请求进入 `DispatcherServlet`，经过HandlerInterceptor并找到Controller，再进入权限注解；从可信身份取得用户ID，通过SpEL取得图片ID，查询资源归属和权限集合。发现无权限后抛异常，由全局异常处理器返回403；Controller未执行，因此事务不会开启。

### 补充后的标准答案

> `DispatcherServlet` 通过HandlerMapping找到目标Controller方法及拦截器链，先执行 `HandlerInterceptor.preHandle()`。HandlerAdapter随后调用Spring容器中的Controller代理对象，权限环绕通知读取 `@SpacePermission`，从Session取得可信登录用户，并用SpEL从方法实参解析图片ID。授权服务查询Picture及所属空间，构造授权主体和资源上下文，计算后发现用户B不具有 `picture:edit`，于是切面不调用 `joinPoint.proceed()` 并抛出业务异常。Controller原方法、Service和Mapper均不执行；因为Service事务代理根本没有被调用，所以事务从未开启，不是开启后回滚。异常返回DispatcherServlet，由全局异常处理器转换响应。未登录使用401；已经登录但无资源权限使用403。

---

## 今日最终评价

你已经可以讲清以下主线：

```text
可信登录身份
→ 动态定位资源
→ 查询数据库确认资源归属
→ 根据空间类型和成员角色计算权限
→ 缺少权限则在副作用前拒绝
→ 全局异常处理返回统一错误
```

目前不是“完全不懂权限”，而是已经理解业务授权链路，但部分Spring底层术语还不够稳定。面试时先讲自己真正掌握的业务链路，再逐步补框架原理，不要一开始主动深入源码扩展点。

## 今日六个重点纠错项

1. `@Aspect` 加在切面类，`@SpacePermission` 才加在Controller方法。
2. SpEL只负责从方法实参取ID，数据库负责确认资源归属。
3. `AuthorizationSubject` 是操作人，`SpaceAuthorizationResource` 是资源上下文。
4. Spring AOP依赖代理；`new` 对象和同类 `this` 调用可能绕过代理。
5. Spring事务默认只对 `RuntimeException` 和 `Error` 回滚，异常被吞可能错误提交。
6. Filter管Servlet请求，Interceptor管MVC接口，AOP管Spring Bean方法。

## Day 3 面试速记版

```text
认证：你是谁；授权：你能做什么。

权限链：
登录用户 → Subject
图片ID → Picture → Space → SpaceUser → Resource
Subject + Resource → PermissionSet
缺权限 → NO_AUTH

AOP：
@Aspect 定义切面
@Around 环绕方法
@annotation 绑定注解
ProceedingJoinPoint 表示目标调用
proceed() 才会执行原方法

Spring MVC：
DispatcherServlet → HandlerMapping → Interceptor
→ HandlerAdapter → Controller代理 → Controller原方法

IOC：对象交给容器；DI：容器送入依赖。

Bean：实例化 → 注入 → Aware → 前置处理
→ 初始化 → 后置处理/代理 → 使用 → 销毁。

事务生效：Spring管理 + 经过代理 + 异常传播 + 符合回滚规则。

安全边界：Controller保护入口，Service保护业务，授权组件统一规则。
```

## 进入 Day 4 前的复习清单

- [x] 用一句话区分认证与授权。
- [x] 不看文档画出 `Subject + Resource → PermissionSet`。
- [x] 解释 `#p0.id` 的解析与数据库授权为什么是两个阶段。
- [x] 口述 `@Around` 中六个核心概念。
- [x] 画出 Spring MVC 请求链，并标出普通拦截器与AOP的位置。
- [x] 说出 `new` 对象和 `this.xxx()` 为什么绕过代理。
- [x] 口述 Bean生命周期。
- [x] 说出至少四种事务失效场景和默认回滚规则。
- [x] 解释三个自动配置条件注解。
- [x] 背熟 Filter、HandlerInterceptor、AOP 对照表。
- [x] 独立回答一次“用户B编辑用户A图片”的完整拒绝链路。

完成后进入 Day 4：WebSocket连接、协同房间、消息广播、Redis Pub/Sub与Lua原子操作。
