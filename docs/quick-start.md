# 快速开始指南（5 分钟）

## 前置条件

- JDK 17+
- Maven 3.8+
- Docker 和 Docker Compose（可选）

## 第 1 步：克隆并构建（2 分钟）

```bash
git clone https://github.com/dengxingzhi000/CommonPermissionsFramework.git
cd CommonPermissionsFramework
mvn clean install -DskipTests
```

## 第 2 步：启动依赖服务（1 分钟）

```bash
# 使用 Docker Compose 启动所有依赖
docker-compose -f docker-compose.yml up -d

# 或者启动 Nacos
docker run -d --name nacos -e MODE=standalone -p 8848:8848 nacos/nacos-server:v2.2.0
```

## 第 3 步：启动应用（2 分钟）

在三个不同的终端中启动服务：

```bash
# 终端 1: system-service
cd system && mvn spring-boot:run

# 终端 2: auth-service
cd ../auth && mvn spring-boot:run

# 终端 3: gateway-service
cd ../gateway && mvn spring-boot:run
```

## 第 4 步：验证部署

```bash
# 检查健康状态
curl http://localhost:9095/health

# 获取 JWT 令牌
curl -X POST http://localhost:9095/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 响应示例
{
  "code": 200,
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

## 第 5 步：使用 API

```bash
# 获取用户列表
curl http://localhost:9095/system/users \
  -H "Authorization: Bearer YOUR_TOKEN"

# 创建新用户
curl -X POST http://localhost:9095/system/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "username": "newuser",
    "password": "password123",
    "email": "user@example.com"
  }'
```

## 下一步

- 查看 [完整文档](./README.md)
- 阅读 [API 文档](./api-guide.md)
- 探索 [使用示例](./examples.md)

## 常见问题

**Q: 端口被占用？**  
A: 修改 `application.yaml` 中的 `server.port`

**Q: 数据库连接失败？**  
A: 检查数据库是否运行且配置正确

**Q: 找不到 Nacos？**  
A: 确保 Nacos 已启动且可访问 http://localhost:8848

更多帮助见 [故障排查](./troubleshooting.md)。
