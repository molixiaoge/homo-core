# homo-core

<div align="center">

![homo-core](https://img.shields.io/badge/homo--core-v1.0.0-blue.svg)
![Java](https://img.shields.io/badge/Java-8+-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7+-green.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**针对游戏开发场景设计的全响应式分布式框架**

[快速开始](#快速开始) • [文档](#文档) • [示例](#示例) • [社区](#社区)

</div>

---

## ✨ 特性

- 🚀 **全响应式编程** - 基于 Reactor 框架，全异步非阻塞操作
- 🌐 **多协议 RPC** - 支持 HTTP、gRPC 等多种通信协议
- 💾 **智能存储** - 多级缓存 + 自动落地，Redis + MySQL 存储架构
- 🎮 **有状态实体** - 支持基于实体ID的有状态服务调用
- 📨 **消息队列** - 支持 Kafka、Redis 等多种消息队列
- 🔍 **链路追踪** - 基于 Zipkin 的全链路追踪
- 🔒 **分布式锁** - 基于 Redis 的分布式锁实现
- 🛠️ **开发工具** - 一键部署、一键导表等 Maven 插件

## 🏗️ 架构

```text
┌────────────────────────────┐
│          应用层            │
│  游戏服务                  │
│   ├── RPC 服务             │
│   ├── 实体服务             │
│   └── 网关服务             │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│          框架层            │
│  RPC 模块    实体模块      │
│      \        /           │
│        → 存储模块 ← 网关模块
│                  \        │
│                 消息队列   │
│      ├─ 缓存层             │
│      └─ 数据库层           │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│          基础设施          │
│  Redis   MySQL   MongoDB   │
│           Kafka            │
└────────────────────────────┘
```

## 🚀 快速开始

### 环境要求

- **JDK**: 8 或更高版本
- **Maven**: 3.6.0 或更高版本
- **Redis**: 3.0 或更高版本
- **MySQL**: 5.7 或更高版本

### 创建项目

```bash
# 1. 克隆项目
git clone https://github.com/your-org/homo-core.git
cd homo-core

# 2. 编译项目
mvn clean install

# 3. 运行示例
cd homo-core-test/homo-game-demo
mvn spring-boot:run
```

### 添加依赖

```xml
<dependencies>
    <!-- 核心模块 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-system</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- 存储模块 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-storage</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- RPC 模块 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-server</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 第一个服务

```java
// 1. 定义服务接口
@ServiceExport(tagName = "hello-service:8080", isMainServer = true)
@RpcHandler
public interface HelloServiceFacade {
    Homo<String> sayHello(String name);
}

// 2. 实现服务
@Component
public class HelloService extends BaseService implements HelloServiceFacade {
    @Override
    public Homo<String> sayHello(String name) {
        return Homo.result("Hello, " + name + "!");
    }
}

// 3. 启动应用
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 📚 文档

### 核心模块

| 模块 | 描述 | 文档 |
|------|------|------|
| **RPC** | 远程过程调用，支持 HTTP/gRPC | [RPC 使用指南](docs/Rpc/RPC模块使用指南.md) |
| **Storage** | 智能存储，多级缓存 + 自动落地 | [存储使用指南](docs/Storage/存储模块使用指南.md) |
| **Entity** | 有状态实体，支持远程调用 | [实体使用指南](docs/Entity/实体模块使用指南.md) |
| **Gateway** | 网关服务，支持 TCP/HTTP | [网关使用指南](docs/交互网关/网关设计使用文档.md) |

### 数据存储

| 模块 | 描述 | 文档 |
|------|------|------|
| **Relational** | 响应式关系数据库 | [响应式数据库使用指南](docs/响应式数据库/响应式数据库使用指南.md) |
| **Document** | 文档数据库，支持 MongoDB | [文档数据库使用指南](docs/Document/文档数据库使用指南.md) |
| **Cache** | 缓存服务，基于 Redis | [缓存使用指南](docs/缓存/缓存驱动设计使用文档.md) |

### 消息通信

| 模块 | 描述 | 文档 |
|------|------|------|
| **MessageQueue** | 消息队列，支持 Kafka/Redis | [消息队列使用指南](docs/消息队列/消息队列使用指南.md) |
| **Distributed Lock** | 分布式锁 | [分布式锁使用指南](docs/分布式锁/分布式锁使用指南.md) |

### 开发工具

| 模块 | 描述 | 文档 |
|------|------|------|
| **Plugin** | Maven 插件，一键部署/导表 | [插件使用指南](docs/插件/插件模块使用指南.md) |
| **Trace** | 链路追踪，基于 Zipkin | [链路追踪使用指南](docs/链路追踪/链路追踪使用指南.md) |

### 技术设计

| 文档 | 描述 |
|------|------|
| [框架总体设计](docs/技术设计文档/homo-core框架总体技术设计文档.md) | 框架整体架构和设计理念 |
| [模块设计文档](docs/技术设计文档/) | 各模块详细技术设计文档 |
| [数据落地技术设计文档](docs/技术设计文档/数据落地技术设计文档.md) | 存储数据捞起（热加载）与脏数据落地的设计、流程与规则 |

## 🎯 核心概念

### 响应式编程

homo-core 基于 Reactor 框架，提供全响应式编程体验：

```java
// 链式调用
public Homo<UserInfo> getUserInfo(String userId) {
    return userRepository.findById(userId)
        .nextDo(user -> {
            // 处理用户数据
            return processUserData(user);
        })
        .nextDo(processedUser -> {
            // 缓存用户数据
            return cacheService.set("user:" + userId, processedUser);
        })
        .onErrorContinue(throwable -> {
            // 错误处理
            log.error("获取用户信息失败", throwable);
            return Homo.error(throwable);
        });
}
```

### 有状态实体

支持基于实体ID的有状态服务调用：

```java
// 定义实体
@EntityType(type = "user-entity")
@StorageTime(10000)  // 10秒落地
@CacheTime(20000)    // 20秒缓存
public interface UserEntityFacade {
    Homo<UserInfo> getUserInfo(GetUserInfoReq request);
    Homo<Boolean> updateLevel(UpdateLevelReq request);
}

// 调用实体
UserEntityFacade userEntity = entityProxyFactory.getEntityProxy(
    UserEntityFacade.class, userId);
Homo<UserInfo> userInfo = userEntity.getUserInfo(request);
```

### 智能存储

多级缓存 + 自动落地：

```java
// 自动缓存和落地
public Homo<Boolean> saveUserData(UserData userData) {
    return objStorage.save(userData)  // 自动写入 Redis 缓存
        .nextDo(result -> {
            // 数据会自动落地到 MySQL
            return Homo.result(result);
        });
}
```

## 🛠️ 开发工具

### 一键部署

```xml
<plugin>
    <groupId>com.homo</groupId>
    <artifactId>homo-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>homoBuild</goal>
                <goal>homoDeploy</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

```bash
# 构建和部署
mvn homo:homoBuild
mvn homo:homoDeploy
```

### 一键导表

```xml
<plugin>
    <groupId>com.homo</groupId>
    <artifactId>homo-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>homoTurnTable</id>
            <goals>
                <goal>homoTurnTable</goal>
            </goals>
            <configuration>
                <dataPath>E:\config\excel</dataPath>
                <javaPath>E:\src\main\java\com\game\data</javaPath>
                <packageName>com.game.data</packageName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## 📊 性能特性

- **高并发**: 基于响应式编程，支持数万并发连接
- **低延迟**: 多级缓存架构，毫秒级响应时间
- **高可用**: 支持服务发现、负载均衡、故障转移
- **可扩展**: 微服务架构，支持水平扩展

## 🎮 游戏场景

homo-core 专为游戏开发场景设计，特别适用于：

- **MMORPG**: 大型多人在线角色扮演游戏
- **MOBA**: 多人在线战术竞技游戏
- **卡牌游戏**: 回合制卡牌对战游戏
- **策略游戏**: 实时策略游戏
- **社交游戏**: 社交互动类游戏

## 🔧 配置示例

### 基础配置

```properties
# 服务配置
server.info.appId=game-server
server.info.regionId=region-1
server.info.serverName=user-service

# Redis 配置
homo.redis.url=redis://localhost:6379
homo.redis.maxTotal=100

# MySQL 配置
homo.datasource.url=jdbc:mysql://localhost:3306/game_db
homo.datasource.username=root
homo.datasource.password=password

# 链路追踪
homo.zipkin.client.trace.open=true
homo.zipkin.server.addr=localhost
homo.zipkin.server.port=9411
```

### K8S 部署

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: game-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: game-server
  template:
    metadata:
      labels:
        app: game-server
    spec:
      containers:
      - name: game-server
        image: game-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
```

## 🤝 贡献

我们欢迎社区贡献！请查看 [贡献指南](CONTRIBUTING.md) 了解如何参与项目开发。

### 开发流程

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

感谢以下开源项目的支持：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Reactor](https://projectreactor.io/)
- [Redis](https://redis.io/)
- [MySQL](https://www.mysql.com/)
- [Kafka](https://kafka.apache.org/)
- [Zipkin](https://zipkin.io/)

## 📞 社区

- **GitHub**: [https://github.com/your-org/homo-core](https://github.com/your-org/homo-core)
- **文档**: [https://homo-core.dev](https://homo-core.dev)
- **问题反馈**: [GitHub Issues](https://github.com/your-org/homo-core/issues)
- **讨论**: [GitHub Discussions](https://github.com/your-org/homo-core/discussions)

---

<div align="center">

**如果这个项目对你有帮助，请给我们一个 ⭐️**

Made with ❤️ by the homo-core team

</div>