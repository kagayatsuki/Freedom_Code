# 智能代码生成平台 API 接口文档

> **Version:** 1.0.0
> **Base URL:** `http://localhost:8080`

---

## 通用约定

### 请求头

| 字段 | 值 | 说明 |
|---|---|---|
| Content-Type | application/json | 所有请求使用 JSON 格式 |
| Authorization | Bearer {token} | 需要认证的接口必须携带 |

### 统一响应格式

所有接口返回以下 JSON 结构：

```json
{
  "code": 200,
  "message": "操作描述",
  "data": {}
}
```

### 状态码

| 状态码 | 说明 |
|---|---|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权，token 无效或已过期 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 1. 认证模块 `/api/auth`

### 1.1 用户注册

**POST** `/api/auth/register`

无需认证。

**请求体**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名，3-20 字符 |
| password | string | 是 | 密码，6-32 字符 |
| email | string | 是 | 邮箱地址 |

**请求示例**

```json
{
  "username": "newuser",
  "password": "123456",
  "email": "user@example.com"
}
```

**成功响应**

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 2,
      "username": "newuser",
      "email": "user@example.com",
      "avatar": ""
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| data.token | string | JWT 令牌，后续请求需携带 |
| data.user.id | number | 用户 ID |
| data.user.username | string | 用户名 |
| data.user.email | string | 邮箱 |
| data.user.avatar | string | 头像 URL，为空时前端使用默认头像 |

---

### 1.2 用户登录

**POST** `/api/auth/login`

无需认证。

**请求体**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**请求示例**

```json
{
  "username": "admin",
  "password": "123456"
}
```

**成功响应**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "admin",
      "email": "admin@example.com",
      "avatar": ""
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| data.token | string | JWT 令牌 |
| data.user.id | number | 用户 ID |
| data.user.username | string | 用户名 |
| data.user.email | string | 邮箱 |
| data.user.avatar | string | 头像 URL |

---

### 1.3 获取当前用户信息

**GET** `/api/auth/user`

需要认证。

**请求头**

```
Authorization: Bearer {token}
```

**成功响应**

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "user": {
      "id": 1,
      "username": "admin",
      "email": "admin@example.com",
      "avatar": "",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| data.user.id | number | 用户 ID |
| data.user.username | string | 用户名 |
| data.user.email | string | 邮箱 |
| data.user.avatar | string | 头像 URL |
| data.user.createdAt | string | 注册时间 |
