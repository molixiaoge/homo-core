# homo-core-utils 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- Homo 语义化 Reactor：内置线程切换、错误续流、队列化与追踪能力。
- 模块生命周期：统一初始化顺序与关闭钩子，支撑系统有序启动/停机。
- 并发基石：IdCallQueue/IdLocker 提供有序串行与细粒度互斥。

### 0.2 架构图（ASCII）
```text
ModuleMgr → Module(Service/Driver/Support) 生命周期
Homo<T> → Mono 封装（switch/nextDo/errorContinue/queue）
CallQueue/IdCallQueue → 有序执行
```

### 0.3 典型流程（ASCII）
```text
Homo.warp → capture MDC → nextDo(...).switchToCurrentThread() → 执行 → onErrorContinue
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-utils` 是 homo-core 框架的工具模块，提供了框架运行所需的基础工具类、响应式编程支持、模块管理、并发控制等核心功能。

### 1.2 设计目标
- 提供统一的工具类和基础功能
- 实现响应式编程模型（Homo）
- 提供模块生命周期管理
- 支持高并发场景下的线程安全操作
- 提供反射、序列化、配置管理等工具

## 2. 核心设计理念

### 2.1 响应式编程模型
基于 Reactor 框架封装的自定义响应式编程模型 `Homo<T>`，提供链式调用和异步处理能力。

### 2.2 模块化架构
通过 `Module` 接口实现模块的标准化管理，支持模块的初始化顺序控制和生命周期管理。

### 2.3 工具类设计原则
- 无状态设计，确保线程安全
- 提供静态方法，便于调用
- 统一的异常处理机制
- 高性能实现

## 3. 核心组件设计

### 3.1 响应式编程支持（Homo）

#### 3.1.1 Homo 类设计
```java
public class Homo<T> extends Mono<T> {
    private final Mono<T> mono;
    
    // 构造函数
    public Homo(Mono<T> mono);
    
    // 线程切换
    public Homo<T> switchToCurrentThread();
    public Homo<T> switchThread(CallQueue callQueue, Span span);
    
    // 队列处理
    public static <T> Homo<T> queue(IdCallQueue idCallQueue, 
                                   Callable<Homo<T>> callable, 
                                   Runnable errCb);
}
```

**设计特点：**
- 基于 Reactor 的 Mono 扩展
- 支持线程切换和队列处理
- 集成链路追踪（Zipkin）
- 支持错误处理和重试机制

#### 3.1.2 响应式操作符
```java
// 链式操作
public Homo<T> nextDo(Function<T, Homo<R>> mapper);
public Homo<T> errorContinue(Function<Throwable, Homo<T>> errorMapper);
public Homo<T> switchToCurrentThread();
```

### 3.2 模块管理系统

#### 3.2.1 模块接口（Module）
```java
public interface Module {
    default Integer getOrder() { return Integer.MAX_VALUE; }
    default void moduleInit() {}
    default void afterAllModuleInit() {}
    default void beforeClose() {}
}
```

**模块类型层次：**
- `RootModule` - 根模块
- `SupportModule` - 支持模块  
- `DriverModule` - 驱动模块
- `ServiceModule` - 服务模块
- `Module` - 普通模块

#### 3.2.2 模块管理器（ModuleMgr）
```java
public interface ModuleMgr {
    void registerModule(Module module);
    void initAllModules();
    void afterAllModuleInit();
    void closeAllModules();
}
```

### 3.3 并发控制

#### 3.3.1 调用队列（CallQueue）
```java
public class CallQueue {
    // 单线程队列处理
    // 支持任务优先级
    // 支持队列大小限制
    // 支持丢弃策略
}
```

#### 3.3.2 ID 调用队列（IdCallQueue）
```java
public class IdCallQueue extends CallQueue {
    // 基于 ID 的队列路由
    // 保证同一 ID 的任务串行执行
    // 支持负载均衡
}
```

#### 3.3.3 ID 锁（IdLocker）
```java
public class IdLocker {
    // 基于 ID 的细粒度锁
    // 避免死锁
    // 支持超时机制
}
```

### 3.4 反射工具

#### 3.4.1 类型工具（HomoTypeUtil）
```java
public class HomoTypeUtil {
    // 获取泛型类型信息
    // 类型转换工具
    // 方法参数类型解析
}
```

#### 3.4.2 注解工具（HomoAnnotationUtil）
```java
public class HomoAnnotationUtil {
    // 注解查找和解析
    // 支持继承的注解查找
    // 注解信息缓存
}
```

### 3.5 序列化支持

#### 3.5.1 序列化处理器（HomoSerializationProcessor）
```java
public interface HomoSerializationProcessor {
    byte[] writeByte(Object obj);
    <T> T readValue(byte[] bytes, Class<T> clazz);
}
```

**支持的序列化方式：**
- FST 序列化
- Protobuf 序列化
- JSON 序列化
- 自定义序列化

### 3.6 配置管理

#### 3.6.1 配置驱动（ConfigDriver）
```java
public interface ConfigDriver {
    void registerNamespace(String namespace);
    String getProperty(String key, String defaultValue);
    void addListener(String key, Consumer<String> listener);
}
```

**配置来源：**
- Apollo 配置中心
- 本地配置文件
- 环境变量
- 系统属性

### 3.7 工具类集合

#### 3.7.1 单例原型（SingletonPrototype）
```java
public class SingletonPrototype {
    public static <T> T get(Class<T> clazz, Object... params);
    // 线程安全的单例管理
    // 支持参数化构造
}
```

#### 3.7.2 线程工具（ThreadUtil）
```java
public class ThreadUtil {
    // 线程池管理
    // 线程工厂
    // 线程监控
}
```

#### 3.7.3 异常处理（HomoError）
```java
public class HomoError extends RuntimeException {
    // 统一的异常类型
    // 错误码管理
    // 异常链支持
}
```

## 4. 设计模式应用

### 4.1 单例模式
- `SingletonPrototype` 提供线程安全的单例管理
- 配置管理器单例
- 线程池管理器单例

### 4.2 工厂模式
- 线程池工厂
- 序列化器工厂
- 模块工厂

### 4.3 观察者模式
- 配置变更监听
- 模块状态监听
- 异常事件监听

### 4.4 策略模式
- 序列化策略
- 队列丢弃策略
- 负载均衡策略

## 5. 性能优化

### 5.1 对象池化
- 线程池复用
- 对象缓存
- 连接池管理

### 5.2 缓存机制
- 反射信息缓存
- 配置信息缓存
- 类型信息缓存

### 5.3 异步处理
- 非阻塞 I/O
- 异步任务执行
- 响应式数据流

### 5.4 内存优化
- 对象复用
- 弱引用管理
- 垃圾回收优化

## 6. 并发安全

### 6.1 线程安全设计
- 无状态工具类
- 不可变对象
- 线程本地存储

### 6.2 锁机制
- 细粒度锁
- 读写锁
- 无锁数据结构

### 6.3 原子操作
- Atomic 类使用
- CAS 操作
- 内存屏障

## 7. 错误处理

### 7.1 异常分类
- 系统异常
- 业务异常
- 网络异常

### 7.2 异常处理策略
- 异常传播
- 异常转换
- 异常恢复

### 7.3 监控和日志
- 异常统计
- 性能监控
- 链路追踪

## 8. 扩展性设计

### 8.1 插件化支持
- 自定义序列化器
- 自定义配置源
- 自定义模块类型

### 8.2 配置驱动
- 运行时配置
- 热更新支持
- 多环境配置

### 8.3 版本兼容
- 接口版本管理
- 向后兼容
- 平滑升级

## 9. 使用示例

### 9.1 响应式编程
```java
Homo<String> result = Homo.warp(sink -> {
    // 异步操作
    asyncOperation()
        .thenAccept(sink::success)
        .exceptionally(sink::error);
})
.nextDo(data -> {
    // 数据处理
    return processData(data);
})
.switchToCurrentThread();
```

### 9.2 模块管理
```java
@Component
public class MyModule implements ServiceModule {
    @Override
    public Integer getOrder() {
        return 100; // 初始化顺序
    }
    
    @Override
    public void moduleInit() {
        // 模块初始化
    }
}
```

### 9.3 并发控制
```java
IdCallQueue queue = new IdCallQueue("myQueue", 1000);
Homo<String> result = Homo.queue(queue, () -> {
    // 串行执行的任务
    return doSomething();
}, () -> {
    // 错误处理
    log.error("Task failed");
});
```

## 10. 总结

`homo-core-utils` 模块作为框架的基础工具模块，通过提供响应式编程支持、模块管理、并发控制等核心功能，为整个框架奠定了坚实的技术基础。其设计充分考虑了性能、并发安全、扩展性等方面，为上层业务提供了稳定可靠的工具支持。

---

## 11. 设计思想（补充）
- Homo 即语义化 Mono：将常用链式操作、线程切换、错误续流、队列化封装为可读 API。
- 线程与MDC：线程切换前后需显式恢复 MDC，保证日志上下文连续。

## 12. 线程与队列（规则）
- `switchToCurrentThread()`：将后续操作切换到当前 `CallQueue` 绑定线程。
- `IdCallQueue`：同一 id 的任务串行，丢弃策略可配置（DROP_CURRENT_TASK 等）。
- 队列过载保护：超时/丢弃/降级，避免系统雪崩。

## 13. MDC 传播建议
- 在 Homo 源头（warp/queue）捕获 MDC：`MDC.getCopyOfContextMap()`
- 在 `nextDo/subscribeOn` 处恢复；完成后按需清理，防止污染。

## 14. Checklist
- [ ] 新增工具遵循无状态/线程安全
- [ ] Homo 操作符不吞异常，均提供 `onErrorContinue`
- [ ] CallQueue 指标可观测（排队时延、丢弃计数）
