# Java项目代码提交完整流程规范 (Issue → Branch → PR)

通过标准化的 GitHub Issues 和分支管理流程,确保Java项目代码质量和可追溯性。

## 准备工作

1. **暂存区确认:** 已将所有相关的代码变更添加到 `git` 暂存区 (staging area),不需要进行任何相关操作。
2. **禁止变更:** 此阶段严禁对暂存区进行任何修改(如 `git add` 新文件或 `git rm` 文件)。
3. **梳理总结:** 使用 `git status --short` 和 `git diff --cached` 阅读暂存区内容,总结本次提交所实现的具体功能或修复的问题,以便撰写 Issue 和 Commit Message。

## Java项目特定检查

提交前必须确保以下检查全部通过,这是代码提交的**强制前置条件**:

### 构建验证

**Maven项目:**
```bash
mvn clean verify
```

**Gradle项目:**
```bash
./gradlew clean build
```

### 代码质量检查

1. **单元测试:** 所有测试用例必须通过,覆盖率不低于团队标准(建议 ≥ 80%)
   ```bash
   # Maven
   mvn test
   
   # Gradle
   ./gradlew test
   ```

2. **静态代码分析:** 运行代码检查工具,确保无严重问题
   ```bash
   # Checkstyle
   mvn checkstyle:check
   
   # SpotBugs
   mvn spotbugs:check
   
   # SonarQube (可选)
   mvn sonar:sonar
   ```

3. **代码格式:** 确保代码符合团队编码规范(如 Google Java Style Guide)
   ```bash
   # 使用 spotless 格式化
   mvn spotless:check
   ```

4. **依赖安全扫描:** 检查是否存在已知漏洞依赖
   ```bash
   mvn dependency-check:check
   ```

### 提交内容审查

- 确认没有包含敏感信息(密码、密钥、内部地址等)
- 确认没有提交无关文件(IDE配置、日志文件、临时文件等)
- 确认代码变更逻辑完整,不会导致编译或运行时错误

---

## 标准流程

### 1. 创建 Issue

使用 `gh issue create` 命令创建任务:

```bash
gh issue create --title "feat(user-service): 添加JWT用户认证模块" \
                --body "$(cat issue-template.md)" \
                --label "type/feature,priority/P1"
```

**Issue 内容要求:**

- **Title:** 简明扼要,格式为 `<类型>(<范围>): <描述>`
- **Body:** 包含以下内容
    - 背景说明:为什么需要这个变更
    - 需求描述:具体要实现什么功能
    - 技术方案:关键技术选型和实现思路(可选)
    - 验收标准:如何判断任务完成
    - 相关文档:架构图、接口文档、原型图等
- **Labels:** 必须添加类型、优先级标签,详见 [Labels 规范](#github-labels-规范)

### 2. 创建特性分支

从最新的主干分支创建特性分支:

```bash
# 更新本地主干分支
git checkout main
git pull origin main

# 创建并切换到特性分支
git checkout -b feat/jwt-auth-#123
```

分支命名必须遵循 [分支命名规范](#分支命名规范)。

### 3. 提交变更

将暂存区的变更提交到本地分支:

```bash
git commit
```

Commit Message 必须遵循 [提交信息规范](#提交信息规范)。

**提交粒度原则:**
- 一次提交应该是一个逻辑完整、功能独立的变更
- 重构和功能开发必须分开提交
- 避免将多个不相关的功能混在一个 commit 中
- 每个 commit 都应该能够独立编译和运行

### 4. 推送代码并创建 PR

```bash
# 推送分支到远程仓库
git push -u origin feat/jwt-auth-#123

# 创建 Pull Request
gh pr create --title "feat(user-service): 添加JWT用户认证模块" \
             --body "Closes #123" \
             --label "type/feature,priority/P1"
```

**PR 内容要求:**

- **Title:** 与对应的 Issue 或主要 Commit 保持一致
- **Body:** 包含
    - 变更说明:做了什么改动
    - 测试说明:如何验证这些改动
    - 关联 Issue: `Closes #123`
    - 截图/演示(如涉及UI变更)
- **Reviewers:** 指定至少2名代码审查者
- **CI/CD:** 确保所有自动化检查通过

### 5. 代码审查

**审查者职责:**
- 检查代码逻辑正确性和安全性
- 验证是否符合架构设计和编码规范
- 确认单元测试覆盖充分
- 评估性能影响和潜在风险

**提交者职责:**
- 及时响应审查意见
- 修改代码并更新PR
- 所有讨论必须达成一致后才能合并

### 6. 合并代码

**合并前检查清单:**
- [ ] 所有CI/CD检查通过
- [ ] 至少2名审查者批准
- [ ] 代码冲突已解决
- [ ] 分支基于最新的主干分支

**合并策略:**
- 使用 `Squash and Merge` 保持主干分支历史清晰
- 或使用 `Rebase and Merge` 保留详细的提交历史

---

## 开发规范

### 分支命名规范

**格式:** `<type>/<short-desc>-#<issue>`

**类型 (Type):**
- `feat` - 新功能
- `fix` - Bug修复
- `refactor` - 重构
- `docs` - 文档更新
- `test` - 测试相关
- `chore` - 构建/配置变更

**示例:**
- `feat/jwt-auth-#123`
- `fix/order-query-npe-#456`
- `refactor/service-layer-#789`

**多模块项目建议:** 在描述中包含模块名,如 `feat/user-service-jwt-#123`

---

### 提交信息规范

严格遵循**约定式提交 (Conventional Commits)** 格式,所有提交信息使用中文。

**格式:**

```
<类型>(<范围>): <主题>

[可选的正文]

[可选的页脚]
```

**类型 (Type):**

- `feat` ✨ 新功能
- `fix` 🐛 错误修复
- `refactor` ♻️ 代码重构(不改变外部行为)
- `perf` ⚡ 性能优化
- `test` ✅ 测试相关
- `docs` 📝 文档更新
- `style` 🎨 代码格式调整(不影响逻辑)
- `chore` 🔧 构建配置、依赖更新等

**范围 (Scope):**

建议使用**模块名**或**核心包名**,便于快速定位变更位置:

- 单体应用: 使用包名,如 `user.service`, `order.repository`, `common.utils`
- 微服务: 使用服务名,如 `user-service`, `order-service`, `gateway`
- 多模块项目: 使用模块名,如 `user-api`, `user-service`, `common-core`

**主题 (Subject):**
- 简明扼要,不超过50个字符
- 使用动词开头,如"添加"、"修复"、"重构"
- 不要句号结尾

**正文 (Body):**
- 详细说明变更的原因、内容和影响
- 使用列表格式,每项一行
- 说明关键技术细节和注意事项

**页脚 (Footer):**
- 使用 `Closes #issue` 关联并自动关闭 Issue
- 使用 `BREAKING CHANGE:` 标识破坏性变更
- 使用 `Refs #issue` 引用但不关闭 Issue

**示例 1: 新功能**

```
feat(user-service): 添加基于JWT的用户认证功能

- 实现JwtTokenProvider用于生成和验证JWT令牌
- 添加JwtAuthenticationFilter拦截器验证请求
- 实现UserDetailsService加载用户信息
- 配置Spring Security安全策略
- 添加相关单元测试和集成测试

测试覆盖率: 85%
性能影响: 认证平均耗时 < 50ms

Closes #123
```

**示例 2: Bug修复**

```
fix(order.repository): 修复订单查询中的空指针异常

问题: 当订单状态为null时,状态过滤条件导致NPE

解决方案:
- 在OrderRepository.findByStatus()中添加null检查
- 使用Optional包装可能为null的状态参数
- 完善异常处理和日志记录

影响范围: 订单查询接口
回归测试: 已通过

Closes #456
```

**示例 3: 重构**

```
refactor(common.utils): 重构日期工具类提升可维护性

- 使用Java 8 time API替代过时的Date类
- 提取常用日期格式为常量
- 简化方法命名,提高可读性
- 补充完整的JavaDoc文档
- 添加边界条件单元测试

BREAKING CHANGE: DateUtils.format()方法签名变更,
需要传入DateTimeFormatter而非String格式

Refs #789
```

**示例 4: 性能优化**

```
perf(order.service): 优化订单列表查询性能

- 添加订单状态和创建时间复合索引
- 使用分页查询避免全表扫描
- 优化关联查询,减少N+1问题
- 添加Redis缓存热点数据

性能提升:
- 查询响应时间从 800ms 降至 120ms
- 数据库CPU使用率降低 40%
- 支持并发数从 100 提升至 500

Closes #321
```

---

### GitHub Labels 规范

**优先级 (Priority):**

- `priority/P0` 🔥 紧急 (线上故障,立即处理)
- `priority/P1` 🔴 高优先级 (重要功能,本迭代必须完成)
- `priority/P2` 🔵 中优先级 (正常排期)
- `priority/P3` 🟢 低优先级 (可延后处理)

**状态 (Status):**

- `status/triage` 🔷 待梳理 (新创建,待评估)
- `status/planned` 📋 已计划 (已排期,待开始)
- `status/in-progress` 🚧 开发中
- `status/in-review` 🔍 代码审查中
- `status/blocked` 🚫 阻塞 (等待依赖或决策)
- `status/done` ✅ 已完成

**类型 (Type):**

- `type/feature` ✨ 新功能
- `type/bug` 🐛 错误修复
- `type/refactor` ♻️ 代码重构
- `type/perf` ⚡ 性能优化
- `type/test` ✅ 测试相关
- `type/docs` 📝 文档更新
- `type/chore` 🔧 构建配置变更

**领域 (Domain):** (根据项目实际情况定义)

- `domain/auth` 认证授权
- `domain/order` 订单模块
- `domain/user` 用户模块
- `domain/payment` 支付模块
- `domain/infra` 基础设施

**其他 (Others):**

- `question` ❓ 需要讨论的问题
- `duplicate` 🔁 重复的Issue
- `wontfix` ❌ 不修复
- `help-wanted` 🙋 需要帮助
- `good-first-issue` 🌱 适合新人

---

## 最佳实践

### Commit 粒度建议

**推荐做法:**
```
✅ commit 1: refactor(user.service): 提取用户验证逻辑到独立方法
✅ commit 2: feat(user.service): 添加手机号登录功能
✅ commit 3: test(user.service): 补充手机号登录单元测试
```

**不推荐做法:**
```
❌ commit 1: 添加手机号登录 + 修复用户查询bug + 更新README
```

### 分支管理

- **主干分支 (main/master):** 始终保持可发布状态,只接受经过审查的PR
- **开发分支 (develop):** 可选,用于集成测试
- **特性分支:** 从主干或开发分支创建,完成后合并回去并删除
- **热修复分支 (hotfix/*):** 紧急修复线上问题,直接从主干创建

### 代码审查清单

**功能性:**
- [ ] 代码实现了Issue描述的需求
- [ ] 边界条件和异常情况处理完善
- [ ] 没有引入新的bug或副作用

**代码质量:**
- [ ] 符合团队编码规范和架构设计
- [ ] 命名清晰,逻辑易懂
- [ ] 没有重复代码,遵循DRY原则
- [ ] 适当的设计模式应用

**测试:**
- [ ] 单元测试覆盖核心逻辑
- [ ] 测试用例充分,包含正常和异常场景
- [ ] 集成测试验证端到端流程

**安全性:**
- [ ] 输入验证和SQL注入防护
- [ ] 敏感信息加密和脱敏
- [ ] 权限控制正确实施

**性能:**
- [ ] 没有明显的性能问题
- [ ] 数据库查询优化(索引、分页)
- [ ] 合理使用缓存

**文档:**
- [ ] 关键方法有JavaDoc注释
- [ ] 复杂逻辑有注释说明
- [ ] API文档更新(如使用Swagger)

---

## 常见问题

**Q: 已经提交到本地分支,发现commit message写错了怎么办?**

A: 使用 `git commit --amend` 修改最后一次提交的信息。如果已经推送到远程,需要使用 `git push --force-with-lease`。

**Q: 特性分支落后主干分支很多提交,如何更新?**

A: 建议使用 rebase 保持历史清晰:
```bash
git checkout main
git pull
git checkout feat/your-branch
git rebase main
# 解决冲突后
git push --force-with-lease
```

**Q: 一个功能涉及多个模块修改,如何组织提交?**

A: 按照逻辑单元拆分提交:
- 先提交基础设施/公共模块的变更
- 再提交业务模块的变更
- 最后提交配置和文档更新
- 确保每个commit都能独立编译通过

**Q: hotfix分支如何处理?**

A: 紧急修复流程:
1. 从main创建 `hotfix/critical-bug-#999`
2. 修复并充分测试
3. 合并回main并立即发布
4. 将修复同步回develop分支(如果存在)

**Q: 代码审查中发现较大问题需要重构,怎么办?**

A: 关闭当前PR,创建新的重构Issue和分支,按照标准流程重新提交。保持PR专注于单一目标,避免在审查中进行大规模改动。

---

## 工具推荐

- **IDE插件:** IntelliJ IDEA Git Integration, GitLens for VS Code
- **Commit规范检查:** Commitlint, Husky (Git Hooks)
- **代码格式化:** Spotless, google-java-format
- **静态分析:** Checkstyle, SpotBugs, SonarQube
- **依赖检查:** OWASP Dependency-Check
- **CLI工具:** GitHub CLI (`gh`), Git Extras

---

## 附录:自动化配置示例

### Maven pom.xml 配置片段

```xml
<build>
    <plugins>
        <!-- Checkstyle -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-checkstyle-plugin</artifactId>
            <version>3.3.0</version>
            <configuration>
                <configLocation>checkstyle.xml</configLocation>
            </configuration>
        </plugin>
        
        <!-- SpotBugs -->
        <plugin>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs-maven-plugin</artifactId>
            <version>4.7.3.5</version>
        </plugin>
        
        <!-- JaCoCo 代码覆盖率 -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.10</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Git Hooks 示例 (.git/hooks/pre-commit)

```bash
#!/bin/bash

echo "Running pre-commit checks..."

# 运行构建
mvn clean verify -DskipTests=false

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please fix the errors before committing."
    exit 1
fi

# 运行代码检查
mvn checkstyle:check

if [ $? -ne 0 ]; then
    echo "❌ Checkstyle violations found. Please fix before committing."
    exit 1
fi

echo "✅ All checks passed!"
exit 0
```

---

**文档版本:** v1.0
**最后更新:** 2024-11-28
**维护者:** 技术委员会