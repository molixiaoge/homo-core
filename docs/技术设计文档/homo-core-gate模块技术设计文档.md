# homo-core-gate 模块技术设计文档

## 0. 设计思路与架构概览

### 0.1 设计思路
- 轻网关：聚焦连接、编解码、路由、限流，业务逻辑转发后端。
- 可观测：会话级 MDC 注入（userId/channel/ip），重要路径埋点。
- 背压与隔离：对连接/全局限流，防止后端过载。

### 0.2 架构图（ASCII）
```text
Client ⇄ Netty(TcpGateDriver) → pipeline(decoder→custom→logic→encoder)
                           → GameGateServer 回调（connect/message/disconnect）
```

### 0.3 流程（ASCII）
```text
connect → onClientConnect → attr 初始化 → 监听可写
read → decoder → AbstractGateLogicHandler.doProcess → 路由后端（RPC/MQ）
write → sendToClient → encoder → socket
close → onClientDisconnect → 清理
```

## 1. 模块概述

### 1.1 模块定位
`homo-core-gate` 是 homo-core 框架的网关模块，提供了统一的客户端连接管理和消息路由功能，支持多种通信协议（TCP、HTTP），实现了连接管理、消息处理、负载均衡等核心功能。

### 1.2 设计目标
- 提供统一的网关抽象接口
- 支持多种通信协议
- 实现客户端连接管理
- 提供消息路由和转发
- 支持负载均衡和故障转移
- 提供连接状态监控

## 2. 模块架构

### 2.1 模块组成
- `homo-core-gate` - 网关抽象和通用功能
- `homo-core-gate-tcp` - TCP 协议实现

### 2.2 架构层次
```
客户端连接
  ↓
网关抽象层 (facade)
  ↓
协议实现层 (tcp)
  ↓
传输层 (netty)
```

## 3. 核心设计理念

### 3.1 协议抽象
通过统一的接口抽象不同的网关协议，支持协议的无缝切换和扩展。

### 3.2 连接管理
- 客户端连接生命周期管理
- 连接状态监控
- 连接池管理
- 连接健康检查

### 3.3 消息路由
- 消息分发和路由
- 消息格式转换
- 消息队列管理
- 消息持久化

## 4. 核心组件设计

### 4.1 网关抽象接口

#### 4.1.1 网关驱动接口（GateDriver）
```java
public interface GateDriver {
    /**
     * 启动网关服务
     */
    void startGate(GateServer gateServer);
    
    /**
     * 停止网关服务
     */
    void stopGate();
    
    /**
     * 发送消息到客户端
     */
    void sendToClient(GateClient gateClient, byte[] data);
    
    /**
     * 广播消息
     */
    void broadcast(byte[] data);
    
    /**
     * 获取连接数
     */
    int getConnectionCount();
}
```

#### 4.1.2 网关服务器接口（GateServer）
```java
public interface GateServer {
    String getName();              // 服务器名称
    int getPort();                 // 监听端口
    String getHost();              // 监听地址
    
    /**
     * 处理客户端连接
     */
    void onClientConnect(GateClient client);
    
    /**
     * 处理客户端断开
     */
    void onClientDisconnect(GateClient client);
    
    /**
     * 处理客户端消息
     */
    void onClientMessage(GateClient client, byte[] message);
}
```

#### 4.1.3 网关客户端接口（GateClient）
```java
public interface GateClient {
    String getId();                // 客户端ID
    String getSessionId();         // 会话ID
    String getRemoteAddress();     // 远程地址
    boolean isConnected();         // 是否连接
    
    /**
     * 发送消息
     */
    void send(byte[] data);
    
    /**
     * 关闭连接
     */
    void close();
    
    /**
     * 设置属性
     */
    void setAttribute(String key, Object value);
    
    /**
     * 获取属性
     */
    Object getAttribute(String key);
}
```

### 4.2 消息处理

#### 4.2.1 消息包（GateMessagePackage）
```java
public class GateMessagePackage {
    private GateMessageHeader header;
    private byte[] body;
    
    public GateMessagePackage(GateMessageHeader header, byte[] body) {
        this.header = header;
        this.body = body;
    }
}
```

#### 4.2.2 消息头（GateMessageHeader）
```java
public class GateMessageHeader {
    private short messageId;       // 消息ID
    private short messageType;     // 消息类型
    private int bodyLength;        // 消息体长度
    private long timestamp;        // 时间戳
    private Map<String, Object> attributes; // 扩展属性
}
```

#### 4.2.3 消息处理器（GateMessageHandler）
```java
public interface GateMessageHandler {
    /**
     * 处理消息
     */
    void handleMessage(GateClient client, GateMessagePackage message);
    
    /**
     * 获取支持的消息类型
     */
    Set<Short> getSupportedMessageTypes();
}
```

## 5. TCP 协议实现

### 5.1 TCP 网关驱动（TcpGateDriver）

```java
@Slf4j
public class TcpGateDriver implements GateDriver, DriverModule {
    @Autowired(required = false)
    private GateTcpProperties gateTcpProperties;
    
    @Autowired(required = false)
    private GateCommonProperties gateCommonProperties;
    
    private GateServer gateServer;
    private Channel serverChannel;
    private Map<GateClient, Channel> clientMap = new HashMap<>();
    
    // Netty 相关
    private EventLoopGroup bossGroup;
    private EventLoopGroup workGroup;
    
    public void moduleInit() {
        if (Epoll.isAvailable()) {
            bossGroup = new EpollEventLoopGroup(gateTcpProperties.bossNum, bossFactory);
            workGroup = new EpollEventLoopGroup(gateTcpProperties.workNum, workFactory);
            log.info("TcpGateDriver initialize using epoll");
        } else {
            bossGroup = new NioEventLoopGroup(gateTcpProperties.bossNum, bossFactory);
            workGroup = new NioEventLoopGroup(gateTcpProperties.workNum, workFactory);
            log.info("TcpGateDriver initialize not use epoll");
        }
    }
    
    @Override
    public void startGate(GateServer gateServer) {
        try {
            this.gateServer = gateServer;
            ChannelFuture channelFuture = new ServerBootstrap()
                    .group(bossGroup, workGroup)
                    .channel(Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                    .childHandler(new ServerChanelInitializer(customHandlers, gateTcpProperties, gateCommonProperties))
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .bind(gateServer.getPort()).sync();
                    
            if (channelFuture.isSuccess()) {
                log.info("TcpGateDriver {} startGateServer success listener port {}", 
                        gateServer.getName(), gateServer.getPort());
                serverChannel = channelFuture.channel();
                isRunning = true;
            }
        } catch (Exception e) {
            log.error("TcpGateDriver name {} port {} startGateServer fail", 
                     gateServer.getName(), gateServer.getPort(), e);
        }
    }
}
```

**设计特点：**
- 基于 Netty 实现
- 支持 Epoll 和 NIO
- 可配置的线程池大小
- 支持自定义处理器链

### 5.2 服务器通道初始化器（ServerChanelInitializer）

```java
public class ServerChanelInitializer extends ChannelInitializer<SocketChannel> {
    private Tuple3<List<ChannelHandler>, List<AbstractGateLogicHandler>, List<ChannelHandler>> customHandlers;
    private GateTcpProperties gateTcpProperties;
    private GateCommonProperties gateCommonProperties;
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 添加解码器
        // 基于长度字段的帧解码器：按照消息头中的“长度字段”切分完整包，解决粘包/半包问题
        // 参数说明：
        // 1) maxFrameLength：单条消息允许的最大长度（超出将抛出 TooLongFrameException，用于防御性保护）
        // 2) lengthFieldOffset：长度字段在消息中的偏移（从报文起始算起，单位：字节）
        // 3) lengthFieldLength：长度字段所占字节数（常见为 2/4）
        // 4) lengthAdjustment：长度字段表示的长度到实际帧长度的修正值
        //    例如：长度字段仅统计“负载”长度，则需要 +（头部其余字节数）；若统计了整个消息，则为 0
        // 5) initialBytesToStrip：解码后从头部剥离掉的字节数（通常剥离长度字段本身，业务 Handler 只拿到净荷）
        pipeline.addLast("lengthFieldBasedFrameDecoder", 
                        new LengthFieldBasedFrameDecoder(
                            gateTcpProperties.getMaxFrameLength(),
                            gateTcpProperties.getLengthFieldOffset(),
                            gateTcpProperties.getLengthFieldLength(),
                            gateTcpProperties.getLengthAdjustment(),
                            gateTcpProperties.getInitialBytesToStrip()));
        
        // 添加编码器
        pipeline.addLast("lengthFieldPrepender", 
                        new LengthFieldPrepender(gateTcpProperties.getLengthFieldLength()));
        
        // 添加自定义处理器
        for (ChannelHandler handler : customHandlers.getT1()) {
            pipeline.addLast(handler);
        }
        
        // 添加业务处理器
        for (AbstractGateLogicHandler handler : customHandlers.getT2()) {
            pipeline.addLast(handler);
        }
        
        // 添加尾部处理器
        for (ChannelHandler handler : customHandlers.getT3()) {
            pipeline.addLast(handler);
        }
    }
}
```

### 5.3 抽象网关逻辑处理器（AbstractGateLogicHandler）

```java
@ChannelHandler.Sharable
@Slf4j
public abstract class AbstractGateLogicHandler<T> extends ChannelInboundHandlerAdapter {
    
    public abstract void doProcess(T data, GateClient gateClient, GateMessageHeader header) throws Exception;
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object source) throws Exception {
        GateMessagePackage messagePackage = (GateMessagePackage) source;
        GateMessageHeader header = messagePackage.getHeader();
        GateClient gateClient = ctx.channel().attr(TcpGateDriver.clientKey).get();
        
        ZipkinUtil.startScope(ZipkinUtil.newSRSpan(), 
                            span -> doProcess((T) messagePackage, gateClient, header), 
                            null);
    }
}
```

**设计特点：**
- 支持共享处理器
- 集成链路追踪
- 抽象业务处理逻辑

### 5.4 协议网关逻辑处理器（ProtoGateLogicHandler）

```java
@Slf4j
public class ProtoGateLogicHandler extends AbstractGateLogicHandler<GateMessagePackage> {
    
    @Override
    public void doProcess(GateMessagePackage data, GateClient gateClient, GateMessageHeader header) throws Exception {
        try {
            // 解析协议消息
            Any any = Any.parseFrom(data.getBody());
            
            // 根据消息类型分发处理
            switch (any.getTypeUrl()) {
                case "type.googleapis.com/LoginRequest":
                    handleLoginRequest(gateClient, any);
                    break;
                case "type.googleapis.com/GameRequest":
                    handleGameRequest(gateClient, any);
                    break;
                default:
                    log.warn("Unknown message type: {}", any.getTypeUrl());
            }
        } catch (Exception e) {
            log.error("Process message error", e);
            // 发送错误响应
            sendErrorResponse(gateClient, header.getMessageId(), e.getMessage());
        }
    }
    
    private void handleLoginRequest(GateClient client, Any any) {
        // 处理登录请求
    }
    
    private void handleGameRequest(GateClient client, Any any) {
        // 处理游戏请求
    }
}
```

### 5.5 JSON 网关逻辑处理器（FastJsonGateLogicHandler）

```java
@Slf4j
public class FastJsonGateLogicHandler extends AbstractGateLogicHandler<GateMessagePackage> {
    
    @Override
    public void doProcess(GateMessagePackage data, GateClient gateClient, GateMessageHeader header) throws Exception {
        try {
            // 解析 JSON 消息
            String jsonStr = new String(data.getBody(), StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(jsonStr);
            
            // 根据消息类型分发处理
            String messageType = jsonObject.getString("type");
            switch (messageType) {
                case "login":
                    handleLoginRequest(gateClient, jsonObject);
                    break;
                case "game":
                    handleGameRequest(gateClient, jsonObject);
                    break;
                default:
                    log.warn("Unknown message type: {}", messageType);
            }
        } catch (Exception e) {
            log.error("Process JSON message error", e);
            // 发送错误响应
            sendErrorResponse(gateClient, header.getMessageId(), e.getMessage());
        }
    }
}
```

## 6. 连接管理

### 6.1 连接生命周期管理

```java
public class ConnectionManager {
    private Map<String, GateClient> clientMap = new ConcurrentHashMap<>();
    private Map<String, Channel> channelMap = new ConcurrentHashMap<>();
    
    public void addClient(String clientId, GateClient client, Channel channel) {
        clientMap.put(clientId, client);
        channelMap.put(clientId, channel);
        
        // 触发连接事件
        gateServer.onClientConnect(client);
    }
    
    public void removeClient(String clientId) {
        GateClient client = clientMap.remove(clientId);
        Channel channel = channelMap.remove(clientId);
        
        if (client != null) {
            // 触发断开事件
            gateServer.onClientDisconnect(client);
        }
        
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }
    
    public GateClient getClient(String clientId) {
        return clientMap.get(clientId);
    }
}
```

### 6.2 连接状态监控

```java
public class ConnectionMonitor {
    private AtomicInteger connectionCount = new AtomicInteger(0);
    private AtomicLong totalConnections = new AtomicLong(0);
    private AtomicLong totalDisconnections = new AtomicLong(0);
    
    public void onClientConnect() {
        connectionCount.incrementAndGet();
        totalConnections.incrementAndGet();
    }
    
    public void onClientDisconnect() {
        connectionCount.decrementAndGet();
        totalDisconnections.incrementAndGet();
    }
    
    public int getCurrentConnectionCount() {
        return connectionCount.get();
    }
    
    public long getTotalConnections() {
        return totalConnections.get();
    }
    
    public long getTotalDisconnections() {
        return totalDisconnections.get();
    }
}
```

## 7. 消息路由

### 7.1 消息路由器（MessageRouter）

```java
public class MessageRouter {
    private Map<Short, MessageHandler> handlerMap = new ConcurrentHashMap<>();
    
    public void registerHandler(short messageType, MessageHandler handler) {
        handlerMap.put(messageType, handler);
    }
    
    public void routeMessage(GateClient client, GateMessagePackage message) {
        short messageType = message.getHeader().getMessageType();
        MessageHandler handler = handlerMap.get(messageType);
        
        if (handler != null) {
            handler.handleMessage(client, message);
        } else {
            log.warn("No handler found for message type: {}", messageType);
        }
    }
}
```

### 7.2 负载均衡

```java
public class LoadBalancer {
    private List<GateServer> servers = new ArrayList<>();
    private AtomicInteger currentIndex = new AtomicInteger(0);
    
    public GateServer selectServer() {
        if (servers.isEmpty()) {
            return null;
        }
        
        int index = currentIndex.getAndIncrement() % servers.size();
        return servers.get(index);
    }
    
    public void addServer(GateServer server) {
        servers.add(server);
    }
    
    public void removeServer(GateServer server) {
        servers.remove(server);
    }
}
```

## 8. 性能优化

### 8.1 连接池管理
- 连接复用
- 连接池大小控制
- 连接健康检查

### 8.2 消息处理优化
- 消息批处理
- 异步消息处理
- 消息压缩

### 8.3 内存管理
- 对象池化
- 内存泄漏检测
- 垃圾回收优化

## 9. 监控和运维

### 9.1 连接监控
- 连接数统计
- 连接状态监控
- 连接质量监控

### 9.2 消息监控
- 消息吞吐量
- 消息延迟
- 消息错误率

### 9.3 性能监控
- CPU 使用率
- 内存使用率
- 网络 I/O 监控

## 10. 配置管理

### 10.1 TCP 配置
```yaml
homo:
  gate:
    tcp:
      bossNum: 1
      workNum: 4
      maxFrameLength: 65536
      lengthFieldOffset: 0
      lengthFieldLength: 4
      lengthAdjustment: 0
      initialBytesToStrip: 4
```

### 10.2 通用配置
```yaml
homo:
  gate:
    common:
      maxConnections: 10000
      connectionTimeout: 30000
      messageQueueSize: 1000
      enableCompression: true
```

## 11. 使用示例

### 11.1 定义网关服务器

```java
@Component
public class GameGateServer implements GateServer {
    @Override
    public String getName() {
        return "game-gate";
    }
    
    @Override
    public int getPort() {
        return 8080;
    }
    
    @Override
    public void onClientConnect(GateClient client) {
        log.info("Client connected: {}", client.getId());
    }
    
    @Override
    public void onClientDisconnect(GateClient client) {
        log.info("Client disconnected: {}", client.getId());
    }
    
    @Override
    public void onClientMessage(GateClient client, byte[] message) {
        // 处理客户端消息
        processMessage(client, message);
    }
}
```

### 11.2 配置网关驱动

```java
@Configuration
public class GateConfig {
    @Bean
    public GateDriver gateDriver() {
        TcpGateDriver driver = new TcpGateDriver();
        driver.registerAfterHandler(new ProtoGateLogicHandler());
        return driver;
    }
}
```

## 12. 总结

`homo-core-gate` 模块通过提供统一的网关抽象和多种协议实现，为 homo-core 框架提供了强大的客户端连接管理能力。其灵活的处理器链设计和消息路由机制使得系统具有很好的扩展性，而连接管理和监控功能则确保了系统的高可用性。该模块的成功设计为整个框架的客户端通信奠定了坚实的基础。

---

## 13. 设计思想（补充）
- 轻逻辑重转发：网关仅做连接/编解码/路由/限流，业务尽量在后端服务。
- 会话上下文：`Channel.attr` 保存 userId/channel/version 等，进入业务前合并到 MDC。
- 背压优先：对单连接/全局限流，保护后端。

## 14. 连接与消息流程（ASCII）
```text
Client TCP → Netty pipeline(decoder→custom→logic→encoder)
  connect: GameGateServer.onClientConnect
  read: AbstractGateLogicHandler.channelRead → doProcess → route
  write: GateDriver.sendToClient
  close: GameGateServer.onClientDisconnect
```

## 15. 关键关系（ASCII）
```text
TcpGateDriver → ServerChanelInitializer → (handlers)
GateServer ⇄ GateClient
AbstractGateLogicHandler → MessageRouter → 后端 RPC/MQ
```

## 16. MDC 与安全
- 入站：提取 `sessionId/userId/ip/channel/version` → MDC。
- 出站：最小必要信息；严禁日志中输出敏感凭证。

## 17. Checklist
- [ ] pipeline 顺序：解码 → 自定义 → 业务 → 编码
- [ ] 连接数/速率/包长限制
- [ ] 异常断连清理 attr 与注册信息
