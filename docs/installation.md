# 安装指南

## 前置需求

- **JDK**: 17 或更高版本
- **Maven**: 3.8 或更高版本
- **MySQL**: 8.0 或更高版本
- **Redis**: 6.0 或更高版本
- **Docker**: （可选，用于容器化部署）

## 步骤 1: 克隆项目

```bash
git clone https://github.com/dengxingzhi000/CommonPermissionsFramework.git
cd CommonPermissionsFramework
```

## 步骤 2: 配置数据库

### 创建数据库

```bash
mysql -u root -p << EOF
CREATE DATABASE cpf_auth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE cpf_system DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE cpf_gateway DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
