# RocketMQ Migration Demo

本项目是一个RocketMQ集群切换与改造的演示程序，用于验证改造方案的可行性。

## 方案概述

1. 将原有的RocketMQ集群按照消息类型分成多个业务集群:
   - Product集群：处理产品相关消息
   - Asset集群：处理资产相关消息
   - Operation集群：处理运营相关消息
   - Risk集群：处理风险相关消息
   - Base集群：处理基础服务相关消息
   - Origin集群：保留原集群用于过渡期双写和消费

2. 使用Redis开关控制消息流向:
   - 生产者开关：控制是否双写消息到业务集群
   - 消费者开关：控制是否从业务集群消费消息

## 系统架构

整个系统采用Spring Boot框架实现，主要包含以下核心组件：

### 1. 配置管理
- `MQProperties`：映射application.yml中的MQ配置，包括集群地址和各类参数
- `RocketMQConfig`：创建并管理各业务集群的生产者和消费者实例
- `RedisConfig`：Redis配置，用于开关状态存储
- `MQInitService`：初始化服务，负责设置开关默认值和初始化生产者/消费者

### 2. 生产者组件
- `RocketMQProducer`：核心生产者类，实现了消息发送和双写逻辑
  - 根据Redis开关状态决定是否双写消息
  - 原集群保持消息发送
  - 业务集群在开关打开时接收消息
  - 增强错误处理，记录发送失败情况

### 3. 消费者组件
- `RocketMQConsumerContainer`：消费者容器，管理消费者实例和消费逻辑
  - 根据Redis开关状态选择从哪个集群消费消息
  - 动态管理消费者与Topic的订阅关系
- `DemoMessageConsumer`：消息处理实现，处理收到的消息

### 4. 开关控制
- Redis开关：使用Redis存储生产者和消费者的开关状态
  - 生产者开关：`demo-mq:producer:switch`，默认值为`false`（只写原集群）
  - 消费者开关：`demo-mq:consumer:switch:{consumerGroup}:{topicName}`，默认值为`false`（从原集群消费）

### 5. API接口
- `MQController`：提供HTTP接口用于测试和控制开关
  - 消息发送接口
  - 开关控制接口

### 6. 消息流转流程

```
                    ┌─────────────────┐
                    │  Redis Switches │
                    └─────────┬───────┘
                              │ 控制
                              ▼
┌───────────┐    消息    ┌────────────┐     双写(开关ON)   ┌──────────────┐
│ 生产者应用 ├───────────►│ Origin集群 │──────────────────►│ 业务MQ集群    │
└───────────┘            └──────┬─────┘                   └───────┬──────┘
                                │                                 │
                                │开关OFF                          │开关ON
                                ▼                                 ▼
                         ┌─────────────┐                  ┌─────────────┐
                         │ 消费者应用   │◄─────────────────┤ 消费者应用  │
                         └─────────────┘                  └─────────────┘
```

### 7. 目录结构

```
src/main/java/com/example/rocketmqdemo/
├── RocketMqDemoApplication.java            # 程序入口
├── config/                                 # 配置类
│   ├── MQProperties.java                   # MQ配置属性
│   ├── RocketMQConfig.java                 # RocketMQ实例配置
│   ├── RedisConfig.java                    # Redis配置
│   └── MQInitService.java                  # MQ初始化服务
├── producer/
│   └── RocketMQProducer.java               # 消息生产者
├── consumer/
│   ├── RocketMQConsumerContainer.java      # 消费者容器
│   └── DemoMessageConsumer.java            # 消息消费实现
├── model/
│   └── MessageDTO.java                     # 消息数据模型
└── controller/
    └── MQController.java                   # API控制器
```

## 配置说明

配置文件`application.yml`中包含以下主要配置项:

```yaml
spring:
  redis:
    cluster:
      nodes: 148.150.20.130:7000,148.150.20.130:7001 # Redis集群节点
      timeout: 5000
      max-redirects: 3
    lettuce:
      pool:
        max-active: 8
        min-idle: 0
        max-wait: -1ms

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
    # 其他生产者配置...
  
  consumer:
    enable: true
    # Topic列表
    topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST;TOPIC_RISK_TEST
    # 每个Topic对应的业务集群类型
    topicClusters: product;asset;risk
    # 其他消费者配置...
```

## Redis开关

程序使用Redis中的两个开关控制消息流向:

1. 生产者开关: `demo-mq:producer:switch`
   - `true`: 向业务集群和原集群双写消息
   - `false`: 只向原集群写消息（**默认值**）

2. 消费者开关: `demo-mq:consumer:switch:{consumerGroup}:{topicName}`
   - `true`: 从业务集群消费消息
   - `false`: 从原集群消费消息（**默认值**）

所有开关在应用启动时通过`MQInitService`初始化为默认值（`false`）。

## 错误处理

系统增强了错误处理机制：

1. 消息发送错误处理：
   - 发送消息到原集群失败或异常会记录到日志
   - 发送消息到业务集群失败或异常会记录到日志
   - 返回发送状态以便调用方处理

2. 开关状态读取：
   - 增加了空值检查，避免NullPointerException
   - 如果Redis中没有对应的开关值，默认使用`false`（原集群）

## 测试方法

### 1. 使用真实Redis

如果使用真实的Redis环境，直接配置`application.yml`中的Redis集群地址即可。

### 2. 使用内嵌Redis测试

如果需要在本地测试环境中运行而没有Redis服务，可以使用内嵌Redis进行测试：

1. 添加内嵌Redis依赖:

```xml
<dependency>
    <groupId>it.ozimov</groupId>
    <artifactId>embedded-redis</artifactId>
    <version>0.7.3</version>
    <scope>test</scope>
</dependency>
```

2. 创建内嵌Redis配置类:

```java
@Configuration
@Profile("test") // 只在test环境中激活
public class EmbeddedRedisConfig {
    private RedisServer redisServer;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory();
        connectionFactory.setPort(redisPort);
        connectionFactory.setHostName("localhost");
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }
    
    @PostConstruct
    public void startRedis() throws IOException {
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }
    
    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}
```

3. 修改application.yml或创建application-test.yml:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

4. 使用test环境运行应用:

```bash
java -jar -Dspring.profiles.active=test target/rocketmq-demo-0.0.1-SNAPSHOT.jar
```

## 接口说明

系统提供以下HTTP接口用于测试和控制:

### 1. 发送消息

```
POST /api/mq/send/{topic}?tag={tag}&cluster={cluster}
Content-Type: application/json

{
  "content": "消息内容"
}
```

### 2. 设置生产者开关

```
POST /api/mq/producer/switch?enabled={true|false}
```

### 3. 获取生产者开关状态

```
GET /api/mq/producer/switch
```

### 4. 设置消费者开关

```
POST /api/mq/consumer/switch/{consumerId}/{topic}?enabled={true|false}
```

### 5. 获取消费者开关状态

```
GET /api/mq/consumer/switch/{consumerId}/{topic}
```

## 迁移流程演示

1. 首先发送消息到原集群:
   ```
   POST /api/mq/send/TOPIC_PRODUCT_TEST?cluster=product
   ```

2. 开启生产者双写:
   ```
   POST /api/mq/producer/switch?enabled=true
   ```

3. 再次发送消息(将同时发往原集群和业务集群):
   ```
   POST /api/mq/send/TOPIC_PRODUCT_TEST?cluster=product
   ```

4. 开启消费者切换到业务集群:
   ```
   POST /api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST?enabled=true
   ```

5. 观察日志确认消息消费正常

## 运行项目

```bash
# 编译
mvn clean package

# 运行
java -jar target/rocketmq-demo-0.0.1-SNAPSHOT.jar
``` 