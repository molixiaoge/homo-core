# homo-core-mq 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 驱动可插拔：统一抽象（Producer/Consumer/Codec），适配 Kafka/Redis 等。
- Topic 规范化：通过策略拼接 `appId/regionId`，保障租户隔离。
- 可恢复：消费失败可回调/重试/路由到错误处理。

### 0.2 架构图（ASCII）
```text
Producer → MQProducerImpl → Codec → Driver → MQ
MQ → Driver → MQConsumerImpl → Codec → RouterMgr → SinkHandler/ReceiverSink
```

### 0.3 典型流程（ASCII）
```text
send(topic,msg) → getRealTopic → encode → driver.send
poll → decode → route(topic) → sink(message) → callback.confirm/reject
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-mq` 是 homo-core 框架的消息队列模块，提供了统一的消息队列抽象，支持多种消息队列实现（Kafka、Redis），实现了消息生产、消费、路由、序列化等核心功能。

### 1.2 设计目标
- 提供统一的消息队列抽象接口
- 支持多种消息队列实现
- 实现消息的生产和消费
- 提供消息序列化和反序列化
- 支持消息路由和过滤
- 提供消息可靠性保证

## 2. 模块架构

### 2.1 模块组成
- `homo-core-mq-base` - 消息队列基础抽象和通用功能
- `homo-core-mq-producer` - 消息生产者实现
- `homo-core-mq-consumer` - 消息消费者实现
- `homo-core-mq-driver-kafka` - Kafka 驱动实现

### 2.2 架构层次
```
业务层
  ↓
消息队列抽象层 (facade)
  ↓
生产者/消费者层 (producer/consumer)
  ↓
驱动实现层 (kafka/redis)
  ↓
消息队列服务 (kafka/redis)
```

## 3. 核心设计理念

### 3.1 驱动模式
通过驱动模式实现不同消息队列的适配，支持 Kafka、Redis 等多种消息队列实现。

### 3.2 生产者-消费者模式
- **生产者（Producer）**：负责消息的创建和发送
- **消费者（Consumer）**：负责消息的接收和处理
- **消息队列（Message Queue）**：消息的存储和传输媒介

### 3.3 消息序列化
- 支持多种序列化方式
- 自动序列化/反序列化
- 消息格式转换

## 4. 核心组件设计

### 4.1 消息队列抽象接口

#### 4.1.1 消息生产者接口（MQProducer）
```java
public interface MQProducer {
    MQType getType();                              // 消息队列类型
    String getRealTopic(String originTopic);       // 获取真实主题名
    <T extends Serializable> void send(String originTopic, T message) throws Exception;
    <T extends Serializable> void send(String originTopic, String key, T message) throws Exception;
    <T extends Serializable> void send(String originTopic, String key, T message, Callback callback) throws Exception;
}
```

#### 4.1.2 消息消费者接口（MQConsumer）
```java
public interface MQConsumer {
    MQType getType();                              // 消息队列类型
    <T extends Serializable> void subscribe(String originTopic, Consumer<T> consumer);
    <T extends Serializable> void subscribe(String originTopic, String groupId, Consumer<T> consumer);
    void start();                                  // 启动消费者
    void stop();                                   // 停止消费者
    Status getStatus();                            // 获取状态
}
```

#### 4.1.3 消息队列类型（MQType）
```java
public enum MQType {
    KAFKA,          // Kafka 消息队列
    REDIS,          // Redis 消息队列
    RABBITMQ        // RabbitMQ 消息队列
}
```

### 4.2 消息生产者实现

#### 4.2.1 消息生产者实现（MQProducerImpl）
```java
@Slf4j
public class MQProducerImpl implements MQProducer {
    MQProducerConfig config;
    MQProducerDriver driver;
    Map<String, String> realTopics;
    ByteSrcCodecRegister codecRegister;
    
    public MQProducerImpl(@NotNull MQProducerConfig config) {
        MQType mqType = config.getType();
        MQProducerDriverFactory driverFactory = MQDriverFactoryProvider.getProducerDriverFactory(mqType);
        
        if (driverFactory == null) {
            throw new RuntimeException(String.format("no producer driver implementation found for MQType %s !", mqType));
        }
        
        this.config = config;
        this.driver = driverFactory.create();
        this.codecRegister = new ByteSrcCodecRegister();
        this.realTopics = new ConcurrentHashMap<>();
    }
    
    @Override
    public <T extends Serializable> void send(@NotNull String originTopic, @NotNull T message) throws Exception {
        send(originTopic, null, message, null);
    }
    
    @Override
    public <T extends Serializable> void send(@NotNull String originTopic, String key, @NotNull T message, Callback callback) throws Exception {
        String realTopic = getRealTopic(originTopic);
        MQCodeC<T, byte[]> codec = codecRegister.getCodec(realTopic);
        byte[] messageBytes = codec.encode(message);
        
        driver.send(realTopic, key, messageBytes, callback);
    }
}
```

**设计特点：**
- 支持多种消息队列驱动
- 自动序列化处理
- 支持回调机制
- 主题名映射

#### 4.2.2 生产者配置（MQProducerConfig）
```java
public class MQProducerConfig {
    private MQType type;                           // 消息队列类型
    private String appId;                          // 应用ID
    private String regionId;                       // 区域ID
    private TopicResolveStrategy topicResolveStrategy; // 主题解析策略
    
    // 配置方法
    public static MQProducerConfig create(MQType type, String appId, String regionId) {
        MQProducerConfig config = new MQProducerConfig();
        config.setType(type);
        config.setAppId(appId);
        config.setRegionId(regionId);
        config.setTopicResolveStrategy(new DefaultTopicResolveStrategy());
        return config;
    }
}
```

### 4.3 消息消费者实现

#### 4.3.1 消息消费者实现（MQConsumerImpl）
```java
@Slf4j
public class MQConsumerImpl implements MQConsumer {
    protected MQConsumerDriver driver;
    public CodecRegister<byte[]> codecRegister;
    final Map<String, String> originTorealTopicCacheMap;
    protected Set<ErrorListener> listeners;
    protected MQConsumerConfig consumerConfig;
    protected RouterMgr routerMgr;
    protected volatile Status status;
    
    public MQConsumerImpl(MQConsumerConfig consumerConfig) {
        MQType mqType = consumerConfig.getType();
        MQConsumerDriverFactory driverFactory = MQDriverFactoryProvider.getConsumerDriverFactory(mqType);
        
        if (driverFactory == null) {
            throw new RuntimeException(String.format("no consumer driver implementation found for MQType %s !", mqType));
        }
        
        this.driver = driverFactory.create(consumerConfig.getGroupId());
        this.routerMgr = new RouterMgr();
        this.codecRegister = new CodecRegister<>();
        this.originTorealTopicCacheMap = new HashMap<>();
        this.status = Status.INIT;
        this.listeners = new HashSet<>();
        this.consumerConfig = consumerConfig;
    }
    
    @Override
    public <T extends Serializable> void subscribe(String originTopic, Consumer<T> consumer) {
        subscribe(originTopic, consumerConfig.getGroupId(), consumer);
    }
    
    @Override
    public <T extends Serializable> void subscribe(String originTopic, String groupId, Consumer<T> consumer) {
        String realTopic = getRealTopic(originTopic);
        routerMgr.registerConsumer(realTopic, consumer);
        
        if (status == Status.INIT) {
            start();
        }
    }
}
```

**设计特点：**
- 支持多种消息队列驱动
- 自动反序列化处理
- 支持消息路由
- 支持错误监听

#### 4.3.2 消费者配置（MQConsumerConfig）
```java
public class MQConsumerConfig {
    private MQType type;                           // 消息队列类型
    private String groupId;                        // 消费者组ID
    private String appId;                          // 应用ID
    private String regionId;                       // 区域ID
    private TopicResolveStrategy topicResolveStrategy; // 主题解析策略
    
    // 配置方法
    public static MQConsumerConfig create(MQType type, String groupId, String appId, String regionId) {
        MQConsumerConfig config = new MQConsumerConfig();
        config.setType(type);
        config.setGroupId(groupId);
        config.setAppId(appId);
        config.setRegionId(regionId);
        config.setTopicResolveStrategy(new DefaultTopicResolveStrategy());
        return config;
    }
}
```

### 4.4 消息序列化

#### 4.4.1 序列化接口（MQCodeC）
```java
public interface MQCodeC<T, DEST> {
    DEST encode(T obj);
    T decode(DEST data);
}
```

#### 4.4.2 FST 序列化器（FSTMessageCodec）
```java
public class FSTMessageCodec<T extends Serializable> implements MQCodeC<T, byte[]> {
    private FSTConfiguration fstConfiguration;
    
    public FSTMessageCodec() {
        this.fstConfiguration = FSTConfiguration.getDefaultConfiguration();
    }
    
    @Override
    public byte[] encode(T obj) {
        return fstConfiguration.asByteArray(obj);
    }
    
    @Override
    public T decode(byte[] data) {
        return (T) fstConfiguration.asObject(data);
    }
}
```

#### 4.4.3 编解码器注册器（CodecRegister）
```java
@Slf4j
public class CodecRegister<DEST> {
    MQCodeC<?, ?> defaultCodec = new FSTMessageCodec();
    final ConcurrentHashMap<String, MQCodeC<?, DEST>> codecMap = new ConcurrentHashMap<>();
    
    public <T extends java.io.Serializable> void setCodec(@NotNull String topic, @NotNull MQCodeC<T,DEST> codec){
        codecMap.put(topic, codec);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends java.io.Serializable> MQCodeC<T,DEST> getCodec(@NotNull String topic){
        MQCodeC<T,DEST> codec = (MQCodeC<T,DEST>)codecMap.get(topic);
        if(codec == null){
            codec = (MQCodeC<T,DEST>)defaultCodec;
            if(log.isTraceEnabled()){
                log.trace("未找到 {} 特定的编码器，返回默认编码器", topic);
            }
        }
        return codec;
    }
}
```

## 5. Kafka 驱动实现

### 5.1 Kafka 生产者驱动（MQKafkaProducerDriver）

```java
@Slf4j
public class MQKafkaProducerDriver implements MQProducerDriver {
    private KafkaProducerTemplate<String, byte[]> producerTemplate;
    private MQKafkaProducerProperties properties;
    
    public MQKafkaProducerDriver(MQKafkaProducerProperties properties) {
        this.properties = properties;
        this.producerTemplate = createProducerTemplate();
    }
    
    private KafkaProducerTemplate<String, byte[]> createProducerTemplate() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, properties.getAcks());
        props.put(ProducerConfig.RETRIES_CONFIG, properties.getRetries());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, properties.getBatchSize());
        props.put(ProducerConfig.LINGER_MS_CONFIG, properties.getLingerMs());
        
        ThreadPoolExecutor executor = ThreadPoolFactory.newThreadPool("kafka-producer", 
                                                                     properties.getThreadPoolSize(), 
                                                                     properties.getThreadPoolQueueSize());
        return new KafkaProducerTemplate<>(props, executor);
    }
    
    @Override
    public void send(String topic, String key, byte[] message, Callback callback) {
        producerTemplate.sendMessage(topic, key, message, callback);
    }
}
```

### 5.2 Kafka 消费者驱动（MQKafkaConsumerDriver）

```java
@Slf4j
public class MQKafkaConsumerDriver implements MQConsumerDriver {
    private KafkaConsumer<String, byte[]> consumer;
    private MQKafkaConsumerProperties properties;
    private volatile boolean running = false;
    private Thread consumerThread;
    
    public MQKafkaConsumerDriver(String groupId, MQKafkaConsumerProperties properties) {
        this.properties = properties;
        this.consumer = createConsumer(groupId);
    }
    
    private KafkaConsumer<String, byte[]> createConsumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, properties.isEnableAutoCommit());
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, properties.getAutoCommitIntervalMs());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, properties.getMaxPollRecords());
        
        return new KafkaConsumer<>(props);
    }
    
    @Override
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        consumerThread = new Thread(new ConsumerWorker(), "kafka-consumer-" + System.currentTimeMillis());
        consumerThread.start();
    }
    
    @Override
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        consumer.close();
    }
}
```

### 5.3 Kafka 消费者工作线程（ConsumerWorker）

```java
private class ConsumerWorker implements Runnable {
    @Override
    public void run() {
        while (running) {
            try {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        // 处理消息
                        onMessage(record.topic(), record.key(), record.value());
                    } catch (Exception e) {
                        log.error("Error processing message from topic: {}", record.topic(), e);
                    }
                }
                
                // 手动提交偏移量
                if (!properties.isEnableAutoCommit()) {
                    consumer.commitSync();
                }
            } catch (Exception e) {
                log.error("Error in consumer worker", e);
            }
        }
    }
}
```

## 6. 消息路由

### 6.1 消息路由器（RouterMgr）

```java
@Slf4j
public class RouterMgr {
    private Map<String, Set<Consumer<Object>>> topicConsumerMap = new ConcurrentHashMap<>();
    
    public <T extends Serializable> void registerConsumer(String topic, Consumer<T> consumer) {
        topicConsumerMap.computeIfAbsent(topic, k -> new HashSet<>()).add((Consumer<Object>) consumer);
    }
    
    public void topicRouter(String topic, Object message, ConsumerCallback callback) {
        Set<Consumer<Object>> consumers = topicConsumerMap.get(topic);
        if (consumers != null && !consumers.isEmpty()) {
            for (Consumer<Object> consumer : consumers) {
                try {
                    consumer.accept(message);
                    callback.confirm();
                } catch (Exception e) {
                    log.error("Error processing message for topic: {}", topic, e);
                    callback.reject();
                }
            }
        } else {
            log.warn("No consumer found for topic: {}", topic);
            callback.confirm(); // 确认消息，避免重复消费
        }
    }
}
```

### 6.2 主题解析策略（TopicResolveStrategy）

```java
public interface TopicResolveStrategy {
    String getRealTopic(String originTopic, String appId, String regionId);
}

public class DefaultTopicResolveStrategy implements TopicResolveStrategy {
    @Override
    public String getRealTopic(String originTopic, String appId, String regionId) {
        return String.format("%s_%s_%s", appId, regionId, originTopic);
    }
}
```

## 7. 错误处理

### 7.1 错误监听器（ErrorListener）

```java
public interface ErrorListener {
    void onError(String topic, byte[] message, Throwable error);
}

public class DefaultErrorListener implements ErrorListener {
    @Override
    public void onError(String topic, byte[] message, Throwable error) {
        log.error("Message processing error for topic: {}", topic, error);
        // 可以在这里实现错误消息的持久化、告警等逻辑
    }
}
```

### 7.2 消费者回调（ConsumerCallback）

```java
public interface ConsumerCallback {
    void confirm();     // 确认消息处理成功
    void reject();      // 拒绝消息，触发重试
}
```

## 8. 性能优化

### 8.1 批量处理
- 批量发送消息
- 批量消费消息
- 批量提交偏移量

### 8.2 异步处理
- 异步消息发送
- 异步消息处理
- 非阻塞 I/O

### 8.3 连接池管理
- 生产者连接池
- 消费者连接池
- 连接复用

## 9. 监控和运维

### 9.1 消息监控
- 消息发送速率
- 消息消费速率
- 消息延迟监控

### 9.2 错误监控
- 消息处理错误率
- 连接异常监控
- 序列化错误监控

### 9.3 性能监控
- 吞吐量监控
- 延迟监控
- 资源使用监控

## 10. 配置管理

### 10.1 Kafka 生产者配置
```yaml
homo:
  mq:
    kafka:
      producer:
        bootstrapServers: localhost:9092
        acks: all
        retries: 3
        batchSize: 16384
        lingerMs: 5
        threadPoolSize: 10
        threadPoolQueueSize: 1000
```

### 10.2 Kafka 消费者配置
```yaml
homo:
  mq:
    kafka:
      consumer:
        bootstrapServers: localhost:9092
        autoOffsetReset: earliest
        enableAutoCommit: false
        autoCommitIntervalMs: 1000
        maxPollRecords: 500
```

## 11. 使用示例

### 11.1 消息生产者使用

```java
@Service
public class MessageProducerService {
    @Autowired
    private MQProducer messageProducer;
    
    public void sendUserLoginEvent(String userId, String loginTime) {
        UserLoginEvent event = new UserLoginEvent(userId, loginTime);
        try {
            messageProducer.send("user.login", userId, event, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        log.error("Failed to send message", exception);
                    } else {
                        log.info("Message sent successfully to topic: {}", metadata.topic());
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error sending message", e);
        }
    }
}
```

### 11.2 消息消费者使用

```java
@Service
public class MessageConsumerService {
    @Autowired
    private MQConsumer messageConsumer;
    
    @PostConstruct
    public void init() {
        // 订阅用户登录事件
        messageConsumer.subscribe("user.login", this::handleUserLoginEvent);
        
        // 启动消费者
        messageConsumer.start();
    }
    
    private void handleUserLoginEvent(UserLoginEvent event) {
        log.info("User login event received: {}", event);
        // 处理用户登录事件
        processUserLogin(event);
    }
    
    private void processUserLogin(UserLoginEvent event) {
        // 业务逻辑处理
    }
}
```

## 12. 总结

`homo-core-mq` 模块通过提供统一的消息队列抽象和多种驱动实现，为 homo-core 框架提供了强大的消息通信能力。其灵活的序列化机制和消息路由功能使得系统具有很好的扩展性，而错误处理和监控功能则确保了消息的可靠性。该模块的成功设计为整个框架的异步消息处理奠定了坚实的基础。

---

## 13. 设计思想（补充）
- 统一抽象：Producer/Consumer/Codec/Router 解耦驱动细节。
- 主题规范：通过 `TopicResolveStrategy(appId/regionId)` 防止跨租户串话。
- 可恢复：消费错误可回调/重试/死信策略（可扩展）。

## 14. 生产与消费流程（ASCII）
```text
Produce: biz → MQProducer → codec.encode → driver.send → MQ
Consume: driver.poll → codec.decode → RouterMgr.topicRouter → Sink/Handler
```

## 15. 关键关系（ASCII）
```text
MQProducerImpl --uses--> MQProducerDriver
MQConsumerImpl --uses--> MQConsumerDriver --push--> RouterMgr --invoke--> SinkHandler/ReceiverSink
CodecRegister ↔ MQCodeC
```

## 16. 可靠性与顺序
- 顺序：同 key 消息保持分区内有序；跨分区不保证。
- 确认：`ConsumerCallback.confirm/reject`；reject 触发重试或记录错误（可配置）。
- 幂等：消费者侧建议使用业务幂等键（如 eventId）。

## 17. Checklist
- [ ] 明确 topic 解析策略（默认/追加 appId/regionId）
- [ ] 注册编解码器（全局/按 topic）
- [ ] 消费者错误监听已配置
- [ ] 监控：发送/消费速率、失败率、积压
