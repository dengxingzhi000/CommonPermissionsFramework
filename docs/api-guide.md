# API 文档指南

## 认证 API

### 用户登录

**端点**: `POST /auth/login`

**请求体**:
```json
{
  "username": "admin",
  "password": "password123"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

### 刷新令牌

**端点**: `POST /auth/refresh`

**请求头**:
```
Authorization: Bearer <access_token>
```

**响应**: 返回新的 access_token

### 用户注册

**端点**: `POST /auth/register`

**请求体**:
```json
{
  "username": "newuser",
  "password": "password123",
  "email": "user@example.com"
}
```

## 用户管理 API

### 获取用户列表

**端点**: `GET /system/users`

**查询参数**:
- `page`: 页码（默认 1）
- `size`: 每页数量（默认 10）
- `search`: 搜索关键字（可选）

**请求头**:
```
Authorization: Bearer <access_token>
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "content": [
      {
        "id": 1,
        "username": "admin",
        "email": "admin@example.com",
        "status": "ACTIVE"
      }
    ],
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 创建用户

**端点**: `POST /system/users`

**请求头**:
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**:
```json
{
  "username": "newuser",
  "password": "password123",
  "email": "user@example.com",
  "fullName": "New User",
  "departmentId": 1
}
```

### 更新用户

**端点**: `PUT /system/users/{id}`

**请求头**:
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**请求体**: 同创建用户（password 可选）

### 删除用户

**端点**: `DELETE /system/users/{id}`

**请求头**:
```
Authorization: Bearer <access_token>
```

## 角色管理 API

### 获取角色列表

**端点**: `GET /system/roles`

### 创建角色

**端点**: `POST /system/roles`

**请求体**:
```json
{
  "name": "MANAGER",
  "description": "Manager role",
  "permissions": [1, 2, 3]
}
```

## 权限管理 API

### 获取权限列表

**端点**: `GET /system/permissions`

### 检查权限

**端点**: `POST /system/permissions/check`

**请求体**:
```json
{
  "userId": 1,
  "permissionCode": "USER_VIEW"
}
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "hasPermission": true
  }
}
```

## 错误处理

所有 API 的错误响应格式：

```json
{
  "code": 400,
  "message": "Invalid request",
  "data": null,
  "timestamp": "2025-12-11T10:00:00Z"
}
```

### 常见错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器错误 |

## 速率限制

- 每个用户每分钟最多 100 个请求
- 超出限制返回 429 (Too Many Requests)

## 数据格式

所有日期时间字段使用 ISO 8601 格式：
```
2025-12-11T10:30:00Z
```

更多详情见 Swagger UI: `http://localhost:9095/swagger-ui.html`
