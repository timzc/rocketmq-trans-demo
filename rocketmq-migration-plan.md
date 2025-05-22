# RocketMQ集群切换与改造方案

## 1. 背景与目标

当前系统使用单一RocketMQ集群处理所有类型的消息。为了提高系统的可扩展性、消息处理的隔离性以及实现平滑迁移，计划将消息按业务类型拆分到不同的集群中。

## 2. 集群拆分方案

### 2.1 集群分类

将原有的单一集群拆分为以下几个业务集群：

- **Product集群**：处理产品相关消息
- **Asset集群**：处理资产相关消息
- **Operation集群**：处理运营相关消息
- **Risk集群**：处理风险相关消息
- **Base集群**：处理基础服务相关消息
- **Origin集群**：保留原有集群，作为消息双写和消费的备选集群

### 2.2 消息流转方案

![消息流转示意图](https://mermaid.ink/img/pako:eNp1kMFqwzAMhl_F6NRCXsA5dGxjg9FdejHxIWkUZ8GxM1uBUvLuU5wOStnQQfqF_n-RZhgcRWDB3JH5UfcRRjqdbfdqfyTfBu9o1nX-5MuSTiNR4qK1NqPfEm1V1WXQVXGhYJW5SGnv0g95W_lsrWn1n1MzZa_AKlbW_TIo3vfxgGkYRkUuaEKnZsR0CRpQYnZJ4zPCRfjsXHzQjnz9Q50F9H5b_Vgb6zG-XoBTHnmIWYnMiWKE2Dt0jGtIdU-pSJejthvYYo9p8sjVFTg8Xn8KLYKG_N_6J-GFMGGGNe7BQuwSBdiT1KftTeLB9A)

**双写策略**：
- 生产者发送消息时，根据生产者开关状态，决定是否进行双写
- 双写开启时，消息同时发送到原始集群和对应的业务集群
- 双写关闭时，消息仅发送到原始集群

**消费策略**：
- 消费者根据消费者开关状态，决定从哪个集群消费消息
- 消费开关开启时，从业务集群消费消息
- 消费开关关闭时，从原始集群消费消息

## 3. 代码改造方案

### 3.1 配置文件改造

在`application.yml`中增加`topicCluster`配置：

```yaml
server:
  port: 8080

spring:
  redis:
    cluster:
      nodes: 148.150.20.130:7000,148.150.20.130:7001,148.150.20.130:7002,148.150.20.130:7003,148.150.20.130:7004,148.150.20.130:7005
      timeout: 5000
      max-redirects: 3
    lettuce:
      pool:
        max-active: 8
        min-idle: 0
        max-wait: -1ms
    database: 0

mq:
  mqType: RocketMQ
  # 各集群地址配置
  origin-address: 148.150.20.133:9876;148.150.20.134:9876
  product-address: 148.150.20.135:9876;148.150.20.136:9876
  asset-address: 148.150.20.135:9876;148.150.20.136:9876
  operation-address: 148.150.20.135:9876;148.150.20.136:9876
  risk-address: 148.150.20.135:9876;148.150.20.136:9876
  base-address: 148.150.20.135:9876;148.150.20.136:9876
  
  producer:
    enable: true
    # Topic列表
    topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST;TOPIC_RISK_TEST
    # 每个Topic对应的业务集群类型
    topicClusters: product;asset;risk
    # 其他生产者配置
    sendMsgTimeout: 6000
    msgTypes: normal;normal;normal
    ids: PID_PRODUCT_TEST;PID_ASSET_TEST;PID_RISK_TEST
    checkImmunityTimeInSeconds: 10;10;10
  
  consumer:
    enable: true
    # Topic列表
    topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST;TOPIC_RISK_TEST
    # 每个Topic对应的业务集群类型
    topicClusters: product;asset;risk
    # 其他消费者配置
    msgTypes: normal;normal;normal
    ids: CID_PRODUCT_TEST;CID_ASSET_TEST;CID_RISK_TEST
    beans: demoMessageConsumer;demoMessageConsumer;demoMessageConsumer
    initSubExps: "*;*;*"
    subscribeTypes: false;false;false
    suspendTimeMillis: 1000;1000;1000
    maxReconsumeTimes: 20;20;20
    consumeThreadNums: 5;5;5
    consumeTimeouts: 15;15;15
```

### 3.2 Redis开关设计

在Redis中设置两类开关：

**生产者开关**：
- Key格式：`demo-mq:producer:switch`
- 值：`true/false`
- 功能：控制是否对指定topic进行双写

**消费者开关**：
- Key格式：`demo-mq:consumer:switch`
- 值：`true/false`
- 功能：控制指定消费组是否从业务集群消费消息

### 3.3 生产者改造

```java
@Component
public class RocketMQProducer {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private RocketMQTemplate originRocketMQTemplate;
    
    @Autowired
    private Map<String, RocketMQTemplate> clusterTemplates;  // 不同集群的模板
    
    public void sendMessage(String topic, String tag, Object message, String cluster) {
        // 获取双写开关状态
        Boolean dualWriteEnabled = redisTemplate.opsForValue()
            .get("mq:producer:switch:" + topic) != null ? 
            Boolean.parseBoolean(redisTemplate.opsForValue().get("mq:producer:switch:" + topic)) : 
            false;
        
        // 发送到原始集群
        originRocketMQTemplate.syncSend(topic + ":" + tag, message);
        
        // 如果开启双写，同时发送到业务集群
        if (dualWriteEnabled && clusterTemplates.containsKey(cluster)) {
            clusterTemplates.get(cluster).syncSend(topic + ":" + tag, message);
        }
    }
}
```

### 3.4 消费者改造

```java
@Component
public class RocketMQConsumerContainer {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private DefaultMQPushConsumer originConsumer;
    
    @Autowired
    private Map<String, DefaultMQPushConsumer> clusterConsumers;  // 不同集群的消费者
    
    public void subscribeAndConsume(String consumerGroup, String topic, String tags, MessageListener messageListener) {
        // 获取消费集群开关状态
        Boolean useBusinessCluster = redisTemplate.opsForValue()
            .get("mq:consumer:switch:" + consumerGroup + ":" + topic) != null ? 
            Boolean.parseBoolean(redisTemplate.opsForValue().get("mq:consumer:switch:" + consumerGroup + ":" + topic)) : 
            false;
        
        String clusterType = useBusinessCluster ? 
            environment.getProperty("rocketmq.consumer.topicCluster") : "origin";
        
        // 选择消费者
        DefaultMQPushConsumer consumer = "origin".equals(clusterType) ? 
            originConsumer : clusterConsumers.get(clusterType);
        
        // 订阅主题
        try {
            consumer.subscribe(topic, tags);
            consumer.registerMessageListener(messageListener);
            consumer.start();
        } catch (MQClientException e) {
            log.error("Subscribe topic {} failed", topic, e);
        }
    }
}
```

## 4. 迁移流程

1. **准备阶段**：
   - 部署新业务集群
   - 在配置中心添加新集群配置
   - 在Redis中设置开关，默认关闭状态

2. **双写阶段**：
   - 开启生产者双写开关，消息同时写入原集群和业务集群
   - 消费者仍从原集群消费，确保业务正常运行
   - 监控业务集群消息堆积情况，确保消息正确写入

3. **切换消费源**：
   - 开启消费者开关，逐步切换消费者从业务集群消费
   - 监控业务运行情况，确保消费正常
   - 如有异常，可通过关闭消费者开关快速回滚到原集群消费

4. **完成迁移**：
   - 确认所有消费者都已稳定从业务集群消费
   - 根据需要决定是否关闭双写，只向业务集群写入

## 5. 验证方案

实现一个demo程序，包含：

1. 模拟多个消息生产者，发送不同类型的消息
2. 模拟多个消费者，从不同集群消费消息
3. 测试Redis开关控制功能，验证双写和消费切换是否正常
4. 监控消息流转过程，确保无消息丢失

## 6. 风险与应对措施

1. **消息丢失风险**：
   - 保持双写状态一段时间，确保消息完整性
   - 实现消息补偿机制

2. **性能影响**：
   - 监控双写对系统性能的影响
   - 分批次、分时段进行迁移

3. **一致性问题**：
   - 确保消费者幂等性设计，避免重复消费问题 