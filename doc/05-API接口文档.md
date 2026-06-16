# API 接口文档

## 一、概述

### 基本信息

| 项目 | 值 |
|------|-----|
| 基础 URL | `http://localhost:8124/api` |
| 响应格式 | JSON，统一 `BaseResponse<T>` 封装 |
| 认证方式 | Session（Cookie：`JSESSIONID`） |
| 日期格式 | ISO 8601 |
| OpenAPI 文档 | `GET /api/v3/api-docs` |
| Swagger UI | `http://localhost:8124/api/swagger-ui.html` |

### 统一响应结构

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

**成功**：`code = 0`，`message = "ok"`，`data` 为接口实际数据。

**失败**：`code ≠ 0`，`message` 为错误描述，`data = null`。

### 错误码速查

| code | 含义 | 触发场景 |
|------|------|----------|
| `0` | 成功 | — |
| `40000` | 请求参数错误 | 字段为空、格式不符合、长度超限 |
| `40100` | 未登录 | Session 过期或不存在 |
| `40101` | 无权限 | 非管理员访问管理员接口、非作者编辑他人图片 |
| `40300` | 禁止访问 | — |
| `40400` | 请求数据不存在 | 查询/删除不存在的 ID |
| `50000` | 系统内部异常 | 未预期的异常（COS 上传失败等） |
| `50001` | 操作失败 | 数据库操作返回失败 |

### 注意事项

1. 所有 `POST` 接口，请求体使用 `application/json`（除非特别说明为 `multipart/form-data`）
2. ID 字段在 JSON 中为 **String 类型**（如 `"1823456789012345678"`），因为雪花 ID 的 19 位数字超出 JavaScript 安全整数范围
3. 分页接口中 `sortOrder` 取值固定为 `"ascend"` 或 `"descend"`
4. 公开的图片分页接口（`/picture/list/page/vo`）限制单页最多 20 条

---

## 二、用户接口 `/user`

### 2.1 用户注册

```
POST /user/register

请求体：
{
  "userAccount": "string",    // 必填，≥4 位
  "userPassword": "string",   // 必填，≥8 位
  "checkPassword": "string"   // 必填，需与 userPassword 一致
}

成功响应：
{
  "code": 0,
  "data": 1823456789012345678  // 新用户 ID（JSON 中为 String "1823456789012345678"）
}

可能错误：
- 40000: 账号不能为空 / 密码不能为空 / 账号长度不能小于4位 / 密码长度不能小于8位
- 40000: 两次输入的密码不一致
- 40000: 该账号已被注册
```

### 2.2 用户登录

```
POST /user/login

请求体：
{
  "userAccount": "string",   // 必填
  "userPassword": "string"   // 必填
}

成功响应：
{
  "code": 0,
  "data": {
    "id": "1823456789012345678",
    "userAccount": "admin",
    "userName": "管理员",
    "userAvatar": "https://...",
    "userProfile": "个人简介",
    "userRole": "admin",
    "createTime": "2026-06-13T10:00:00"
  }
}

可能错误：
- 40000: 账号不能为空 / 密码不能为空
- 40000: 账号或密码错误
```

登录成功后服务器返回 `Set-Cookie: JSESSIONID=xxx` 响应头，后续请求浏览器自动携带该 Cookie。

### 2.3 用户注销

```
POST /user/logout
需要登录

成功响应：
{ "code": 0, "data": true }

说明：Servlet 容器管理 Session 过期时间（默认为 30 分钟），主动调用注销立即清除
```

### 2.4 获取当前登录用户

```
GET /user/current
需要登录

成功响应：
{ "code": 0, "data": { UserVO } }

可能错误：
- 40100: 未登录
```

用于前端页面初始化时判断用户是否已登录（如 App.vue 中的 `fetchCurrentUser()`）。

### 2.5 根据 ID 获取脱敏用户

```
GET /user/get/vo?id=1823456789012345678
需要登录

成功响应：
{ "code": 0, "data": { UserVO } }

可能错误：
- 40000: ID 非法
- 40400: 用户不存在
- 40100: 未登录
```

### 2.6 【管理员】创建用户

```
POST /user/add
需要 admin 角色

请求体：
{
  "userAccount": "string",    // 必填，≥4 位
  "userPassword": "string",   // 必填，≥8 位
  "userName": "string",       // 可选，默认取 userAccount
  "userAvatar": "string",     // 可选
  "userProfile": "string",    // 可选
  "userRole": "user|admin"    // 可选，默认 "user"
}

成功响应：
{ "code": 0, "data": 1823456789012345679 }
```

### 2.7 【管理员】删除用户

```
POST /user/delete
需要 admin 角色

请求体：
{ "id": 1823456789012345678 }

成功响应：
{ "code": 0, "data": true }

可能错误：
- 40000: ID 非法
- 40400: 用户不存在
```

**逻辑删除**：数据库 `isDelete` 字段置为 1，数据不物理删除。

### 2.8 【管理员】更新用户

```
POST /user/update
需要 admin 角色

请求体：
{
  "id": 1823456789012345678,   // 必填
  "userName": "string",         // 可选
  "userAvatar": "string",       // 可选
  "userProfile": "string",      // 可选
  "userRole": "user|admin"      // 可选
}

成功响应：
{ "code": 0, "data": true }
```

null 字段不会被更新（保留数据库原值）。**密码不通过此接口修改**。

### 2.9 【管理员】分页获取用户列表

```
POST /user/list/page
需要 admin 角色

请求体：
{
  "current": 1,                // 默认 1
  "pageSize": 10,              // 默认 10，最大 100
  "id": 1823456789012345678,   // 可选，精确匹配
  "userAccount": "admin",      // 可选，模糊匹配
  "userName": "管理员",         // 可选，模糊匹配
  "userRole": "admin"          // 可选，精确匹配
}

成功响应：
{
  "code": 0,
  "data": {
    "records": [ { UserVO }, ... ],
    "total": 42,
    "size": 10,
    "current": 1,
    "pages": 5
  }
}
```

### 2.10 【管理员】获取完整用户

```
GET /user/get?id=1823456789012345678
需要 admin 角色

成功响应：
{ "code": 0, "data": { User 完整实体（含 userPassword 密文） } }

可能错误：
- 40000: ID 非法
- 40400: 用户不存在
```

---

## 三、图片接口 `/picture`

### 3.1 文件上传图片

```
POST /picture/upload
Content-Type: multipart/form-data
需要登录

表单参数：
  file:                    (binary)  图片文件，≤2MB，格式：jpg/jpeg/png/webp

成功响应：
{ "code": 0, "data": { PictureVO } }

注意：非管理员上传的图片将进入"待审核"状态，管理员自动过审。
```

### 3.2 URL 上传图片

```
POST /picture/upload/url
Content-Type: application/json
需要登录

请求体：
{
  "fileUrl": "https://example.com/image.jpg"   // 必填，图片 URL（http/https）
}

成功响应：
{ "code": 0, "data": { PictureVO } }

后端校验流程：
1. URL 格式验证（必须是合法的 http/https URL）
2. HEAD 请求验证文件存在（状态码 200）
3. Content-Type 白名单（image/jpeg, image/png, image/webp）
4. Content-Length ≤ 2MB
5. 下载文件 → COS 上传 → 提取元数据

可能错误：
- 40000: 文件地址不能为空 / 文件地址格式不正确 / 仅支持 HTTP(S) 协议
- 40000: 文件不存在或无法访问 / 文件类型错误 / 文件大小不能超过 2M
```

### 3.3 删除图片

```
POST /picture/delete
需要登录（仅本人或管理员）

请求体：
{ "id": 1823456789012345678 }

成功响应：
{ "code": 0, "data": true }

可能错误：
- 40000: 参数错误（id 为空或 ≤0）
- 40400: 图片不存在
- 40101: 无权限（既不是作者也不是管理员）
```

### 3.3 编辑图片（用户）

```
POST /picture/edit
需要登录（仅本人或管理员）

请求体：
{
  "id": 1823456789012345678,       // 必填
  "name": "新名称",                  // 可选
  "introduction": "新简介（≤800字）", // 可选
  "category": "风景",               // 可选
  "tags": ["标签1", "标签2"]         // 可选（传空数组清空标签）
}

成功响应：
{ "code": 0, "data": true }
```

`editTime` 字段自动更新为当前时间。

### 3.4 【管理员】更新图片

```
POST /picture/update
需要 admin 角色

请求体：同 3.3 编辑图片

区别：管理员可修改任意图片，不受作者限制
```

### 3.5 根据 ID 获取图片 VO（公开）

```
GET /picture/get/vo?id=1823456789012345678
公开

成功响应：
{
  "code": 0,
  "data": {
    "id": "1823456789012345678",
    "url": "https://xx.cos.ap-beijing.myqcloud.com/public/123/20260613_xxx.jpg",
    "name": "示例图片",
    "introduction": "这是一张示例图片",
    "tags": ["风景", "城市"],
    "category": "风景",
    "picSize": "1024000",
    "picWidth": 1920,
    "picHeight": 1080,
    "picScale": 1.78,
    "picFormat": "jpg",
    "userId": "1823456789012345678",
    "createTime": "2026-06-13T10:00:00",
    "editTime": null,
    "updateTime": "2026-06-13T10:00:00",
    "user": { UserVO }               // ★ 关联的上传者信息
  }
}
```

### 3.6 【管理员】获取原始图片实体

```
GET /picture/get?id=1823456789012345678
需要 admin 角色

返回 Picture 实体（不含关联 UserVO，tags 为 JSON 字符串格式）
```

### 3.7 分页获取图片 VO 列表（公开）

```
POST /picture/list/page/vo
公开（最多 20 条/页）

请求体：
{
  "current": 1,
  "pageSize": 12,                   // ≤20
  "searchText": "城市夜景",          // ★ 同时在 name + introduction 中搜索
  "name": "示例",                    // 按名称模糊
  "introduction": "",               // 按简介模糊
  "category": "风景",                // 按分类精确
  "tags": ["标签1"],                 // 按标签（JSON LIKE）
  "picFormat": "jpg",               // 按格式
  "picWidth": 1920,                 // 按宽度
  "picHeight": 1080,                // 按高度
  "picSize": 1024000,               // 按文件大小
  "picScale": 1.78,                 // 按宽高比
  "userId": "1823456789012345678",  // 按上传者
  "sortField": "createTime",        // 排序字段
  "sortOrder": "descend"            // ascend / descend
}

成功响应：
{
  "code": 0,
  "data": {
    "records": [ { PictureVO }, ... ],
    "total": 100,
    "size": 12,
    "current": 1,
    "pages": 9
  }
}
```

**关于 `searchText`**：与其他字段的筛选条件是 AND 关系。`searchText` 在内部被翻译为 `(name LIKE '%kw%' OR introduction LIKE '%kw%')`。

### 3.8 【管理员】分页获取原始图片列表

```
POST /picture/list/page
需要 admin 角色

请求体：同 3.7
返回 Picture 实体列表（不含关联 UserVO）
```

### 3.9 获取预设标签与分类

```
GET /picture/tag_category
公开

成功响应：
{
  "code": 0,
  "data": {
    "tagList": ["热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意"],
    "categoryList": ["模板", "电商", "表情包", "素材", "海报"]
  }
}
```

前端用此接口渲染搜索页面的标签筛选和分类下拉框。

### 3.10 图片审核（管理员）

```
POST /picture/review
需要 admin 角色

请求体：
{
  "id": 1823456789012345678,       // 必填，图片 ID
  "reviewStatus": 1,                // 必填：1=通过, 2=拒绝（不允许设为 0=待审核）
  "reviewMessage": "画面清晰，审核通过" // 可选，审核意见
}

成功响应：
{ "code": 0, "data": true }

可能错误：
- 40000: 请求参数为空 / id 为空 / 状态无效 / 不允许设为待审核
- 40000: 请勿重复审核（已是该状态）
- 40400: 图片不存在
- 40101: 无权限（非管理员）
```

### 3.11 图片列表公开查询（审核过滤说明）

```
POST /picture/list/page/vo
公开 — 自动过滤仅显示 reviewStatus=1（已通过）的数据

后端自动设置：
pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
```

---

## 四、文件接口 `/file`（测试用）

### 4.1 测试上传

```
POST /file/test/upload
Content-Type: multipart/form-data
需要 admin 角色

表单参数：
  file: (binary) 任意文件

成功响应：
{ "code": 0, "data": "/test/filename.jpg" }

说明：文件上传到 COS 的 /test/ 目录，返回 COS 中的路径
```

### 4.2 测试下载

```
GET /file/test/download/?filepath=/test/filename.jpg
需要 admin 角色

响应：
Content-Type: application/octet-stream
Content-Disposition: attachment; filename=/test/filename.jpg

下载文件的二进制流
```

---

## 五、健康检查 `/hi`

```
GET /hi/

响应：
"Hi, welcome to LiPictureCloud!"
```

---

## 六、常见数据模型参考

### UserVO

```json
{
  "id": "1823456789012345678",       // String 类型
  "userAccount": "admin",
  "userName": "管理员",
  "userAvatar": "https://example.com/avatar.jpg",
  "userProfile": "个人简介",
  "userRole": "admin",
  "createTime": "2026-06-13T10:00:00"
}
```

> `userPassword` 和 `isDelete` 字段不出现在 VO 中。

### PictureVO

```json
{
  "id": "1823456789012345678",
  "url": "https://xx-...cos.ap-beijing.myqcloud.com/public/123/20260613_xxx.jpg",
  "name": "示例图片",
  "introduction": "这是一张示例图片",
  "tags": ["标签1", "标签2"],           // ★ List<String>
  "category": "风景",
  "picSize": "1024000",
  "picWidth": 1920,
  "picHeight": 1080,
  "picScale": 1.78,
  "picFormat": "jpg",
  "userId": "1823456789012345678",
  "createTime": "2026-06-13T10:00:00",
  "editTime": null,
  "updateTime": "2026-06-13T10:00:00",
  "user": { UserVO }
}
```

> 注意：`tags` 在数据库中为 JSON 字符串，VO 中已转为 List。
