# homo-core-entity-ability 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 组合优先：以组合模式将能力解耦为可插拔组件，避免继承层级膨胀。
- 有状态一致：同一实体请求路由至同一实例，结合单线程队列与细粒度锁保证串行一致。
- 可观测默认开启：实体/能力生命周期、远程调用与存储路径均标准化注入 MDC 与 Trace。

### 0.2 架构图（ASCII）
```text
Client → CallSystem → 路由(pod/entityId) → StorageEntityMgr
                          │
                          ├─ 缓存命中：返回 BaseAbilityEntity
                          └─ 未命中：创建/加载 → attach 能力（Call/Storage/Time...）
                                         │
                                         └─ 业务方法 → 存储/远程调用
```

### 0.3 典型流程（ASCII）
```text
1) getOrCreate(entityType, entityId)
   → 缓存查找 → 持久化加载（ObjStorage）→ attach 能力
2) 调用能力方法（远程/本地）
   → IdCallQueue 串行 → 执行 → StorageAbility.save/update
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-entity-ability` 是 homo-core 框架的实体能力模块，实现了基于组合模式的能力系统，允许实体（Entity）动态组合不同的能力（Ability），实现灵活的功能扩展和模块化设计。

### 1.2 设计目标
- 实现灵活的能力组合系统
- 支持实体的动态能力管理
- 提供统一的能力接口抽象
- 支持能力的热插拔
- 实现有状态的实体管理
- 提供自动化的存储能力

## 2. 核心设计理念

### 2.1 组合模式（Composition Pattern）
通过组合模式实现能力的灵活组合，避免继承带来的类爆炸问题，提高代码的复用性和可维护性。

### 2.2 能力系统（Ability System）
- **能力（Ability）**：提供特定功能的组件
- **实体（Entity）**：拥有能力的对象
- **能力系统（Ability System）**：管理能力和实体的系统

### 2.3 有状态实体
支持有状态的实体管理，确保同一实体的请求路由到同一服务实例，保证数据一致性。

## 3. 核心组件设计

### 3.1 能力接口（Ability）

```java
public interface Ability {
    /**
     * 关联对象
     */
    void attach(AbilityEntity abilityEntity);
    
    /**
     * 取消关联
     */
    void unAttach(AbilityEntity abilityEntity);
    
    /**
     * 获取关联的对象
     */
    AbilityEntity getOwner();
    
    /**
     * 关联后的回调
     */
    default void afterAttach(AbilityEntity abilityEntity) {}
    
    /**
     * 取消关联后的回调
     */
    default void afterUnAttach(AbilityEntity abilityEntity) {}
}
```

**设计特点：**
- 支持动态关联和取消关联
- 提供生命周期回调
- 松耦合设计

### 3.2 能力实体接口（AbilityEntity）

```java
public interface AbilityEntity {
    String getType();                              // 实体类型
    String getId();                                // 实体ID
    <T extends Ability> T getAbility(String abilityName);
    void setAbility(String abilityName, Ability ability);
}
```

**设计特点：**
- 支持多能力组合
- 类型安全的能力获取
- 动态能力管理

### 3.3 抽象能力实体（BaseAbilityEntity）

```java
@Slf4j
@ToString()
public class BaseAbilityEntity<SELF extends BaseAbilityEntity<SELF>>
        implements AbilityEntity, TimeAble, CallAble, SaveAble {
    
    protected String id;
    protected Integer queueId;
    Map<String, Ability> abilityMap = new HashMap<>();
    
    @Override
    public <T extends Ability> T getAbility(String abilityName) {
        return (T) abilityMap.get(abilityName);
    }
    
    @Override
    public void setAbility(String abilityName, Ability ability) {
        abilityMap.put(abilityName, ability);
    }
    
    // 能力管理方法
    public Homo<SELF> attachAbility(Ability ability) {
        // 关联能力
    }
    
    public Homo<SELF> unAttachAbility(String abilityName) {
        // 取消关联能力
    }
}
```

**设计特点：**
- 泛型自引用设计
- 内置能力管理
- 支持多种能力类型

### 3.4 抽象能力（AbstractAbility）

```java
public abstract class AbstractAbility implements Ability {
    protected AbilityEntity abilityEntity;
    
    @Override
    public void attach(AbilityEntity abilityEntity) {
        this.abilityEntity = abilityEntity;
        abilityEntity.setAbility(this);
        log.info("Ability attach to entity name {} type {} id {}", 
                this.getClass().getSimpleName(), 
                abilityEntity.getType(), 
                abilityEntity.getId());
    }
    
    @JSONField(serialize = false, deserialize = false)
    @Override
    public AbilityEntity getOwner() {
        return abilityEntity;
    }
}
```

**设计特点：**
- 提供基础实现
- 自动关联管理
- 序列化控制

## 4. 核心能力实现

### 4.1 调用能力（CallAbility）

```java
public class CallAbility extends AbstractAbility implements ICallAbility {
    public static Map<Class<?>, CallDispatcher> entityDispatcherMap = new ConcurrentHashMap<>();
    
    public CallAbility(AbilityEntity abilityEntity) {
        attach(abilityEntity);
    }
    
    @Override
    public void afterAttach(AbilityEntity abilityEntity) {
        CallSystem callSystem = GetBeanUtil.getBean(CallSystem.class);
        callSystem.add(this).start();
    }
    
    @Override
    public void unAttach(AbilityEntity abilityEntity) {
        CallSystem callSystem = GetBeanUtil.getBean(CallSystem.class);
        callSystem.remove(this).start();
    }
    
    public Homo callEntity(String srcName, String funName, byte[][] data,
                          Integer podId, ParameterMsg parameterMsg) {
        // 实体远程调用实现
    }
}
```

**功能特点：**
- 支持实体远程调用
- 自动注册到调用系统
- 支持有状态调用

### 4.2 存储能力（StorageAbility）

```java
public class StorageAbility extends AbstractAbility implements IStorageAbility {
    @Autowired
    private StorageSystem storageSystem;
    
    @Override
    public Homo<Boolean> save() {
        // 保存实体数据
        return storageSystem.saveEntity(getOwner());
    }
    
    @Override
    public Homo<Boolean> load() {
        // 加载实体数据
        return storageSystem.loadEntity(getOwner());
    }
    
    @Override
    public Homo<Boolean> update() {
        // 更新实体数据
        return storageSystem.updateEntity(getOwner());
    }
}
```

**功能特点：**
- 自动数据持久化
- 支持增量更新
- 集成存储系统

### 4.3 时间能力（TimeAbility）

```java
public class TimeAbility extends AbstractAbility implements ITimeAbility {
    @Override
    public Homo<Void> schedule(Runnable task, long delay, TimeUnit unit) {
        // 定时任务调度
    }
    
    @Override
    public Homo<Void> scheduleAtFixedRate(Runnable task, long initialDelay, 
                                         long period, TimeUnit unit) {
        // 周期性任务调度
    }
    
    @Override
    public Homo<Void> cancel() {
        // 取消定时任务
    }
}
```

**功能特点：**
- 定时任务调度
- 周期性任务支持
- 任务生命周期管理

## 5. 能力系统管理

### 5.1 存储实体管理器（StorageEntityMgr）

```java
@Slf4j
public class StorageEntityMgr extends CacheEntityMgr implements ServiceModule {
    static Map<String, Class<AbilityEntity>> typeToAbilityObjectClazzMap = new ConcurrentHashMap<>();
    AbilityProperties abilityProperties;
    
    @Autowired
    StorageSystem storageSystem;
    Map<Class<? extends AbilitySystem>, AbilitySystem> systemMap = new HashMap<>();
    IdLocker idLocker = new IdLocker();
    
    @Autowired
    public StorageEntityMgr(Set<? extends AbilitySystem> abilitySystems, 
                           AbilityProperties abilityProperties) {
        this.abilityProperties = abilityProperties;
        init(abilitySystems);
    }
    
    protected void init(Set<? extends AbilitySystem> abilitySystems) {
        // 初始化能力系统
        // 扫描实体类型
        // 注册实体类映射
    }
    
    @Override
    public <T extends AbilityEntity> Homo<T> getOrCreate(String type, String id, Class<T> clazz) {
        // 获取或创建实体
        // 支持缓存
        // 支持持久化加载
    }
}
```

**功能特点：**
- 实体生命周期管理
- 自动类型扫描
- 缓存和持久化支持

### 5.2 缓存实体管理器（CacheEntityMgr）

```java
@Slf4j
public class CacheEntityMgr implements ServiceModule {
    Map<String, AbilityEntity> entityCache = new ConcurrentHashMap<>();
    
    public <T extends AbilityEntity> Homo<T> get(String type, String id) {
        // 从缓存获取实体
    }
    
    public <T extends AbilityEntity> Homo<T> put(String type, String id, T entity) {
        // 缓存实体
    }
    
    public Homo<Boolean> remove(String type, String id) {
        // 移除实体
    }
}
```

**功能特点：**
- 内存缓存管理
- 实体快速访问
- 缓存策略控制

### 5.3 调用系统（CallSystem）

```java
@Slf4j
public class CallSystem implements ICallSystem, ServiceModule {
    IdCallQueue idCallQueue = new IdCallQueue("CallSystem", 5000, 
                                             IdCallQueue.DropStrategy.DROP_CURRENT_TASK, 3);
    
    @Autowired
    ServiceMgr serviceMgr;
    @Autowired
    ServiceStateMgr serviceStateMgr;
    
    Map<String, Boolean> methodInvokeByQueueMap = new ConcurrentHashMap<>();
    KKMap<String, String, ICallAbility> type2id2callAbilityMap = new KKMap<>();
    KKMap<String, String, Boolean> id2type2callLinkMap = new KKMap<>();
    
    @Override
    public Homo call(String srcName, EntityRequest entityRequest, 
                    Integer podId, ParameterMsg parameterMsg) throws Exception {
        // 实体远程调用
        // 路由到对应服务
        // 处理调用结果
    }
}
```

**功能特点：**
- 实体远程调用管理
- 有状态调用路由
- 调用队列管理

### 5.4 存储系统（StorageSystem）

```java
@Slf4j
public class StorageSystem implements IStorageSystem, ServiceModule {
    @Autowired
    private ObjStorage objStorage;
    
    @Override
    public <T extends AbilityEntity> Homo<Boolean> saveEntity(T entity) {
        // 保存实体到存储
    }
    
    @Override
    public <T extends AbilityEntity> Homo<T> loadEntity(String type, String id, Class<T> clazz) {
        // 从存储加载实体
    }
    
    @Override
    public <T extends AbilityEntity> Homo<Boolean> updateEntity(T entity) {
        // 更新实体数据
    }
}
```

**功能特点：**
- 实体数据持久化
- 自动序列化处理
- 版本控制支持

## 6. 实体类型管理

### 6.1 实体类型注解（EntityType）

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntityType {
    String type();                    // 实体类型
    boolean isStateful() default true; // 是否是有状态实体
}
```

### 6.2 实体类型扫描

```java
public void scanEntityTypes() {
    String entityScanPath = abilityProperties.getEntityScanPath();
    Reflections reflections = new Reflections(entityScanPath);
    Set<Class<?>> entityClazzSet = reflections.getTypesAnnotatedWith(EntityType.class);
    
    for (Class<?> entityClazz : entityClazzSet) {
        EntityType entityType = HomoAnnotationUtil.findAnnotation(entityClazz, EntityType.class);
        String type = entityType.type();
        typeToAbilityObjectClazzMap.computeIfAbsent(type, k -> (Class<AbilityEntity>) entityClazz);
    }
}
```

## 7. 能力组合示例

### 7.1 用户实体定义

```java
@EntityType(type = "user", isStateful = true)
public class UserEntity extends BaseAbilityEntity<UserEntity> {
    private String name;
    private int level;
    private long exp;
    
    // 业务方法
    public void addExp(long exp) {
        this.exp += exp;
        // 触发升级检查
        checkLevelUp();
    }
    
    private void checkLevelUp() {
        // 升级逻辑
    }
}
```

### 7.2 能力组合使用

```java
@Service
public class UserService {
    @Autowired
    private StorageEntityMgr entityMgr;
    
    public void createUser(String userId, String name) {
        entityMgr.getOrCreate("user", userId, UserEntity.class)
            .nextDo(user -> {
                user.setName(name);
                user.setLevel(1);
                user.setExp(0);
                // 说明：StorageAbility/CallAbility/TimeAbility 由系统在实体创建阶段自动装配，无需手动 attach
                return Homo.result(user);
            })
            .start();
    }
}
```

## 8. 性能优化

### 8.1 实体缓存
- 内存缓存实体实例
- LRU 缓存策略
- 缓存大小控制

### 8.2 能力池化
- 能力对象池化
- 减少对象创建开销
- 内存使用优化

### 8.3 异步处理
- 异步能力调用
- 非阻塞操作
- 响应式编程

## 9. 监控和调试

### 9.1 能力监控
- 能力使用统计
- 性能指标收集
- 异常监控

### 9.2 实体监控
- 实体生命周期监控
- 内存使用监控
- 调用链路追踪

### 9.3 调试支持
- 能力状态查看
- 实体状态导出
- 调用日志记录

## 10. 使用示例

### 10.1 定义实体

```java
@EntityType(type = "player")
public class PlayerEntity extends BaseAbilityEntity<PlayerEntity> {
    private String playerName;
    private int level;
    private long exp;
    
    public void gainExp(long exp) {
        this.exp += exp;
        checkLevelUp();
    }
    
    private void checkLevelUp() {
        if (exp >= getExpForNextLevel()) {
            level++;
            // 触发升级事件
        }
    }
}
```

### 10.2 使用实体

```java
@Service
public class PlayerService {
    @Autowired
    private StorageEntityMgr entityMgr;
    
    public void playerLogin(String playerId) {
        entityMgr.getOrCreate("player", playerId, PlayerEntity.class)
            .nextDo(player -> {
                // 添加存储能力
                return player.attachAbility(new StorageAbility());
            })
            .nextDo(player -> {
                // 添加调用能力
                return player.attachAbility(new CallAbility(player));
            })
            .nextDo(player -> {
                // 加载玩家数据
                return player.getAbility(StorageAbility.class).load();
            })
            .start();
    }
}
```

## 11. 总结

`homo-core-entity-ability` 模块通过实现灵活的能力系统，为 homo-core 框架提供了强大的实体管理能力。其组合模式的设计使得系统具有很好的扩展性，而有状态实体的支持则满足了游戏服务器的特殊需求。该模块的成功设计为整个框架的实体管理奠定了坚实的基础。

---

## 12. 设计思想与约束（补充）
- 单实例有序：同一实体的调用在同一 `IdCallQueue` 上串行，避免并发写冲突。
- 能力即插件：能力遵循最小接口，避免交叉耦合；能力间协作通过实体（Owner）进行。
- 可观察：所有能力 attach/unAttach、实体创建/销毁、远程调用均打点（MDC：entityType/entityId）。

## 13. 实体访问流程（ASCII）
```text
Client → CallSystem → 路由(pod/entityId) → EntityMgr.getOrCreate
       → BaseAbilityEntity.attach(所需能力) → 执行业务 → StorageAbility.save()
```

## 14. 关键类关系（ASCII）
```text
+-------------------+      1  *      +------------------+
| StorageEntityMgr  |----------------| BaseAbilityEntity|
|  - systemMap      |                |  - abilityMap    |
+---------+---------+                +----+-------------+
          |                               |
          |uses                           |has
          v                               v
+-------------------+            +------------------+
| AbilitySystem     |            | Ability          |
| (Call/Storage/...)|            | (Call/Storage/..)|
+-------------------+            +------------------+
```

## 15. 能力装配与规则
- 命名：`Ability` 类名以 `Ability` 结尾，默认 `abilityName = simpleName`。
- 生命周期：`attach` → `afterAttach` → 使用 → `unAttach` → `afterUnAttach`。
- 存储能力：默认使用 `ObjStorage` 的 `logicType=entityType`，`ownerId=entityId`，字段 `data`。

## 16. 远程调用规则
- Facade 方法返回 `Homo<T>`；参数支持 POJO/Proto/基本类型。
- 有状态：`@EntityType(isStateful=true)` 时，CallSystem 基于 `entityId` 做一致性路由。
- MDC：`entityType/entityId/method` 必须注入，异常路径保留 MDC。

## 17. Checklist
- [ ] 实体方法无共享可变静态状态
- [ ] 能力间无循环依赖
- [ ] 远程方法签名仅使用可序列化类型
- [ ] 所有入口均恢复 MDC（traceId/requestId/entityId）

## 18. 演进
- 能力热替换（版本化 Ability）
- 实体快照与回放（调试/回归）
