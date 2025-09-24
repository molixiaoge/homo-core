# RPC 模块使用指南

## 概述

homo-core RPC 模块提供了强大的远程过程调用能力，支持 HTTP 和 gRPC 两种协议，具备负载均衡、有状态调用、服务发现等特性。特别适用于微服务架构和分布式游戏服务器场景。

## 功能特性

- 🌐 **多协议支持**: 支持 HTTP 和 gRPC 两种通信协议
- ⚖️ **负载均衡**: 自动负载均衡，支持多种负载均衡策略
- 🔄 **有状态调用**: 支持基于用户ID的有状态服务调用
- 🔍 **服务发现**: 自动服务发现和健康检查
- ⚡ **高性能**: 基于响应式编程，支持高并发场景
- 🛡️ **容错机制**: 支持重试、熔断、超时等容错机制

## 环境要求

- **JDK**: 8 或更高版本
- **Maven**: 3.6.0 或更高版本
- **Apollo 配置中心**: 用于配置管理
- **K8S 集群**: 用于服务部署（可选）

## 快速开始

### 1. 添加依赖

#### 服务端依赖

```xml
<dependencies>
    <!-- RPC 服务端 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-server</artifactId>
    </dependency>
    
    <!-- HTTP RPC 驱动 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-http</artifactId>
    </dependency>
    
    <!-- gRPC RPC 驱动 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-grpc</artifactId>
    </dependency>
</dependencies>
```

#### 客户端依赖

```xml
<dependencies>
    <!-- RPC 客户端 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-client</artifactId>
    </dependency>
    
    <!-- HTTP RPC 驱动 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-http</artifactId>
    </dependency>
    
    <!-- gRPC RPC 驱动 -->
    <dependency>
        <groupId>com.homo</groupId>
        <artifactId>homo-core-rpc-grpc</artifactId>
    </dependency>
</dependencies>
```

### 2. 定义服务接口

```java
// 用户服务接口
@ServiceExport(
    tagName = "user-service:8080",
    isMainServer = true,
    isStateful = false,
    driverType = RpcType.http
)
@RpcHandler
public interface UserServiceFacade {
    
    /**
     * 获取用户信息
     */
    Homo<GetUserInfoResp> getUserInfo(GetUserInfoReq request);
    
    /**
     * 创建用户
     */
    Homo<CreateUserResp> createUser(CreateUserReq request);
    
    /**
     * 更新用户信息
     */
    Homo<UpdateUserResp> updateUser(UpdateUserReq request);
    
    /**
     * 删除用户
     */
    Homo<DeleteUserResp> deleteUser(DeleteUserReq request);
}
```

### 3. 实现服务端

```java
// HTTP 服务实现
@Component
public class UserHttpService extends BaseService implements UserServiceFacade {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public Homo<GetUserInfoResp> getUserInfo(GetUserInfoReq request) {
        log.info("获取用户信息: userId={}", request.getUserId());
        
        return userRepository.findById(request.getUserId())
            .nextDo(user -> {
                if (user != null) {
                    GetUserInfoResp resp = GetUserInfoResp.newBuilder()
                        .setUserId(user.getUserId())
                        .setNickName(user.getNickName())
                        .setLevel(user.getLevel())
                        .setExp(user.getExp())
                        .setCreateTime(user.getCreateTime())
                        .build();
                    return Homo.result(resp);
                } else {
                    GetUserInfoResp resp = GetUserInfoResp.newBuilder()
                        .setErrorCode(404)
                        .setErrorMsg("用户不存在")
                        .build();
                    return Homo.result(resp);
                }
            })
            .onErrorContinue(throwable -> {
                log.error("获取用户信息失败: userId={}", request.getUserId(), throwable);
                GetUserInfoResp resp = GetUserInfoResp.newBuilder()
                    .setErrorCode(500)
                    .setErrorMsg("服务器内部错误")
                    .build();
                return Homo.result(resp);
            });
    }
    
    @Override
    public Homo<CreateUserResp> createUser(CreateUserReq request) {
        log.info("创建用户: nickName={}", request.getNickName());
        
        User user = User.builder()
            .userId(UUID.randomUUID().toString())
            .nickName(request.getNickName())
            .level(1)
            .exp(0L)
            .createTime(System.currentTimeMillis())
            .build();
        
        return userRepository.save(user)
            .nextDo(savedUser -> {
                CreateUserResp resp = CreateUserResp.newBuilder()
                    .setUserId(savedUser.getUserId())
                    .setNickName(savedUser.getNickName())
                    .setLevel(savedUser.getLevel())
                    .build();
                return Homo.result(resp);
            });
    }
    
    @Override
    public Homo<UpdateUserResp> updateUser(UpdateUserReq request) {
        log.info("更新用户信息: userId={}", request.getUserId());
        
        return userRepository.findById(request.getUserId())
            .nextDo(user -> {
                if (user == null) {
                    UpdateUserResp resp = UpdateUserResp.newBuilder()
                        .setErrorCode(404)
                        .setErrorMsg("用户不存在")
                        .build();
                    return Homo.result(resp);
                }
                
                // 更新用户信息
                if (!request.getNickName().isEmpty()) {
                    user.setNickName(request.getNickName());
                }
                if (request.getLevel() > 0) {
                    user.setLevel(request.getLevel());
                }
                user.setUpdateTime(System.currentTimeMillis());
                
                return userRepository.save(user)
                    .nextDo(updatedUser -> {
                        UpdateUserResp resp = UpdateUserResp.newBuilder()
                            .setUserId(updatedUser.getUserId())
                            .setNickName(updatedUser.getNickName())
                            .setLevel(updatedUser.getLevel())
                            .build();
                        return Homo.result(resp);
                    });
            });
    }
    
    @Override
    public Homo<DeleteUserResp> deleteUser(DeleteUserReq request) {
        log.info("删除用户: userId={}", request.getUserId());
        
        return userRepository.deleteById(request.getUserId())
            .nextDo(result -> {
                DeleteUserResp resp = DeleteUserResp.newBuilder()
                    .setSuccess(result)
                    .setMessage(result ? "删除成功" : "删除失败")
                    .build();
                return Homo.result(resp);
            });
    }
}
```

### 4. 实现客户端

```java
// 客户端服务
@Service
public class UserClientService extends BaseService {
    
    @Autowired(required = false)
    private UserServiceFacade userService;
    
    // 获取用户信息
    public Homo<UserInfo> getUserInfo(String userId) {
        GetUserInfoReq request = GetUserInfoReq.newBuilder()
            .setUserId(userId)
            .build();
        
        return userService.getUserInfo(request)
            .nextDo(resp -> {
                if (resp.getErrorCode() == 0) {
                    UserInfo userInfo = UserInfo.newBuilder()
                        .setUserId(resp.getUserId())
                        .setNickName(resp.getNickName())
                        .setLevel(resp.getLevel())
                        .setExp(resp.getExp())
                        .build();
                    return Homo.result(userInfo);
                } else {
                    throw new RuntimeException("获取用户信息失败: " + resp.getErrorMsg());
                }
            });
    }
    
    // 创建用户
    public Homo<String> createUser(String nickName) {
        CreateUserReq request = CreateUserReq.newBuilder()
            .setNickName(nickName)
            .build();
        
        return userService.createUser(request)
            .nextDo(resp -> {
                log.info("创建用户成功: userId={}, nickName={}", resp.getUserId(), resp.getNickName());
                return Homo.result(resp.getUserId());
            });
    }
    
    // 更新用户信息
    public Homo<Boolean> updateUser(String userId, String nickName, int level) {
        UpdateUserReq request = UpdateUserReq.newBuilder()
            .setUserId(userId)
            .setNickName(nickName)
            .setLevel(level)
            .build();
        
        return userService.updateUser(request)
            .nextDo(resp -> {
                if (resp.getErrorCode() == 0) {
                    log.info("更新用户成功: userId={}", userId);
                    return Homo.result(true);
                } else {
                    log.error("更新用户失败: userId={}, error={}", userId, resp.getErrorMsg());
                    return Homo.result(false);
                }
            });
    }
}
```

## 协议支持

### HTTP RPC

#### 服务端配置

```java
@ServiceExport(
    tagName = "http-service:8080",
    isMainServer = true,
    isStateful = false,
    driverType = RpcType.http
)
@RpcHandler
public interface HttpServiceFacade {
    
    // 支持的方法签名
    Homo<JSONObject> jsonGet(JSONObject header);
    Homo<JSONObject> jsonPost(JSONObject header, JSONObject body);
    Homo<String> stringPost(JSONObject header, String value);
    Homo<CustomResponse> customPost(HttpHeadInfo header, CustomRequest request);
}
```

#### 客户端调用

```java
@Service
public class HttpClientService {
    
    @Autowired(required = false)
    private HttpServiceFacade httpService;
    
    public Homo<String> callHttpService() {
        JSONObject header = new JSONObject();
        header.put("userId", "12345");
        header.put("version", "1.0");
        
        JSONObject body = new JSONObject();
        body.put("action", "getUserInfo");
        body.put("data", "test");
        
        return httpService.jsonPost(header, body)
            .nextDo(response -> {
                String result = response.getString("result");
                return Homo.result(result);
            });
    }
}
```

### gRPC RPC

#### 服务端配置

```java
@ServiceExport(
    tagName = "grpc-service:9090",
    isMainServer = true,
    isStateful = true,
    driverType = RpcType.grpc
)
@RpcHandler
public interface GrpcServiceFacade {
    
    // gRPC 方法签名
    Homo<String> valueCall(Integer podId, ParameterMsg parameterMsg, String param);
    Homo<Integer> objCall(Integer podId, ParameterMsg parameterMsg, ParamVO paramVO);
    Homo<TestServerResponse> pbCall(Integer podId, ParameterMsg parameterMsg, TestServerRequest request);
    Homo<Tuple2<String, Integer>> tuple2ReturnCall(Integer podId, ParameterMsg parameterMsg);
}
```

#### 客户端调用

```java
@Service
public class GrpcClientService {
    
    @Autowired(required = false)
    private GrpcServiceFacade grpcService;
    
    public Homo<String> callGrpcService(String param) {
        ParameterMsg parameterMsg = ParameterMsg.newBuilder()
            .setUserId("12345")
            .setChannelId("channel1")
            .setVersion("1.0")
            .build();
        
        return grpcService.valueCall(0, parameterMsg, param)
            .nextDo(result -> {
                log.info("gRPC调用结果: {}", result);
                return Homo.result(result);
            });
    }
}
```

## 有状态服务

### 1. 有状态服务配置

```java
@ServiceExport(
    tagName = "stateful-service:8080",
    isMainServer = true,
    isStateful = true,  // 启用有状态服务
    driverType = RpcType.grpc
)
@RpcHandler
public interface StatefulServiceFacade {
    
    // 有状态方法：基于用户ID路由到特定实例
    Homo<UserStateResp> getUserState(Integer podId, ParameterMsg parameterMsg, String userId);
    Homo<UpdateStateResp> updateUserState(Integer podId, ParameterMsg parameterMsg, UserStateRequest request);
}
```

### 2. 有状态服务实现

```java
@Component
public class StatefulService extends BaseService implements StatefulServiceFacade {
    
    // 用户状态存储
    private final Map<String, UserState> userStates = new ConcurrentHashMap<>();
    
    @Override
    public Homo<UserStateResp> getUserState(Integer podId, ParameterMsg parameterMsg, String userId) {
        log.info("获取用户状态: podId={}, userId={}", podId, userId);
        
        UserState userState = userStates.get(userId);
        if (userState != null) {
            UserStateResp resp = UserStateResp.newBuilder()
                .setUserId(userId)
                .setLevel(userState.getLevel())
                .setExp(userState.getExp())
                .setSceneId(userState.getSceneId())
                .build();
            return Homo.result(resp);
        } else {
            UserStateResp resp = UserStateResp.newBuilder()
                .setErrorCode(404)
                .setErrorMsg("用户状态不存在")
                .build();
            return Homo.result(resp);
        }
    }
    
    @Override
    public Homo<UpdateStateResp> updateUserState(Integer podId, ParameterMsg parameterMsg, UserStateRequest request) {
        log.info("更新用户状态: podId={}, userId={}", podId, request.getUserId());
        
        UserState userState = userStates.computeIfAbsent(request.getUserId(), 
            k -> new UserState(request.getUserId()));
        
        userState.setLevel(request.getLevel());
        userState.setExp(request.getExp());
        userState.setSceneId(request.getSceneId());
        userState.setUpdateTime(System.currentTimeMillis());
        
        UpdateStateResp resp = UpdateStateResp.newBuilder()
            .setUserId(request.getUserId())
            .setSuccess(true)
            .setMessage("状态更新成功")
            .build();
        
        return Homo.result(resp);
    }
}
```

### 3. 有状态服务调用

```java
@Service
public class StatefulClientService {
    
    @Autowired(required = false)
    private StatefulServiceFacade statefulService;
    
    public Homo<UserState> getUserState(String userId) {
        ParameterMsg parameterMsg = ParameterMsg.newBuilder()
            .setUserId(userId)
            .setChannelId("channel1")
            .build();
        
        return statefulService.getUserState(0, parameterMsg, userId)
            .nextDo(resp -> {
                if (resp.getErrorCode() == 0) {
                    UserState userState = UserState.builder()
                        .userId(resp.getUserId())
                        .level(resp.getLevel())
                        .exp(resp.getExp())
                        .sceneId(resp.getSceneId())
                        .build();
                    return Homo.result(userState);
                } else {
                    throw new RuntimeException("获取用户状态失败: " + resp.getErrorMsg());
                }
            });
    }
}
```

## 负载均衡

### 1. 负载均衡配置

```properties
# 服务状态配置
homo.service.state.local.cache.duration.second=600
homo.service.state.cache.duration.second=540
homo.service.state.cache.delay.remove.second=60
homo.service.state.expire.seconds=60
homo.service.state.update.seconds=30

# 负载均衡因子 (0~1)
homo.service.state.cpu.factor=0.5

# 服务良好状态配置
homo.service.state.range.user-service=1000
homo.service.state.range.order-service=500
homo.service.state.range.default=500
```

### 2. 自定义负载均衡策略

```java
@Component
public class CustomLoadBalancer {
    
    @Autowired
    private ServiceStateMgr serviceStateMgr;
    
    public String selectService(String serviceName, String userId) {
        List<ServiceInfo> services = serviceStateMgr.getAvailableServices(serviceName);
        
        if (services.isEmpty()) {
            throw new RuntimeException("没有可用的服务实例");
        }
        
        // 基于用户ID的哈希负载均衡
        int index = Math.abs(userId.hashCode()) % services.size();
        ServiceInfo selectedService = services.get(index);
        
        log.info("选择服务实例: serviceName={}, userId={}, selectedPod={}", 
                serviceName, userId, selectedService.getServerName());
        
        return selectedService.getServerName();
    }
}
```

## 配置详解

### 1. 服务端配置

```java
@Configuration
public class RpcServerConfig {
    
    // HTTP 服务端配置
    @Bean
    public RpcHttpServerProperties httpServerProperties() {
        RpcHttpServerProperties properties = new RpcHttpServerProperties();
        properties.setBytesLimit(614400);  // 600KB
        return properties;
    }
    
    // gRPC 服务端配置
    @Bean
    public RpcGrpcServerProperties grpcServerProperties() {
        RpcGrpcServerProperties properties = new RpcGrpcServerProperties();
        properties.setCorePoolSize(3);
        properties.setKeepLive(0);
        properties.setBoosThreadSize(1);
        properties.setWorkerThreadSize(2);
        properties.setMaxInboundMessageSize(5242880);  // 5MB
        properties.setPermitKeepAliveTime(5000);
        return properties;
    }
}
```

### 2. 客户端配置

```java
@Configuration
public class RpcClientConfig {
    
    // gRPC 客户端配置
    @Bean
    public RpcGrpcClientProperties grpcClientProperties() {
        RpcGrpcClientProperties properties = new RpcGrpcClientProperties();
        properties.setDirector(true);
        properties.setCheckDelaySecond(0);
        properties.setCheckPeriodSecond(5);
        properties.setWorkerThread(2);
        properties.setMessageMaxSize(5242880);  // 5MB
        properties.setChannelKeepLiveMillsSecond(5000);
        properties.setChannelKeepLiveTimeoutMillsSecond(5000);
        return properties;
    }
}
```

## 最佳实践

### 1. 服务设计

```java
// ✅ 好的做法：清晰的服务接口
@ServiceExport(tagName = "user-service:8080", isMainServer = true, isStateful = false)
@RpcHandler
public interface UserServiceFacade {
    Homo<GetUserInfoResp> getUserInfo(GetUserInfoReq request);
    Homo<CreateUserResp> createUser(CreateUserReq request);
}

// ❌ 避免：模糊的服务接口
@ServiceExport(tagName = "service:8080")
@RpcHandler
public interface BadServiceFacade {
    Homo<Object> doSomething(Object request);
}
```

### 2. 错误处理

```java
// ✅ 好的做法：完善的错误处理
@Override
public Homo<GetUserInfoResp> getUserInfo(GetUserInfoReq request) {
    return userRepository.findById(request.getUserId())
        .nextDo(user -> {
            if (user != null) {
                GetUserInfoResp resp = buildSuccessResponse(user);
                return Homo.result(resp);
            } else {
                GetUserInfoResp resp = GetUserInfoResp.newBuilder()
                    .setErrorCode(404)
                    .setErrorMsg("用户不存在")
                    .build();
                return Homo.result(resp);
            }
        })
        .onErrorContinue(throwable -> {
            log.error("获取用户信息失败: userId={}", request.getUserId(), throwable);
            GetUserInfoResp resp = GetUserInfoResp.newBuilder()
                .setErrorCode(500)
                .setErrorMsg("服务器内部错误")
                .build();
            return Homo.result(resp);
        });
}
```

### 3. 性能优化

```java
// ✅ 使用连接池
@Configuration
public class RpcConnectionConfig {
    
    @Bean
    public ConnectionPoolConfig connectionPoolConfig() {
        ConnectionPoolConfig config = new ConnectionPoolConfig();
        config.setMaxConnections(100);
        config.setMaxIdleTime(30000);
        config.setMaxWaitTime(5000);
        return config;
    }
}

// ✅ 使用异步调用
public Homo<String> asyncCallService(String param) {
    return serviceFacade.someMethod(param)
        .subscribeOn(Schedulers.parallel())
        .nextDo(result -> {
            // 处理结果
            return Homo.result(result);
        });
}
```

### 4. 监控和日志

```java
// ✅ 添加调用监控
@Component
public class RpcMonitor {
    
    private final MeterRegistry meterRegistry;
    
    public RpcMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordRpcCall(String serviceName, String methodName, boolean success, Duration duration) {
        Timer.builder("rpc.call.duration")
            .tag("service", serviceName)
            .tag("method", methodName)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(duration);
    }
}
```

## 故障排除

### 常见问题

1. **服务调用失败**
   - 检查服务是否正常启动
   - 确认网络连接和端口配置
   - 查看服务发现状态

2. **负载均衡问题**
   - 检查服务状态配置
   - 确认负载均衡因子设置
   - 查看服务健康状态

3. **有状态调用失败**
   - 检查用户ID是否正确传递
   - 确认目标服务实例状态
   - 查看路由配置

### 调试技巧

```java
// 启用详细日志
logging.level.com.homo.core.rpc=DEBUG
logging.level.io.grpc=DEBUG

// 添加调用追踪
@Component
public class RpcTracer {
    
    public void traceRpcCall(String serviceName, String methodName, Object request) {
        log.debug("RPC调用: service={}, method={}, request={}", serviceName, methodName, request);
    }
}
```

## 相关链接

- [homo-core 框架文档](../README.md)
- [gRPC 官方文档](https://grpc.io/docs/)
- [Spring WebFlux 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [响应式编程指南](https://projectreactor.io/docs/core/release/reference/)
