# RocketMQ Demo 项目

这是一个RocketMQ集成Demo项目，支持多集群配置和动态切换功能。

## 功能特性

### 1. 多集群支持
- 支持原始集群和多个业务集群（product、asset、operation、risk、base）
- 生产者支持根据消息类型路由到不同集群
- 消费者支持从不同集群消费消息

### 2. 动态开关切换
- **生产者开关**：控制是否开启双写功能（同时写入业务集群和原始集群）
- **消费者开关**：控制从业务集群还是原始集群消费消息
- **无需重启**：修改Redis中的开关值后，无需重启应用即可生效

### 3. 实时监控
- 定时监控服务每30秒检查一次开关状态变化
- 支持手动触发开关检查
- 支持启用/禁用监控功能

## API接口

### 生产者相关

#### 1. 发送消息
```bash
POST /api/mq/send/{topic}?tag={tag}&cluster={cluster}
Content-Type: application/json

{
  "content": "消息内容"
}
```

#### 2. 设置生产者开关（双写开关）
```bash
POST /api/mq/producer/switch?enabled=true
```

#### 3. 获取生产者开关状态
```bash
GET /api/mq/producer/switch
```

### 消费者相关

#### 1. 设置消费者开关
```bash
POST /api/mq/consumer/switch/{consumerId}/{topic}?enabled=true
```

#### 2. 获取消费者开关状态
```bash
GET /api/mq/consumer/switch/{consumerId}/{topic}
```

#### 3. 手动触发消费者开关检查
```bash
POST /api/mq/consumer/check-switch
```

#### 4. 启用/禁用消费者监控
```bash
POST /api/mq/consumer/monitor?enabled=true
```

#### 5. 获取消费者监控状态
```bash
GET /api/mq/consumer/monitor
```

#### 6. 停止特定消费者
```bash
POST /api/mq/consumer/shutdown/{consumerId}/{topic}
```

## 动态切换原理

### 生产者动态切换
生产者的动态切换相对简单，因为每次发送消息时都会实时读取Redis中的开关状态：
- 开关关闭：只发送到指定的业务集群
- 开关开启：发送到业务集群的同时，还会双写到原始集群

### 消费者动态切换
消费者的动态切换通过以下机制实现：

1. **状态记录**：系统记录每个消费者当前消费的集群类型
2. **定时检查**：每30秒检查一次Redis中的开关状态
3. **动态切换**：当检测到开关状态变化时：
   - 停止当前集群的消费者
   - 清理相关资源
   - 启动新集群的消费者
   - 更新状态记录

## 使用示例

### 1. 发送消息到product集群
```bash
curl -X POST "http://localhost:8080/api/mq/send/TOPIC_PRODUCT_TEST?cluster=product" \
  -H "Content-Type: application/json" \
  -d "测试消息内容"
```

### 2. 开启生产者双写
```bash
curl -X POST "http://localhost:8080/api/mq/producer/switch?enabled=true"
```

### 3. 切换消费者到业务集群
```bash
curl -X POST "http://localhost:8080/api/mq/consumer/switch/CID_PRODUCT_TEST/TOPIC_PRODUCT_TEST?enabled=true"
```

### 4. 手动触发消费者检查
```bash
curl -X POST "http://localhost:8080/api/mq/consumer/check-switch"
```

## 配置说明

### application.yml配置示例
```yaml
rocketmq:
  name-server: 148.150.20.133:9876;148.150.20.134:9876
  product-address: 148.150.20.135:9876;148.150.20.136:9876
  # ... 其他集群配置
  
  producer:
    group: PID_PRODUCT_TEST
    topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST
    topic-clusters: product;asset
    
  consumer:
    topics: TOPIC_PRODUCT_TEST;TOPIC_ASSET_TEST
    topic-clusters: product;asset
    group: CID_PRODUCT_TEST;CID_ASSET_TEST
  
  # 监控配置
  monitor:
    # 是否启用消费者开关监控（默认：true）
    enabled: true
    # 监控检查间隔时间，单位毫秒（默认：30000，即30秒）
    check-interval-ms: 30000
    # 是否在启动时立即执行一次检查（默认：true）
    initial-check: true
    # 监控检查的初始延迟时间，单位毫秒（默认：10000，即10秒）
    initial-delay-ms: 10000
```

### 监控配置说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `rocketmq.monitor.enabled` | boolean | true | 是否启用消费者开关监控 |
| `rocketmq.monitor.check-interval-ms` | long | 30000 | 定期检查间隔时间（毫秒） |
| `rocketmq.monitor.initial-check` | boolean | true | 启动时是否立即检查一次 |
| `rocketmq.monitor.initial-delay-ms` | long | 10000 | 初始延迟时间（毫秒） |

## 监控和运维

### 开关状态监控
系统提供了完整的监控能力：
- 定时检查：自动检查开关状态变化
- 手动触发：支持手动触发检查
- 监控开关：可以临时禁用监控功能

### 日志监控
系统会详细记录以下操作的日志：
- 消费者切换过程
- 开关状态变化
- 错误和异常情况

### 建议运维流程
1. 修改Redis中的开关值
2. 观察日志确认切换过程
3. 可选：手动触发检查加速切换
4. 验证消息收发是否正常

## 注意事项

1. **消费者切换期间**：在切换过程中可能会有短暂的消息处理中断
2. **资源管理**：系统会自动清理旧的消费者资源
3. **监控频率**：默认30秒检查一次，可根据需要调整
4. **错误恢复**：如果切换失败，会记录错误日志，不影响现有消费者 