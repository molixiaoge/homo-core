# homo-core-storage 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 统一抽象：`ByteStorage/ObjStorage` 面向业务，`StorageDriver` 面向实现。
- 多级存储：Redis 热数据 + MySQL/Mongo 冷数据，读写路径分离。
- 自动落地：写入缓存 + 脏表标记，落地程序批处理写 DB。

### 0.2 架构图（ASCII）
```text
Biz → Obj/ByteStorage → StorageDriver(Redis) → Redis(HSet)
                                 └─ DirtyMap → Landing(MySQL/Mongo)
未命中 → LandingDriver.hot* → 回填 Redis → 再读
```

### 0.3 典型流程（ASCII）
```text
update/incr/remove → 写 Redis → 记录 DirtyMap → 返回
get 未命中 → hotFields/hotAllField → 回填 → 再读
landing → 锁表 → 快照 → 批量 → 降级单条 → 错误表
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-storage` 是 homo-core 框架的存储抽象模块，提供了统一的存储接口和多种存储实现，支持字节存储、对象存储、文档存储等多种存储方式。

### 1.2 设计目标
- 提供统一的存储抽象接口
- 支持多种存储后端（Redis、MySQL、MongoDB）
- 实现自动化的数据落地机制
- 支持多级缓存策略
- 提供响应式存储操作

## 2. 核心设计理念

### 2.1 存储抽象层
通过定义统一的存储接口，屏蔽不同存储后端的实现细节，为上层业务提供一致的存储操作体验。

### 2.2 多级存储架构
- **L1 缓存**：内存缓存（Caffeine）
- **L2 缓存**：Redis 缓存
- **L3 存储**：持久化存储（MySQL、MongoDB）

### 2.3 自动化落地
实现脏数据检测和自动落地机制，确保数据的一致性和持久性。

## 3. 核心组件设计

### 3.1 存储驱动接口（StorageDriver）

```java
public interface StorageDriver {
    // 异步更新操作
    Homo<Pair<Boolean, Map<String, byte[]>>> asyncUpdate(
        String appId, String regionId, String logicType, 
        String ownerId, Map<String, byte[]> keyList);
    
    // 异步查询操作
    Homo<Map<String, byte[]>> asyncGetByFields(
        String appId, String regionId, String logicType, 
        String ownerId, List<String> fieldList);
    
    // 异步删除操作
    Homo<Boolean> asyncDelByFields(
        String appId, String regionId, String logicType, 
        String ownerId, List<String> fieldList);
}
```

**设计特点：**
- 统一的异步操作接口
- 支持批量操作
- 支持多租户隔离（appId + regionId）

### 3.2 字节存储（ByteStorage）

```java
@Slf4j
public class ByteStorage implements Module {
    @Autowired(required = false)
    StorageDriver storageDriver;
    
    // 更新存储数据
    public Homo<Pair<Boolean, Map<String, byte[]>>> update(
        String logicType, String ownerId, Map<String, byte[]> keyList);
    
    // 保存单条数据
    public Homo<Boolean> save(String logicType, String ownerId, 
                             String key, byte[] data);
    
    // 获取单个数据
    public Homo<byte[]> get(String logicType, String ownerId, String key);
    
    // 批量获取数据
    public Homo<Map<String, byte[]>> get(String logicType, String ownerId, 
                                        List<String> keyList);
}
```

**设计特点：**
- 支持字节数组存储
- 提供便捷的 CRUD 操作
- 自动处理应用 ID 和区域 ID

### 3.3 对象存储（ObjStorage）

```java
@Slf4j
public class ObjStorage implements Module {
    @Autowired(required = false)
    ByteStorage storage;
    
    @Autowired(required = false)
    HomoSerializationProcessor serializationProcessor;
    
    // 保存对象
    public <T extends SaveObject> Homo<Boolean> save(T obj);
    
    // 加载对象
    public <T extends SaveObject> Homo<T> load(String logicType, 
                                              String ownerId, Class<T> clazz);
    
    // 批量保存对象
    public <T extends Serializable> Homo<Pair<Boolean, Map<String, byte[]>>>
        saveBatch(String appId, String regionId, String logicType, 
                 String ownerId, Map<String, T> objMap);
}
```

**设计特点：**
- 自动序列化/反序列化
- 支持对象版本管理
- 支持批量操作

### 3.4 文档存储（DocumentStorage）

```java
@Slf4j
public class DocumentStorage<F, S, U, P> implements Module {
    @Autowired
    private RootModule rootModule;
    
    // 文档查询
    public <T> Homo<List<T>> asyncQuery(Bson filter, Bson sort, 
                                       Integer limit, Integer skip, Class<T> clazz);
    
    // 文档保存
    public <T> Homo<Boolean> asyncSave(T obj);
    
    // 文档更新
    public <T> Homo<Boolean> asyncUpdate(T obj, Bson update);
    
    // 文档删除
    public <T> Homo<Boolean> asyncDelete(Bson filter);
}
```

**设计特点：**
- 支持复杂查询条件
- 支持排序和分页
- 支持软删除

## 4. 存储实现

### 4.1 Redis 存储实现

#### 4.1.1 Redis 缓存驱动（RedisCacheDriver）
```java
@Slf4j
public class RedisCacheDriver implements CacheDriver {
    private static String REDIS_KEY_TMPL = "slug-cache:{%s:%s:%s:%s}";
    
    @Override
    public Homo<Map<String, byte[]>> asyncGetByFields(...) {
        // 使用 Redis Hash 结构存储
        // 支持批量操作
        // 集成链路追踪
    }
}
```

#### 4.1.2 Redis 存储驱动（RedisStorageDriver）
```java
public class RedisStorageDriver implements StorageDriver {
    // 基于 Redis 的存储实现
    // 支持数据过期策略
    // 支持数据压缩
}
```

### 4.2 MySQL 存储实现

#### 4.2.1 MySQL 存储驱动（MysqlStorageDriver）
```java
public class MysqlStorageDriver implements StorageDriver {
    // 基于 MySQL 的存储实现
    // 支持事务操作
    // 支持读写分离
}
```

#### 4.2.2 数据对象（DataObject）
```java
@Builder
@Data
@AllArgsConstructor
public class DataObject {
    @TableField(value = "primary_key", type = JdbcType.VARCHAR, id = true)
    private String primaryKey;
    
    @TableField(value = "logic_type", type = JdbcType.VARCHAR)
    private String logicType;
    
    @TableField(value = "owner_id", type = JdbcType.VARCHAR)
    private String ownerId;
    
    @TableField(type = JdbcType.VARCHAR, length = 100)
    private String key;
    
    @TableField(type = JdbcType.BLOB)
    private byte[] value;
    
    // 版本控制和软删除字段
    @TableField(value = "up_version", type = JdbcType.BIGINT)
    private Long upVersion;
    
    @TableField(value = "is_del", type = JdbcType.INTEGER)
    private Integer isDel;
}
```

### 4.3 MongoDB 存储实现

#### 4.3.1 MongoDB 文档存储驱动
```java
@Slf4j
public class MongoDocumentStorageDriverImpl implements DocumentStorageDriver {
    @Autowired
    private MongoHelper mongoHelper;
    
    @Override
    public <T> Homo<List<T>> asyncQuery(Bson filter, Bson sort, 
                                       Integer limit, Integer skip, Class<T> clazz) {
        // 基于 MongoDB 的文档查询
        // 支持复杂查询条件
        // 支持索引优化
    }
}
```

## 5. 多级缓存设计

### 5.1 缓存层次结构

```
L1: 内存缓存 (Caffeine)
    ↓ (缓存未命中)
L2: Redis 缓存
    ↓ (缓存未命中)
L3: 持久化存储 (MySQL/MongoDB)
```

### 5.2 缓存策略

#### 5.2.1 写入策略
- **Write-Through**：同时写入缓存和存储
- **Write-Behind**：先写缓存，异步写存储
- **Write-Around**：直接写存储，绕过缓存

#### 5.2.2 读取策略
- **Cache-Aside**：应用负责缓存管理
- **Read-Through**：缓存负责从存储加载
- **Refresh-Ahead**：预加载热点数据

### 5.3 缓存一致性

#### 5.3.1 一致性保证
- 版本号机制
- 乐观锁控制
- 最终一致性

#### 5.3.2 缓存更新
- 主动失效
- 被动失效
- 定时刷新

## 6. 自动化落地机制

### 6.1 脏数据检测

#### 6.1.1 脏表设计
```java
public class DirtyData {
    private String primaryKey;
    private String logicType;
    private String ownerId;
    private String field;
    private byte[] value;
    private Long updateTime;
    private Integer isDirty;
}
```

#### 6.1.2 脏数据标记
- 数据变更时自动标记为脏
- 支持批量标记
- 支持优先级控制

### 6.2 落地策略

#### 6.2.1 定时落地
- 固定时间间隔
- 可配置的落地频率
- 支持多表并行落地

#### 6.2.2 条件落地
- 脏数据数量阈值
- 数据大小阈值
- 业务触发条件

### 6.3 落地流程

```
1. 扫描脏表
2. 获取脏数据
3. 批量写入存储
4. 更新版本号
5. 清理脏标记
6. 异常处理
```

## 7. 性能优化

### 7.1 批量操作
- 批量读取
- 批量写入
- 批量更新

### 7.2 连接池管理
- Redis 连接池
- MySQL 连接池
- MongoDB 连接池

### 7.3 异步处理
- 异步 I/O 操作
- 非阻塞调用
- 响应式数据流

### 7.4 数据压缩
- 数据压缩存储
- 传输压缩
- 内存优化

## 8. 监控和运维

### 8.1 性能监控
- 存储操作耗时
- 缓存命中率
- 存储容量监控

### 8.2 异常监控
- 存储异常统计
- 连接异常监控
- 数据一致性检查

### 8.3 运维工具
- 数据迁移工具
- 缓存清理工具
- 性能调优工具

## 9. 使用示例

### 9.1 字节存储使用
```java
@Autowired
private ByteStorage byteStorage;

public void saveUserData() {
    Map<String, byte[]> data = new HashMap<>();
    data.put("name", "张三".getBytes());
    data.put("age", "25".getBytes());
    
    byteStorage.update("user", "user123", data)
        .nextDo(result -> {
            if (result.getKey()) {
                log.info("数据保存成功");
            }
        })
        .start();
}
```

### 9.2 对象存储使用
```java
@Autowired
private ObjStorage objStorage;

public void saveUser() {
    User user = new User("user123", "张三", 25);
    objStorage.save(user)
        .nextDo(success -> {
            if (success) {
                log.info("用户保存成功");
            }
        })
        .start();
}
```

### 9.3 文档存储使用
```java
@Autowired
private DocumentStorage documentStorage;

public void queryUsers() {
    Bson filter = Filters.eq("age", 25);
    Bson sort = Sorts.ascending("name");
    
    documentStorage.asyncQuery(filter, sort, 10, 0, User.class)
        .nextDo(users -> {
            log.info("查询到用户: {}", users);
        })
        .start();
}
```

## 10. 总结

`homo-core-storage` 模块通过提供统一的存储抽象和多级缓存机制，为游戏服务器提供了高性能、高可用的数据存储解决方案。其自动化落地机制确保了数据的一致性，而多级缓存设计则大大提升了数据访问性能。该模块的成功设计为整个 homo-core 框架的数据管理奠定了坚实的基础。

---

## 11. Redis 键模型与落地协作（补充）
- 数据键（HSet）：`slug-data:{appId:regionId:logicType:ownerId}` → field=logicKey → value=bytes
- 全量缓存标记：`cachedAllKey`（建议）
- 逻辑删除：`{logicKey}:delFlag` 或移 `:del` 域
- 与落地模块协作：写入时记录 DirtyMap；读未命中触发 `LandingDriver.hot*` 捞起

## 12. 热加载与脏落地（摘要）
- 热加载：`asyncGet*` 未命中 → `LandingDriver.hotFields/hotAllField` → 回填 → 再读
- 脏落地：`asyncUpdate/incr/remove` → DirtyMap 记录 → 定时/竞争锁 → 快照 → 批/单落地 → 错误表

## 13. Checklist
- [ ] `StorageDriver` 实现保证 Redis/DirtyMap 原子性（Lua 或事务）
- [ ] Incr 使用原子操作；落地写最终值
- [ ] 回填写 Hash 一次性提交，避免部分可见
- [ ] 监控：命中率/落地延迟/脏表堆积
