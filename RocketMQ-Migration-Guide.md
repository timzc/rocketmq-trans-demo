# RocketMQ 双写改造指导手册

## 📋 概述

本指导手册基于RocketMQ Demo项目，为需要进行类似RocketMQ双写改造的应用程序提供完整的架构说明和实施指导。

## 🏗️ 系统架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                          应用层                                    │
├─────────────────────────────────────────────────────────────────┤
│  Controller      │  Service         │  Producer/Consumer          │
│  MQController    │  业务服务        │  RocketMQProducer          │
│                  │                  │  RocketMQConsumerContainer  │
├─────────────────────────────────────────────────────────────────┤
│                        配置与管理层                                │
│  MQProperties    │  RocketMQConfig  │  ConsumerSwitchMonitor     │
│  MQInitService   │  RedisConfig     │                            │
├─────────────────────────────────────────────────────────────────┤
│                        中间件层                                   │
│  Redis集群                │  RocketMQ集群                         │
│  ├─ 开关状态存储           │  ├─ 原始集群                           │
│  └─ 配置管理              │  ├─ Product集群                        │
│                          │  ├─ Asset集群                          │
│                          │  ├─ Operation集群                      │
│                          │  ├─ Risk集群                           │
│                          │  └─ Base集群                           │
└─────────────────────────────────────────────────────────────────┘
```

### 核心组件架构

```
┌──────────────────┐    ┌────────────────┐    ┌─────────────────┐
│   生产者架构      │    │   消费者架构    │    │   监控架构       │
│                  │    │                │    │                 │
│ RocketMQProducer │    │RocketMQConsumer│    │ConsumerSwitch   │
│      ↓           │    │   Container    │    │   Monitor       │
│ 读取Redis开关     │    │      ↓         │    │      ↓          │
│      ↓           │    │ 动态切换集群    │    │ 定时检查开关     │
│ 业务集群发送      │    │      ↓         │    │      ↓          │
│      ↓           │    │ 资源管理       │    │ 触发集群切换     │
│ 双写判断         │    │                │    │                 │
│      ↓           │    │                │    │                 │
│ 原始集群双写      │    │                │    │                 │
└──────────────────┘    └────────────────┘    └─────────────────┘
```

## 🔧 核心组件说明

### 1. 配置管理组件

#### MQProperties.java
**作用**: 统一管理所有MQ相关配置
**必需**: ✅ 必须引入

```java
@Component("mqProperties")
@ConfigurationProperties(prefix = "rocketmq")
public class MQProperties {
    // 集群地址配置
    private String nameServer;
    private String productAddress;
    private String assetAddress;
    // ... 其他集群地址
    
    // 生产者配置
    private Producer producer;
    
    // 消费者配置
    private Consumer consumer;
    
    // 监控配置
    private Monitor monitor;
}
```

#### RocketMQConfig.java
**作用**: 创建多集群RocketMQ实例
**必需**: ✅ 必须引入

```java
@Configuration
public class RocketMQConfig {
    // 创建原始集群模板
    @Bean @Primary
    public RocketMQTemplate originRocketMQTemplate();
    
    // 创建各业务集群模板
    @Bean public RocketMQTemplate productRocketMQTemplate();
    @Bean public RocketMQTemplate assetRocketMQTemplate();
    // ... 其他业务集群模板
    
    // 创建集群模板映射
    @Bean public Map<String, RocketMQTemplate> clusterTemplates();
}
```

### 2. 生产者组件

#### RocketMQProducer.java
**作用**: 实现双写逻辑的消息生产者
**必需**: ✅ 必须引入

**关键特性**:
- 实时读取Redis开关状态
- 支持业务集群路由
- 自动双写到原始集群
- 完善的错误处理

```java
@Component
public class RocketMQProducer {
    public boolean sendMessage(String topic, String tag, Object message, String cluster) {
        // 1. 读取Redis开关状态
        boolean dualWriteEnabled = Boolean.parseBoolean(
            redisTemplate.opsForValue().get(PRODUCER_SWITCH_KEY));
        
        // 2. 发送到业务集群
        // ... 业务集群发送逻辑
        
        // 3. 根据开关决定是否双写
        if (dualWriteEnabled && !"origin".equals(cluster)) {
            // 双写到原始集群
        }
    }
}
```

### 3. 消费者组件

#### RocketMQConsumerContainer.java
**作用**: 动态管理消费者实例和集群切换
**必需**: ✅ 必须引入

**关键特性**:
- 动态切换消费集群
- 消费者实例生命周期管理
- 资源自动清理

#### DemoMessageConsumer.java
**作用**: 消息处理实现
**必需**: 🔄 需要根据业务自定义

### 4. 监控组件

#### ConsumerSwitchMonitor.java
**作用**: 定时监控开关状态变化
**必需**: ✅ 推荐引入

**关键特性**:
- 配置化检查间隔
- 支持启用/禁用监控
- 手动触发检查

#### MQInitService.java
**作用**: 应用启动时初始化MQ组件
**必需**: ✅ 必须引入

### 5. 控制接口

#### MQController.java
**作用**: 提供HTTP接口管理开关和测试
**必需**: 🔄 可选，推荐用于运维

## 📝 改造实施指导

### 第一步：依赖准备

#### 1.1 Maven依赖
```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- RocketMQ Spring Boot Starter -->
    <dependency>
        <groupId>org.apache.rocketmq</groupId>
        <artifactId>rocketmq-spring-boot-starter</artifactId>
        <version>2.2.3</version>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

#### 1.2 启用定时任务
```java
@SpringBootApplication
@EnableScheduling  // 启用定时任务支持
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 第二步：配置文件

#### 2.1 application.yml配置
```yaml
# Redis配置
spring:
  redis:
    cluster:
      nodes: your-redis-cluster-nodes
      timeout: 5000
      max-redirects: 3

# RocketMQ配置
rocketmq:
  # 原始集群
  name-server: original-cluster-nameservers
  
  # 业务集群地址
  product-address: product-cluster-nameservers
  asset-address: asset-cluster-nameservers
  operation-address: operation-cluster-nameservers
  risk-address: risk-cluster-nameservers
  base-address: base-cluster-nameservers
  
  # 生产者配置
  producer:
    group: YOUR_PRODUCER_GROUP
    send-message-timeout: 6000
    enable: true
    topics: YOUR_TOPIC_LIST
    topic-clusters: product;asset;risk;operation;base
    
  # 消费者配置
  consumer:
    enable: true
    topics: YOUR_TOPIC_LIST
    topic-clusters: product;asset;risk;operation;base
    group: YOUR_CONSUMER_GROUPS
    init-sub-exps: "*;*;*"
    
  # 监控配置
  monitor:
    enabled: true
    check-interval-ms: 30000
    initial-check: true
    initial-delay-ms: 10000
```

### 第三步：核心代码引入

#### 3.1 必须复制的文件清单

```
📁 config/
├── 📄 MQProperties.java           ✅ 必须
├── 📄 RocketMQConfig.java         ✅ 必须
├── 📄 MQInitService.java          ✅ 必须
├── 📄 ConsumerSwitchMonitor.java  ✅ 推荐
└── 📄 RedisConfig.java            🔄 根据需要

📁 producer/
└── 📄 RocketMQProducer.java       ✅ 必须

📁 consumer/
├── 📄 RocketMQConsumerContainer.java  ✅ 必须
└── 📄 DemoMessageConsumer.java        🔄 自定义实现

📁 controller/
└── 📄 MQController.java               🔄 可选

📁 model/
└── 📄 MessageDTO.java                 🔄 根据需要
```

#### 3.2 关键修改点

**A. 集群地址配置**
- 修改 `MQProperties.java` 中的集群地址属性
- 根据实际集群调整 `RocketMQConfig.java` 中的模板创建

**B. 消息处理逻辑**
- 实现自己的 `MessageListenerConcurrently`
- 替换 `DemoMessageConsumer.java` 为实际业务逻辑

**C. 业务适配**
- 调整 `MessageDTO.java` 为实际消息模型
- 修改生产者调用方式

### 第四步：集成验证

#### 4.1 启动检查
```bash
# 1. 检查应用启动日志
grep "RocketMQProducer初始化" application.log
grep "消费者开关监控服务已启动" application.log

# 2. 检查Redis连接
curl http://localhost:8080/api/mq/producer/switch

# 3. 检查集群模板
grep "可用的RocketMQTemplate列表" application.log
```

#### 4.2 功能测试
```bash
# 1. 测试消息发送
curl -X POST "http://localhost:8080/api/mq/send/TEST_TOPIC?cluster=product" \
  -H "Content-Type: application/json" \
  -d "测试消息"

# 2. 测试开关切换
curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=true"

# 3. 测试消费者切换
curl -X POST "http://localhost:8080/api/mq/consumer/switch/TEST_GROUP/TEST_TOPIC?enabled=true"
```

## 🚀 最佳实践

### 1. 渐进式改造策略

```
阶段1: 准备阶段
├── 搭建业务集群
├── 配置Redis集群
└── 部署改造后的应用

阶段2: 生产者双写
├── 开启生产者开关
├── 验证消息双写
└── 监控消息堆积

阶段3: 消费者切换
├── 开启消费者开关
├── 验证消费正常
└── 监控消费延迟

阶段4: 完全切换
├── 关闭原集群消费
├── 下线原集群生产
└── 清理原集群资源
```

### 2. 监控要点

#### 2.1 关键指标
- **消息发送成功率**: 监控生产者发送状态
- **消息堆积量**: 监控各集群topic堆积
- **消费延迟**: 监控消费者处理延迟
- **开关状态**: 监控Redis开关变化

#### 2.2 告警设置
```yaml
# 建议告警配置
alerts:
  - name: 消息发送失败率过高
    condition: failure_rate > 1%
    
  - name: 消息堆积严重
    condition: message_backlog > 10000
    
  - name: 消费延迟过高
    condition: consume_delay > 30s
    
  - name: 开关状态异常
    condition: switch_check_failed
```

### 3. 应急预案

#### 3.1 回滚策略
```bash
# 紧急回滚生产者
curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=false"

# 紧急回滚消费者
curl -X POST "http://localhost:8080/api/mq/consumer/switch/{group}/{topic}?enabled=false"

# 手动触发检查
curl -X POST "http://localhost:8080/api/mq/consumer/check-switch"
```

#### 3.2 故障处理
```bash
# 1. 检查Redis连接
redis-cli -c -h redis-cluster-host ping

# 2. 检查RocketMQ连接
telnet rocketmq-nameserver-host 9876

# 3. 重启消费者监控
curl -X POST "http://localhost:8080/api/mq/consumer/monitor?enabled=false"
sleep 5
curl -X POST "http://localhost:8080/api/mq/consumer/monitor?enabled=true"
```

## 📚 FAQ

### Q1: 是否支持事务消息？
**A**: 当前版本主要针对普通消息，事务消息需要额外适配事务监听器。

### Q2: 消费者切换期间会丢消息吗？
**A**: 切换期间可能有短暂中断，但不会丢消息。RocketMQ会保证消息的可靠性。

### Q3: 可以自定义检查间隔吗？
**A**: 可以，通过修改 `rocketmq.monitor.check-interval-ms` 配置项。

### Q4: 是否支持顺序消息？
**A**: 支持，但需要确保业务集群的队列数量和原集群保持一致。

### Q5: 如何处理集群故障？
**A**: 系统会自动记录错误日志，可以通过API接口手动切换到正常集群。

## 📞 技术支持

如果在改造过程中遇到问题，请检查以下几点：

1. **配置检查**: 确认所有集群地址和组名配置正确
2. **依赖检查**: 确认所有必需的Maven依赖已添加
3. **网络检查**: 确认应用可以访问所有RocketMQ集群和Redis
4. **日志检查**: 查看应用启动日志和运行日志
5. **权限检查**: 确认应用有权限访问所有集群资源

---

*本指导手册基于实际生产环境验证，提供了完整的RocketMQ双写改造方案。* 