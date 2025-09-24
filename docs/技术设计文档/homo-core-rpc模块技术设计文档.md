# homo-core-rpc 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 协议无关调用模型：以 Agent/Client/Server/Dispatcher 为核心抽象。
- 有状态优先：一致性哈希 + 状态缓存，减少跨实例同步。
- 统一观测：客户端/服务端拦截器注入与恢复 MDC/Trace。

### 0.2 架构图（ASCII）
```text
Caller → RpcAgentClient → RpcClient(grpc/http) ⇄ RpcServer → CallDispatcher → Impl
```

### 0.3 典型流程（ASCII）
```text
export Facade → build methodMap → call(name,content) → serialize → send
→ receive → dispatch → invoke → Homo<T> → serialize → return
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-rpc` 是 homo-core 框架的远程过程调用模块，提供了统一的 RPC 通信抽象，支持多种通信协议（HTTP、gRPC），实现了服务发现、负载均衡、有状态调用等核心功能。

### 1.2 设计目标
- 提供统一的 RPC 调用接口
- 支持多种通信协议
- 实现服务发现和注册
- 支持有状态和无状态调用
- 提供负载均衡和故障转移
- 支持链路追踪和监控

## 2. 模块架构

### 2.1 模块组成
- `homo-core-rpc-base` - RPC 基础抽象和通用功能
- `homo-core-rpc-grpc` - gRPC 协议实现
- `homo-core-rpc-http` - HTTP 协议实现
- `homo-core-rpc-client` - RPC 客户端实现
- `homo-core-rpc-server` - RPC 服务端实现

### 2.2 架构层次
```
业务层
  ↓
RPC 抽象层 (facade)
  ↓
协议实现层 (grpc/http)
  ↓
传输层 (netty/webclient)
```

## 3. 核心设计理念

### 3.1 协议抽象
通过统一的接口抽象不同的 RPC 协议，支持协议的无缝切换和扩展。

### 3.2 服务治理
- 服务注册与发现
- 健康检查
- 负载均衡
- 熔断降级

### 3.3 有状态调用
支持基于实体 ID 的有状态服务调用，确保同一实体的请求路由到同一服务实例。

## 4. 核心组件设计

### 4.1 RPC 基础抽象

#### 4.1.1 服务接口（Service）
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

#### 4.1.2 RPC 内容（RpcContent）
```java
public abstract class RpcContent<P, R> {
    protected P param;
    protected R result;
    
    // 序列化方法
    public abstract byte[] toByteArray();
    public abstract void fromByteArray(byte[] data);
}
```

#### 4.1.3 RPC 代理（RpcAgent）
```java
public interface RpcAgent<T extends RpcContent<P, R>, P, R> {
    Homo<R> rpcCall(String funName, T content);
}
```

### 4.2 服务端实现

#### 4.2.1 基础服务（BaseService）
```java
public class BaseService implements Service, IEntityService, ServiceModule {
    @Autowired(required = false)
    @Lazy
    ICallSystem callSystem;
    
    protected ServiceMgr serviceMgr;
    private CallDispatcher callDispatcher;
    private RpcHandlerInfoForServer rpcHandleInfo;
    
    @Override
    public Homo callFun(String srcService, String funName, RpcContent param) {
        return callDispatcher.callFun(this, srcService, funName, param);
    }
}
```

#### 4.2.2 调用分发器（CallDispatcher）
```java
public class CallDispatcher {
    private RpcHandlerInfoForServer rpcHandleInfo;
    
    public Homo callFun(Service service, String srcService, 
                       String funName, RpcContent param) {
        // 方法调用分发
        // 参数验证
        // 异常处理
    }
}
```

#### 4.2.3 RPC 处理器信息（RpcHandlerInfoForServer）
```java
public class RpcHandlerInfoForServer {
    private Map<String, MethodDispatchInfo> methodDispatchInfoMap;
    
    public void exportMethodInfos(Class<?> rpcClazz) {
        // 导出方法信息
        // 参数类型解析
        // 注解处理
    }
}
```

### 4.3 客户端实现

#### 4.3.1 RPC 客户端工厂（RpcClientFactory）
```java
public interface RpcClientFactory {
    RpcAgentClient newAgent(String hostname, ServiceInfo serviceInfo);
    RpcType getType();
}
```

#### 4.3.2 RPC 客户端（RpcClient）
```java
public interface RpcClient {
    Homo<byte[][]> call(String funName, byte[][] data);
    void close();
}
```

#### 4.3.3 RPC 代理客户端（RpcAgentClient）
```java
public class RpcAgentClient implements RpcAgent {
    private RpcClient client;
    private String srcServiceName;
    private String targetServiceName;
    private boolean isStateful;
    
    @Override
    public Homo<R> rpcCall(String funName, T content) {
        // 序列化请求
        // 发送 RPC 调用
        // 反序列化响应
    }
}
```

## 5. 协议实现

### 5.1 gRPC 协议实现

#### 5.1.1 gRPC 客户端（GrpcRpcClient）
```java
@Slf4j
public class GrpcRpcClient implements RpcClient {
    private final String host;
    private final Integer servicePort;
    private ManagedChannel channel;
    private final boolean isStateful;
    
    public GrpcRpcClient(String host, int port, 
                        List<ClientInterceptor> clientInterceptorList, 
                        boolean isStateful, 
                        RpcGrpcClientProperties properties) {
        // 初始化 gRPC 客户端
        // 配置连接参数
        // 设置拦截器
    }
    
    @Override
    public Homo<byte[][]> call(String funName, byte[][] data) {
        // 构建 gRPC 请求
        // 发送流式请求
        // 处理响应
    }
}
```

#### 5.1.2 gRPC 服务端（GrpcRpcServer）
```java
@Slf4j
public class GrpcRpcServer implements RpcServer {
    private Server server;
    private final int port;
    private final Service service;
    
    @Override
    public void start() {
        // 启动 gRPC 服务
        // 注册服务实现
        // 配置拦截器
    }
}
```

#### 5.1.3 gRPC 调用服务（GrpcRpcCallService）
```java
public class GrpcRpcCallService extends RpcCallServiceGrpc.RpcCallServiceImplBase {
    @Autowired
    private ServiceMgr serviceMgr;
    
    @Override
    public StreamObserver<StreamReq> call(StreamObserver<StreamResp> responseObserver) {
        // 处理 gRPC 流式调用
        // 调用本地服务
        // 返回响应
    }
}
```

### 5.2 HTTP 协议实现

#### 5.2.1 HTTP 客户端（HttpRpcClient）
```java
@Slf4j
public class HttpRpcAgentClient implements RpcAgentClient {
    private WebClient webClient;
    private String hostname;
    private int port;
    
    public HttpRpcAgentClient(String srcServiceName, String hostname, 
                             int port, WebClient webClient, 
                             boolean isStateful, boolean targetIsStateful) {
        // 初始化 HTTP 客户端
        // 配置 WebClient
    }
    
    @Override
    public Homo<R> rpcCall(String funName, T content) {
        // 构建 HTTP 请求
        // 发送请求
        // 处理响应
    }
}
```

#### 5.2.2 HTTP 服务端（HttpServer）
```java
@Slf4j
public class HttpServer implements RpcServer {
    private WebServer webServer;
    private final int port;
    private final Service service;
    
    @Override
    public void start() {
        // 启动 HTTP 服务
        // 注册路由
        // 配置中间件
    }
}
```

## 6. 服务治理

### 6.1 服务注册与发现

#### 6.1.1 服务管理器（ServiceMgr）
```java
public class ServiceMgr implements Module {
    private Map<String, Service> serviceMap;
    private ServiceStateMgr serviceStateMgr;
    
    public void registerService(Service service) {
        // 注册服务
        // 更新服务状态
    }
    
    public Service getService(String serviceName) {
        // 获取服务实例
        // 负载均衡选择
    }
}
```

#### 6.1.2 服务状态管理（ServiceStateMgr）
```java
public class ServiceStateMgr {
    private Map<String, ServiceInfo> serviceInfoMap;
    
    public void setServiceInfo(String hostName, ServiceInfo serviceInfo) {
        // 设置服务信息
        // 更新服务状态
    }
    
    public void start() {
        // 启动服务状态管理
        // 定期健康检查
    }
}
```

### 6.2 负载均衡

#### 6.2.1 负载均衡策略
- **轮询（Round Robin）**：依次选择服务实例
- **随机（Random）**：随机选择服务实例
- **最少连接（Least Connections）**：选择连接数最少的实例
- **一致性哈希（Consistent Hash）**：基于请求特征的一致性哈希

#### 6.2.2 有状态调用路由
```java
public class StatefulCallRouter {
    public Service selectService(String entityType, String entityId) {
        // 基于实体 ID 计算哈希值
        // 选择对应的服务实例
        // 确保同一实体的请求路由到同一实例
    }
}
```

### 6.3 故障处理

#### 6.3.1 熔断器模式
```java
public class CircuitBreaker {
    private CircuitState state;
    private int failureCount;
    private long lastFailureTime;
    
    public boolean allowRequest() {
        // 检查熔断器状态
        // 决定是否允许请求
    }
}
```

#### 6.3.2 重试机制
```java
public class RetryPolicy {
    private int maxRetries;
    private long retryDelay;
    private BackoffStrategy backoffStrategy;
    
    public Homo<T> executeWithRetry(Supplier<Homo<T>> operation) {
        // 执行重试逻辑
        // 指数退避
        // 异常处理
    }
}
```

## 7. 性能优化

### 7.1 连接池管理
- gRPC 连接池
- HTTP 连接池
- 连接复用
- 连接健康检查

### 7.2 序列化优化
- 高效的序列化算法
- 对象池复用
- 压缩传输
- 批量序列化

### 7.3 异步处理
- 非阻塞 I/O
- 异步调用
- 响应式编程
- 背压控制

## 8. 监控和链路追踪

### 8.1 链路追踪
- Zipkin 集成
- 请求链路记录
- 性能指标收集
- 异常追踪

### 8.2 监控指标
- 调用次数统计
- 响应时间监控
- 错误率统计
- 吞吐量监控

### 8.3 日志记录
- 请求日志
- 响应日志
- 异常日志
- 性能日志

## 9. 使用示例

### 9.1 定义服务接口
```java
@ServiceExport(tagName = "userService:8080", isStateful = true)
public interface UserService {
    Homo<UserInfo> getUserInfo(String userId);
    Homo<Boolean> updateUserInfo(UserInfo userInfo);
}
```

### 9.2 实现服务
```java
@Service
public class UserServiceImpl implements UserService {
    @Override
    public Homo<UserInfo> getUserInfo(String userId) {
        // 业务逻辑实现
        return Homo.result(new UserInfo(userId, "张三", 25));
    }
}
```

### 9.3 客户端调用
```java
@Autowired
private RpcAgent<UserServiceRequest, UserServiceRequest, UserServiceResponse> userServiceAgent;

public void callUserService() {
    UserServiceRequest request = new UserServiceRequest("user123");
    userServiceAgent.rpcCall("getUserInfo", request)
        .nextDo(response -> {
            log.info("获取用户信息: {}", response.getUserInfo());
        })
        .start();
}
```

## 10. 配置管理

### 10.1 gRPC 配置
```yaml
homo:
  rpc:
    grpc:
      client:
        maxInboundMessageSize: 4194304
        keepAliveTime: 30
        keepAliveTimeout: 5
      server:
        port: 8080
        maxInboundMessageSize: 4194304
```

### 10.2 HTTP 配置
```yaml
homo:
  rpc:
    http:
      client:
        connectTimeout: 5000
        readTimeout: 10000
      server:
        port: 8080
        contextPath: /api
```

## 11. 总结

`homo-core-rpc` 模块通过提供统一的 RPC 抽象和多种协议实现，为 homo-core 框架提供了强大的服务间通信能力。其服务治理功能确保了系统的高可用性，而有状态调用支持则满足了游戏服务器的特殊需求。该模块的成功设计为整个框架的分布式架构奠定了坚实的基础。

---

## 12. 设计思想（补充）
- 抽象先行：协议无关的调用模型（Agent/Client/Server/Dispatcher）。
- 有状态优先：以 entityId/用户维度做一致性路由，减少跨实例同步。
- 观测内建：请求全程注入 MDC/Trace，客户端拦截器/服务端拦截器统一处理。

## 13. 调用流程（ASCII）
```text
Caller → RpcAgentClient → RpcClient(encode,send) → RpcServer(receive)
      → CallDispatcher(method cache) → Impl → Homo<T> → encode → return
```

## 14. 关键关系（ASCII）
```text
RpcAgentClient → RpcClient(grpc/http) → RpcServer → Service(BaseService)
BaseService → CallDispatcher → RpcHandlerInfoForServer(method map)
```

## 15. 有状态路由规则
- `ServiceExport.isStateful=true`：
  - 客户端选择：基于 `userId/entityId` 一致性 Hash 到目标实例。
  - 失败转移：允许漂移，但需清理旧连接信息（ServiceStateMgr）。
- 无状态：按负载策略选择实例（round-robin/权重）。

## 16. Checklist
- [ ] Facade 方法统一 `Homo<T>`
- [ ] 客户端拦截器注入 MDC：traceId/requestId/userId
- [ ] 服务端拦截器恢复 MDC
- [ ] 方法缓存与反射开销可控（预热/懒加载）
