# RocketMQ Migration Demo API 接口文档

本文档详细说明了 RocketMQ Migration Demo 项目中所有 API 接口的调用方式。

## 基础信息

- 基础路径: `/api/mq`
- 响应格式: JSON
- 所有接口响应格式为统一的结构:
  ```json
  {
    "success": true/false,  // 操作是否成功
    "message": "操作结果描述", // 操作结果描述或失败原因
    "data": {}             // 返回的数据，根据接口不同而变化
  }
  ```

## 数据模型

### MessageDTO

消息数据传输对象，用于表示发送到 RocketMQ 的消息内容。

| 字段        | 类型           | 描述                   |
|------------|----------------|----------------------|
| id         | String         | 消息的唯一标识符         |
| type       | String         | 消息类型，通常是集群名称   |
| content    | String         | 消息的实际内容           |
| businessId | String         | 业务ID，用于关联业务记录  |
| createTime | LocalDateTime  | 消息创建时间             |

## 业务组件说明

### 1. 消息生产者 (RocketMQProducer)

负责消息发送逻辑实现：
- 消息始终发送到请求指定的目标集群（业务集群或原集群）
- 当双写开关打开时，消息同时发送到原集群和业务集群
- 双写失败不影响主要发送结果

### 2. 消息消费者 (RocketMQConsumerContainer)

负责管理消费者实例和消费逻辑：
- 根据Redis开关状态选择从哪个集群消费消息
- 动态管理消费者与Topic的订阅关系
- 提供消费者切换功能

### 3. 消息处理 (DemoMessageConsumer)

实现具体的消息处理逻辑：
```java
@Slf4j
@Component
public class DemoMessageConsumer implements MessageListenerConcurrently {

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String topic = msg.getTopic();
                String tags = msg.getTags();
                String msgId = msg.getMsgId();
                String content = new String(msg.getBody(), StandardCharsets.UTF_8);
                
                log.info("接收到消息 - topic: {}, tags: {}, msgId: {}, content: {}", 
                        topic, tags, msgId, content);
                
                // 处理消息的业务逻辑...
                
            } catch (Exception e) {
                log.error("处理消息时发生异常", e);
                // 如果需要重试，返回RECONSUME_LATER
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        
        // 消费成功
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
```

## 接口列表

### 1. 发送消息接口

用于发送消息到指定的主题和业务集群。当生产者开关打开时，会同时将消息双写到原集群。

- **URL**: `/api/mq/send/{topic}`
- **方法**: POST
- **路径参数**:
  - `topic`: 消息主题名称，例如 `TOPIC_PRODUCT_TEST`

- **查询参数**:
  - `tag`: 消息标签（可选）
  - `cluster`: 目标业务集群类型，可选值: `product`, `asset`, `operation`, `risk`, `base`, `origin`

- **请求体**:
  消息内容（字符串格式）

- **curl 调用示例**:
  ```bash
  curl -X POST "http://localhost:8080/api/mq/send/TOPIC_PRODUCT_TEST?cluster=product&tag=tag1" \
       -H "Content-Type: application/json" \
       -d "\"这是一条测试消息\""
  ```

- **响应示例**:
  ```json
  {
    "success": true,
    "message": "消息发送成功",
    "data": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "type": "product",
      "content": "这是一条测试消息",
      "businessId": "550e8400-e29b-41d4-a716-446655440001",
      "createTime": "2023-07-01T10:15:30"
    },
    "targetCluster": "product",
    "dualWriteEnabled": true
  }
  ```

- **响应字段说明**:
  - `targetCluster`: 消息发送的目标集群
  - `dualWriteEnabled`: 是否启用了双写功能，true表示同时发送到原集群

### 2. 设置生产者开关接口

用于设置是否启用消息双写到原集群。

- **URL**: `/api/mq/producer/switch`
- **方法**: POST
- **查询参数**:
  - `enabled`: 开关状态，`true` 启用双写（同时发送到原集群），`false` 禁用双写（只发送到目标集群）

- **curl 调用示例**:
  ```bash
  curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=true"
  ```

- **响应示例**:
  ```json
  {
    "success": true,
    "message": "设置生产者开关成功",
    "data": true
  }
  ```

### 3. 获取生产者开关状态接口

用于查询当前生产者开关状态。

- **URL**: `/api/mq/producer/switch`
- **方法**: GET

- **curl 调用示例**:
  ```bash
  curl -X GET "http://localhost:8080/api/mq/producer/switch"
  ```

- **响应示例**:
  ```json
  {
    "success": true,
    "message": "获取生产者开关状态成功",
    "data": true
  }
  ```

### 4. 设置消费者开关接口

用于设置特定消费组和主题的消费者开关，控制是否从业务集群消费消息。

- **URL**: `/api/mq/consumer/switch/{consumerId}/{topic}`
- **方法**: POST
- **路径参数**:
  - `consumerId`: 消费者组ID，例如 `CID_PRODUCT_TEST`
  - `topic`: 主题名称，例如 `TOPIC_PRODUCT_TEST`

- **查询参数**:
  - `enabled`: 开关状态，`true` 从业务集群消费，`false` 从原集群消费

- **curl 调用示例**:
  ```bash
  curl -X POST "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST?enabled=true"
  ```

- **响应示例**:
  ```json
  {
    "success": true,
    "message": "设置消费者开关成功",
    "data": true
  }
  ```

### 5. 获取消费者开关状态接口

用于查询特定消费组和主题的消费者开关状态。

- **URL**: `/api/mq/consumer/switch/{consumerId}/{topic}`
- **方法**: GET
- **路径参数**:
  - `consumerId`: 消费者组ID，例如 `CID_PRODUCT_TEST`
  - `topic`: 主题名称，例如 `TOPIC_PRODUCT_TEST`

- **curl 调用示例**:
  ```bash
  curl -X GET "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST"
  ```

- **响应示例**:
  ```json
  {
    "success": true,
    "message": "获取消费者开关状态成功",
    "data": false
  }
  ```

## 错误处理

所有接口在发生异常时会返回统一格式的错误响应:

```json
{
  "success": false,
  "message": "错误描述信息，例如：设置生产者开关失败: Connection refused",
  "data": null
}
```

## 接口使用流程示例

以下是一个完整的消息迁移流程示例（使用curl命令）:

1. 首先发送消息到业务集群（默认无双写）:
   ```bash
   curl -X POST "http://localhost:8080/api/mq/send/TOPIC_PRODUCT_TEST?cluster=product" \
        -H "Content-Type: application/json" \
        -d "\"测试消息1\""
   ```
   响应中 `targetCluster` 显示为 "product"，`dualWriteEnabled` 为 false，表明消息仅发送到了业务集群。

2. 查看生产者开关状态（默认为关闭）:
   ```bash
   curl -X GET "http://localhost:8080/api/mq/producer/switch"
   ```

3. 开启生产者双写:
   ```bash
   curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=true"
   ```

4. 再次发送消息（此时消息发送到业务集群并双写到原集群）:
   ```bash
   curl -X POST "http://localhost:8080/api/mq/send/TOPIC_PRODUCT_TEST?cluster=product" \
        -H "Content-Type: application/json" \
        -d "\"测试消息2\""
   ```
   响应中 `targetCluster` 显示为 "product"，`dualWriteEnabled` 为 true，表明消息发送到了业务集群并双写到了原集群。

5. 查看消费者开关状态（默认从原集群消费）:
   ```bash
   curl -X GET "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST"
   ```

6. 开启消费者从业务集群消费:
   ```bash
   curl -X POST "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST?enabled=true"
   ```

7. 验证消费正常后，可以完成迁移。如需回滚，可以关闭消费者开关和生产者开关:
   ```bash
   curl -X POST "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST?enabled=false"
   curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=false"
   ```

## 配置说明

项目使用 application.yml 文件进行配置，主要配置项包括：

### 1. RocketMQ 集群配置

```yaml
mq:
  mqType: RocketMQ
  # 各集群地址配置
  origin-address: 148.150.20.133:9876;148.150.20.134:9876
  product-address: 148.150.20.135:9876;148.150.20.136:9876
  asset-address: 148.150.20.135:9876;148.150.20.136:9876
  operation-address: 148.150.20.135:9876;148.150.20.136:9876
  risk-address: 148.150.20.135:9876;148.150.20.136:9876
  base-address: 148.150.20.135:9876;148.150.20.136:9876
```

### 2. 生产者配置

```yaml
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
```

### 3. 消费者配置

```yaml
consumer:
  enable: true
  # Topic列表
  topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST;TOPIC_RISK_TEST
  # 每个Topic对应的业务集群类型
  topicClusters: product;asset;risk
  # 其他消费者配置
  ids: CID_PRODUCT_TEST;CID_ASSET_TEST;CID_RISK_TEST
  initSubExps: "*;*;*"
```

## Redis 开关说明

系统使用 Redis 中的键值对存储开关状态：

### 1. 生产者开关
- **键名**: `demo-mq:producer:switch`
- **值**: 布尔值（字符串形式的 "true" 或 "false"）
- **默认值**: "false"（仅向目标集群发送消息）
- **作用**: 控制是否同时将消息发送到原集群（双写）
- **注意**: 修改开关后，需要重启服务才能生效，开关值会持久化在Redis中

### 2. 消费者开关
- **键名**: `demo-mq:consumer:switch:{consumerGroup}:{topic}`
- **值**: 布尔值（字符串形式的 "true" 或 "false"）
- **默认值**: "false"（从原集群消费消息）
- **作用**: 控制特定消费组和主题是否从业务集群消费消息
- **注意**: 修改开关后，需要重启服务才能生效，开关值会持久化在Redis中