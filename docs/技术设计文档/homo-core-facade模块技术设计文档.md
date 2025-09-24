# homo-core-facade 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 门面抽象：以最小稳定接口抽象复杂子系统，实现“高内聚、低耦合”。
- 协议无关：先定义调用模型（Service/RpcAgent/Content），再由驱动适配协议。
- 响应式统一：接口返回统一 `Homo<T>`，贯穿全链路观测与错误续流。

### 0.2 架构图（ASCII）
```text
Caller → RpcAgentClient → RpcClient(grpc/http) → RpcServer → Service(BaseService)
                                      │                  │
                                      └─ RpcContent ◄───┘
```

### 0.3 典型流程（ASCII）
```text
export Facade(annotated) → 启动扫描 → RpcHandlerInfo 缓存方法
call(fun) → CallDispatcher 匹配方法 → 反射/缓存执行 → Homo<T> 返回
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-facade` 是整个 homo-core 游戏框架的门面模块，定义了框架的核心抽象接口和数据结构。它作为框架的 API 层，为上层业务逻辑提供统一的编程接口。

### 1.2 设计目标
- 提供统一的抽象接口，屏蔽底层实现细节
- 定义框架的核心数据结构和协议
- 支持多种通信协议（HTTP、gRPC、TCP）
- 提供能力系统（Ability System）的抽象
- 支持有状态和无状态服务调用

## 2. 核心设计理念

### 2.1 门面模式（Facade Pattern）
采用门面模式统一封装复杂的子系统，为客户端提供简化的接口。通过定义清晰的抽象层，实现业务逻辑与底层实现的解耦。

### 2.2 能力系统（Ability System）
基于组合模式设计的能力系统，允许实体（Entity）动态组合不同的能力（Ability），实现灵活的功能扩展。

### 2.3 响应式编程
全框架采用响应式编程模型，基于 Reactor 框架实现异步非阻塞的编程范式。

## 3. 核心接口设计

### 3.1 服务接口（Service）

```java
public interface Service extends Module {
    String getTagName();           // 服务标识符
    String getHostName();          // 服务域名
    int getPort();                 // 服务端口
    RpcType getType();             // 服务类型
    boolean isStateful();          // 是否是有状态服务
    Homo callFun(String srcService, String funName, RpcContent param);
}
```

**设计特点：**
- 支持有状态和无状态服务
- 统一的远程调用接口
- 服务发现和注册能力

### 3.2 能力系统接口

#### 3.2.1 能力接口（Ability）
```java
public interface Ability {
    void attach(AbilityEntity abilityEntity);      // 关联实体
    void unAttach(AbilityEntity abilityEntity);    // 取消关联
    AbilityEntity getOwner();                      // 获取关联的实体
}
```

#### 3.2.2 能力实体接口（AbilityEntity）
```java
public interface AbilityEntity {
    String getType();                              // 实体类型
    String getId();                                // 实体ID
    <T extends Ability> T getAbility(String abilityName);
    void setAbility(String abilityName, Ability ability);
}
```

**设计特点：**
- 支持动态能力组合
- 能力与实体松耦合
- 支持能力的热插拔

### 3.3 RPC 通信接口

#### 3.3.1 RPC 代理接口（RpcAgent）
```java
public interface RpcAgent<T extends RpcContent<P, R>, P, R> {
    Homo<R> rpcCall(String funName, T content);
}
```

#### 3.3.2 RPC 客户端接口（RpcClient）
```java
public interface RpcClient {
    Homo<byte[][]> call(String funName, byte[][] data);
    void close();
}
```

**设计特点：**
- 支持多种 RPC 协议
- 统一的调用接口
- 异步非阻塞调用

### 3.4 网关接口

#### 3.4.1 网关驱动接口（GateDriver）
```java
public interface GateDriver {
    void startGate(GateServer gateServer);
    void stopGate();
    void sendToClient(GateClient gateClient, byte[] data);
}
```

#### 3.4.2 网关客户端接口（GateClient）
```java
public interface GateClient {
    String getId();
    void send(byte[] data);
    void close();
}
```

**设计特点：**
- 支持多种网关协议（TCP、HTTP）
- 统一的客户端管理
- 支持消息路由和转发

## 4. 核心数据结构

### 4.1 RPC 内容（RpcContent）
```java
public abstract class RpcContent<P, R> {
    protected P param;
    protected R result;
    // 序列化和反序列化方法
}
```

### 4.2 实体请求（EntityRequest）
```java
public class EntityRequest {
    private String type;           // 实体类型
    private String id;             // 实体ID
    private String method;         // 调用方法
    private byte[][] data;         // 请求数据
}
```

### 4.3 流程控制（Tread）
```java
public class Tread<T> {
    // 资源操作流程控制
    // 支持原子性操作
    // 支持自定义校验和回调
}
```

## 5. 设计模式应用

### 5.1 策略模式
- RPC 协议选择（HTTP、gRPC）
- 序列化策略选择
- 负载均衡策略

### 5.2 工厂模式
- RPC 客户端工厂
- 网关驱动工厂
- 能力系统工厂

### 5.3 观察者模式
- 服务状态变化通知
- 能力系统事件通知
- 网关连接状态通知

### 5.4 装饰器模式
- RPC 调用拦截器
- 网关消息处理器
- 能力系统增强器

## 6. 扩展性设计

### 6.1 插件化架构
- 支持自定义 RPC 协议
- 支持自定义网关驱动
- 支持自定义能力实现

### 6.2 配置驱动
- 通过注解配置服务导出
- 通过配置文件控制行为
- 支持运行时配置更新

### 6.3 版本兼容
- 接口版本管理
- 向后兼容性保证
- 平滑升级支持

## 7. 性能优化

### 7.1 对象池化
- RPC 客户端连接池
- 消息对象池
- 线程池管理

### 7.2 缓存策略
- 服务发现缓存
- 能力系统缓存
- 配置信息缓存

### 7.3 异步处理
- 全异步 RPC 调用
- 非阻塞网关处理
- 响应式数据流

## 8. 错误处理

### 8.1 异常分类
- 网络异常
- 业务异常
- 系统异常

### 8.2 重试机制
- 指数退避重试
- 熔断器模式
- 降级处理

### 8.3 监控告警
- 调用链路追踪
- 性能指标监控
- 异常告警机制

## 9. 使用示例

### 9.1 定义服务接口
```java
@ServiceExport(tagName = "userService:8080", isStateful = true)
public interface UserService {
    Homo<UserInfo> getUserInfo(String userId);
    Homo<Boolean> updateUserInfo(UserInfo userInfo);
}
```

### 9.2 实现能力实体
```java
@EntityType(type = "user")
public class UserEntity extends BaseAbilityEntity<UserEntity> {
    // 实体实现
}
```

### 9.3 使用 RPC 调用
```java
@Autowired
private RpcAgent agent;

public void callRemoteService() {
    agent.rpcCall("getUserInfo", request)
         .nextDo(result -> {
             // 处理结果
         })
         .start();
}
```

## 10. 总结

`homo-core-facade` 模块作为框架的门面，通过清晰的接口设计和抽象，为上层业务提供了统一、简洁的编程模型。其能力系统的设计使得框架具有很好的扩展性，而响应式编程模型则保证了高性能和可伸缩性。该模块的成功设计为整个 homo-core 框架奠定了坚实的基础。

---

## 11. 设计思想（补充）
- Facade 仅定义“做什么”，不关心“怎么做”，以最小接口保障可替换性与多驱动实现。
- 强类型与响应式：所有远程/异步路径统一 `Homo<T>`，接口参数/返回可序列化。
- 可观测：接口层面规范 MDC/Trace，促进端到端可观测。

## 12. 典型调用流程（ASCII）
```text
Client → RpcAgentClient → RpcClient(grpc/http) → Service(BaseService.callFun)
       → CallDispatcher → 反射/方法缓存 → 实现类 → 返回 Homo<T>
```

## 13. 关键关系图（ASCII）
```text
RpcAgentClient --calls--> RpcClient --transport--> RpcServer --dispatch--> Service
Service --use--> CallDispatcher --cache--> RpcHandlerInfoForServer(methods)
```

## 14. 规则清单
- 接口命名：以业务语义 + `Facade` 结尾。
- 注解：`@ServiceExport(tagName, driverType, isStateful, isMainServer)` 必填。
- 返回值：统一 `Homo<T>`；禁止阻塞返回。
- 版本化：新增方法保持向后兼容，避免破坏旧客户端。

## 15. 错误与幂等
- 语义化错误码（枚举/常量），统一组装到返回对象。
- 幂等：读方法幂等，写方法通过业务幂等键（如 requestId）。
