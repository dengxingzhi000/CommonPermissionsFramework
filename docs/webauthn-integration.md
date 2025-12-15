# WebAuthn 集成文档

## 概述

本项目已集成 WebAuthn (Web Authentication) 标准，提供基于生物识别和硬件密钥的无密码认证功能。

**参考标准：**
- W3C Web Authentication API Level 2
- FIDO2 CTAP2 Protocol
- Google Passkey Implementation

**安全特性：**
- 🔐 凭证ID全局唯一，防止凭证碰撞
- 🛡️ 签名计数器防重放攻击
- 🔒 公钥隔离存储，降低泄露风险
- 📱 支持多设备绑定，提升用户体验
- ✅ 符合FIDO2和W3C WebAuthn标准

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         客户端 (Browser)                         │
│  ┌────────────────┐         ┌─────────────────────────────┐    │
│  │  Web UI        │────────▶│  WebAuthn API              │    │
│  │  (JavaScript)  │         │  navigator.credentials     │    │
│  └────────────────┘         └─────────────────────────────┘    │
└──────────────────────────────────┬──────────────────────────────┘
                                   │ HTTPS
                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                      后端服务 (Spring Boot)                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Controller: WebAuthnCredentialController                 │  │
│  │  - POST /api/auth/webauthn/register/challenge            │  │
│  │  - POST /api/auth/webauthn/register/verify               │  │
│  │  - POST /api/auth/webauthn/authenticate/challenge        │  │
│  │  - POST /api/auth/webauthn/authenticate/verify           │  │
│  │  - GET  /api/auth/webauthn/credentials                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Service: IWebauthnCredentialService                      │  │
│  │  - 注册流程：生成挑战、验证证明                           │  │
│  │  - 认证流程：生成挑战、验证断言、升级Token               │  │
│  │  - 凭证管理：查询、更新、删除、健康检查                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Mapper: WebauthnCredentialMapper (MyBatis-Plus)         │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    数据库 (MySQL/PostgreSQL)                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Table: webauthn_credential                              │  │
│  │  - id, credential_id, user_id                            │  │
│  │  - public_key_pem, alg, sign_count                       │  │
│  │  - device_name, aaguid, transports                       │  │
│  │  - is_active, last_used_at                               │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 数据库设计

### 表结构

详见 `scripts/db/001_schema.sql`

**关键字段说明：**

| 字段                      | 类型           | 说明                                      |
|---------------------------|----------------|-------------------------------------------|
| `id`                      | VARCHAR(64)    | 主键ID                                    |
| `credential_id`           | VARCHAR(1024)  | WebAuthn凭证ID（base64url编码）          |
| `user_id`                 | VARCHAR(64)    | 用户ID（业务外键）                       |
| `public_key_pem`          | VARCHAR(2048)  | 公钥（PEM格式）                          |
| `alg`                     | VARCHAR(64)    | 签名算法（ES256/RS256/EdDSA）            |
| `sign_count`              | BIGINT         | 签名计数器（防重放攻击）                  |
| `device_name`             | VARCHAR(100)   | 设备名称（用户自定义）                   |
| `authenticator_attachment`| VARCHAR(20)    | 认证器类型（platform/cross-platform）     |
| `is_active`               | BOOLEAN        | 启用状态                                  |

### 索引设计

```sql
-- 用户ID索引（最常用）
CREATE INDEX idx_user_id ON webauthn_credential(user_id);

-- 用户ID + 凭证ID复合索引
CREATE INDEX idx_user_cred ON webauthn_credential(user_id, credential_id);

-- 活跃凭证覆盖索引
CREATE INDEX idx_active_creds ON webauthn_credential(user_id, is_active, last_used_at DESC);
```

## API 接口文档

### 1. 注册流程

#### 1.1 生成注册挑战

**请求：**
```http
POST /api/auth/webauthn/register/challenge?rpId=example.com
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "challenge": "KSjKz3HHnUhFIAoS4RFCw...",
    "rpId": "example.com",
    "timeout": 300000,
    "user": {
      "id": "user_123456",
      "name": "john@example.com",
      "displayName": "john@example.com"
    },
    "attestation": "none"
  }
}
```

#### 1.2 验证并注册凭证

**请求：**
```http
POST /api/auth/webauthn/register/verify
Authorization: Bearer {token}
Content-Type: application/json

{
  "credentialId": "KSjKz3HHnUhFIAoS4RFCw",
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "algorithm": "ES256",
  "deviceName": "我的iPhone",
  "aaguid": "08987058-cadc-4b81-b6e1-30de50dcbe96",
  "transports": "internal",
  "authenticatorAttachment": "platform",
  "backupState": false
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "credentialId": "KSjKz3HHnUhFIAoS4RFCw",
    "deviceName": "我的iPhone",
    "algorithm": "ES256",
    "authenticatorAttachment": "platform",
    "isActive": true,
    "createdTime": "2025-11-27 10:00:00"
  }
}
```

### 2. 认证流程

#### 2.1 生成认证挑战

**请求：**
```http
POST /api/auth/webauthn/authenticate/challenge?rpId=example.com
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "challenge": "XYZ123...",
    "rpId": "example.com",
    "timeout": 120000,
    "allowCredentials": [
      {
        "id": "KSjKz3HHnUhFIAoS4RFCw",
        "type": "public-key",
        "transports": ["internal"]
      }
    ],
    "userVerification": "preferred"
  }
}
```

#### 2.2 验证认证并升级Token

**请求：**
```http
POST /api/auth/webauthn/authenticate/verify
Authorization: Bearer {token}
Content-Type: application/json

{
  "credentialId": "KSjKz3HHnUhFIAoS4RFCw",
  "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiWFlaMTIzIiwib3JpZ2luIjoiaHR0cHM6Ly9leGFtcGxlLmNvbSJ9",
  "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MFAAAAAQ==",
  "signature": "MEUCIQDzK...",
  "signCount": 43
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

### 3. 凭证管理

#### 3.1 列出所有凭证

**请求：**
```http
GET /api/auth/webauthn/credentials
Authorization: Bearer {token}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "credentialId": "KSjKz3HHnUhFIAoS4RFCw",
      "deviceName": "我的iPhone",
      "algorithm": "ES256",
      "authenticatorAttachment": "platform",
      "isActive": true,
      "lastUsedAt": "2025-11-27 10:30:00",
      "createdTime": "2025-11-27 10:00:00"
    }
  ]
}
```

#### 3.2 更新设备名称

**请求：**
```http
PUT /api/auth/webauthn/credentials/{credentialId}/name?deviceName=新名称
Authorization: Bearer {token}
```

#### 3.3 删除凭证

**请求：**
```http
DELETE /api/auth/webauthn/credentials/{credentialId}
Authorization: Bearer {token}
```

#### 3.4 停用凭证

**请求：**
```http
PUT /api/auth/webauthn/credentials/{credentialId}/deactivate
Authorization: Bearer {token}
```

#### 3.5 检查凭证健康状态

**请求：**
```http
GET /api/auth/webauthn/credentials/health
Authorization: Bearer {token}
```

**响应：返回长期未使用或异常的凭证列表**

## 客户端集成示例

### JavaScript 集成

```javascript
// 1. 注册新凭证
async function registerWebAuthn() {
  // 获取注册挑战
  const challengeResponse = await fetch('/api/auth/webauthn/register/challenge?rpId=example.com', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  const challengeData = await challengeResponse.json();

  // 调用 WebAuthn API
  const credential = await navigator.credentials.create({
    publicKey: {
      challenge: base64urlDecode(challengeData.data.challenge),
      rp: {
        name: "My App",
        id: challengeData.data.rpId
      },
      user: {
        id: base64urlDecode(challengeData.data.user.id),
        name: challengeData.data.user.name,
        displayName: challengeData.data.user.displayName
      },
      pubKeyCredParams: [
        { type: "public-key", alg: -7 },  // ES256
        { type: "public-key", alg: -257 } // RS256
      ],
      timeout: challengeData.data.timeout,
      attestation: "none",
      authenticatorSelection: {
        authenticatorAttachment: "platform",
        requireResidentKey: false,
        userVerification: "preferred"
      }
    }
  });

  // 提取公钥和其他信息
  const publicKeyPem = await extractPublicKeyPem(credential);

  // 验证并注册
  const registerResponse = await fetch('/api/auth/webauthn/register/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      credentialId: base64urlEncode(credential.rawId),
      publicKeyPem: publicKeyPem,
      algorithm: "ES256",
      deviceName: "我的设备",
      authenticatorAttachment: "platform"
    })
  });

  return await registerResponse.json();
}

// 2. 认证
async function authenticateWebAuthn() {
  // 获取认证挑战
  const challengeResponse = await fetch('/api/auth/webauthn/authenticate/challenge?rpId=example.com', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  const challengeData = await challengeResponse.json();

  // 调用 WebAuthn API
  const assertion = await navigator.credentials.get({
    publicKey: {
      challenge: base64urlDecode(challengeData.data.challenge),
      rpId: challengeData.data.rpId,
      timeout: challengeData.data.timeout,
      allowCredentials: challengeData.data.allowCredentials.map(cred => ({
        type: cred.type,
        id: base64urlDecode(cred.id),
        transports: cred.transports
      })),
      userVerification: "preferred"
    }
  });

  // 验证认证
  const verifyResponse = await fetch('/api/auth/webauthn/authenticate/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      credentialId: base64urlEncode(assertion.rawId),
      clientDataJSON: base64urlEncode(assertion.response.clientDataJSON),
      authenticatorData: base64urlEncode(assertion.response.authenticatorData),
      signature: base64urlEncode(assertion.response.signature),
      signCount: extractSignCount(assertion.response.authenticatorData)
    })
  });

  const result = await verifyResponse.json();
  // 使用新的 accessToken
  localStorage.setItem('token', result.data.accessToken);
  return result;
}

// 辅助函数
function base64urlDecode(str) {
  const base64 = str.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}

function base64urlEncode(buffer) {
  const binary = String.fromCharCode(...new Uint8Array(buffer));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}
```

## 安全考虑

### 1. 防重放攻击

- ✅ 使用签名计数器（`sign_count`）验证
- ✅ 每次认证后计数器必须递增
- ✅ 如果计数器回退，拒绝认证并告警

### 2. 防克隆攻击

- ✅ 检测签名计数器异常
- ✅ 记录认证尝试日志
- ✅ 凭证健康检查API

### 3. 传输安全

- ✅ 必须使用HTTPS
- ✅ 挑战有效期限制（注册5分钟，认证2分钟）
- ✅ 挑战使用后立即删除

### 4. 数据保护

- ✅ 公钥存储在数据库中
- ⚠️ 建议生产环境加密存储公钥
- ✅ 敏感字段不在API响应中暴露

## 待办事项 (TODO)

### 高优先级

1. **集成标准WebAuthn库**
   - [ ] 接入 [Yubico WebAuthn Server](https://github.com/Yubico/java-webauthn-server)
   - [ ] 完整验证 attestation（证明）签名
   - [ ] 完整验证 assertion（断言）签名
   - [ ] 验证 RP ID 和 Origin

2. **公钥加密存储**
   - [ ] 使用数据库加密功能
   - [ ] 或集成 HSM (Hardware Security Module)

### 中优先级

3. **凭证备份和恢复**
   - [ ] 实现备份恢复码机制
   - [ ] 支持凭证同步到云端

4. **增强监控和告警**
   - [ ] 异常认证模式检测
   - [ ] 自动停用可疑凭证
   - [ ] 发送安全告警通知

### 低优先级

5. **用户体验优化**
   - [ ] 支持多语言
   - [ ] 提供前端UI组件
   - [ ] 添加设备图标识别

## 参考资料

- [W3C Web Authentication Specification](https://www.w3.org/TR/webauthn-2/)
- [FIDO2 Project](https://fidoalliance.org/fido2/)
- [Google Passkey Implementation Guide](https://developers.google.com/identity/passkeys)
- [Yubico WebAuthn Guide](https://developers.yubico.com/WebAuthn/)

## 支持的认证器

- ✅ **平台认证器 (Platform Authenticators)**
  - Apple Touch ID / Face ID
  - Windows Hello
  - Android Biometrics

- ✅ **跨平台认证器 (Cross-Platform Authenticators)**
  - YubiKey
  - Google Titan Key
  - 其他FIDO2安全密钥

## 联系方式

如有问题或建议，请联系：
- 项目负责人：system
- 创建日期：2025-11-27
