package com.example.rocketmqdemo.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListener;
import org.apache.rocketmq.client.exception.MQClientException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RocketMQConsumerContainer {
    
    private static final String CONSUMER_SWITCH_KEY_PREFIX = "demo-mq:consumer:switch:";
    
    // 记录已创建的消费者实例，key为actualCluster_consumerGroup_topic
    private final ConcurrentHashMap<String, DefaultMQPushConsumer> consumerInstances = new ConcurrentHashMap<>();
    
    // 记录已订阅的主题，格式：consumerId_topic_tags
    private final ConcurrentHashMap<String, Boolean> subscribedTopics = new ConcurrentHashMap<>();
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private Map<String, DefaultMQPushConsumer> clusterConsumers;
    
    /**
     * 订阅主题并消费消息
     */
    public void subscribeAndConsume(String consumerGroup, String topic, String tags, 
                                   MessageListener messageListener, String clusterName) {
        try {
            // 参数验证
            if (consumerGroup == null || consumerGroup.trim().isEmpty()) {
                log.error("消费组ID不能为空");
                return;
            }
            
            if (topic == null || topic.trim().isEmpty()) {
                log.error("主题不能为空");
                return;
            }
            
            if (clusterName == null || clusterName.trim().isEmpty()) {
                log.error("集群名称不能为空");
                return;
            }
            
            // 使用默认的tags
            String actualTags = (tags == null || tags.trim().isEmpty()) ? "*" : tags;
            
            // 检查消息监听器是否为空
            if (messageListener == null) {
                log.error("消息监听器不能为空, consumerGroup: {}, topic: {}", consumerGroup, topic);
                return;
            }
            
            // 获取消费集群开关状态
            String value = redisTemplate.opsForValue().get(CONSUMER_SWITCH_KEY_PREFIX + consumerGroup + ":" + topic);
            boolean useBusinessCluster = Boolean.parseBoolean(value);
            
            // 根据开关状态决定从哪个集群消费
            String actualCluster = useBusinessCluster ? clusterName : "origin";
            
            log.info("订阅topic: {}, 消费者开关状态: {}, 实际消费集群: {}", topic, useBusinessCluster, actualCluster);
            
            // 创建唯一的消费者标识
            String consumerKey = actualCluster + "_" + consumerGroup + "_" + topic;
            
            // 检查是否已订阅主题
            String subscribeKey = consumerKey + "_" + actualTags;
            if (subscribedTopics.containsKey(subscribeKey)) {
                log.info("主题已订阅，跳过重复订阅，topic: {}, tags: {}, 集群类型: {}", topic, actualTags, actualCluster);
                return;
            }
            
            // 为每个 consumerGroup+topic 组合创建单独的消费者实例
            DefaultMQPushConsumer consumer = createNewConsumer(actualCluster, consumerGroup, topic);
            if (consumer == null) {
                log.error("创建消费者实例失败, 集群类型: {}, 消费组: {}", actualCluster, consumerGroup);
                return;
            }
            
            // 1. 先注册消息监听器
            consumer.registerMessageListener(messageListener);
            log.info("消息监听器已注册, 集群类型: {}, 消费组: {}", actualCluster, consumerGroup);
            
            // 2. 再订阅主题
            try {
                consumer.subscribe(topic, actualTags);
                log.info("成功订阅主题, topic: {}, tags: {}, 集群类型: {}", topic, actualTags, actualCluster);
                
                // 记录已订阅的主题
                subscribedTopics.put(subscribeKey, true);
                
                // 3. 最后启动消费者
                startConsumer(consumer, consumerKey);
            } catch (MQClientException e) {
                log.error("订阅主题失败, topic: {}, 错误: {}", topic, e.getMessage(), e);
                consumerInstances.remove(consumerKey);
            }
        } catch (Exception e) {
            log.error("处理消费者初始化时发生未知异常, 消费组: {}, topic: {}, 错误: {}", consumerGroup, topic, e.getMessage(), e);
        }
    }
    
    /**
     * 创建新的消费者实例
     */
    private DefaultMQPushConsumer createNewConsumer(String actualCluster, String consumerGroup, String topic) {
        String consumerKey = actualCluster + "_" + consumerGroup + "_" + topic;
        
        // 如果已经有创建的实例，直接返回
        if (consumerInstances.containsKey(consumerKey)) {
            return consumerInstances.get(consumerKey);
        }
        
        try {
            // 创建全新的消费者实例，而不是复用已有实例
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
            
            // 设置NameServer地址
            if ("origin".equals(actualCluster)) {
                // 从原集群的消费者获取NameServer地址
                DefaultMQPushConsumer templateConsumer = clusterConsumers.get("origin");
                if (templateConsumer != null) {
                    consumer.setNamesrvAddr(templateConsumer.getNamesrvAddr());
                } else {
                    log.error("无法获取原集群的NameServer地址");
                    return null;
                }
            } else {
                // 从业务集群消费者获取NameServer地址
                DefaultMQPushConsumer templateConsumer = clusterConsumers.get(actualCluster);
                if (templateConsumer != null) {
                    consumer.setNamesrvAddr(templateConsumer.getNamesrvAddr());
                } else {
                    log.error("无法获取{}集群的NameServer地址", actualCluster);
                    return null;
                }
            }
            
            // 设置消费者线程数
            consumer.setConsumeThreadMax(20);
            consumer.setConsumeThreadMin(10);
            
            // 将消费者实例加入缓存
            consumerInstances.put(consumerKey, consumer);
            
            return consumer;
        } catch (Exception e) {
            log.error("创建消费者失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 启动消费者
     */
    private void startConsumer(DefaultMQPushConsumer consumer, String consumerKey) {
        String startKey = consumerKey + "_started";
        if (subscribedTopics.containsKey(startKey)) {
            // 消费者已经启动过
            return;
        }
        
        try {
            // 启动消费者
            consumer.start();
            log.info("消费者已启动, key: {}", consumerKey);
            subscribedTopics.put(startKey, true);
        } catch (MQClientException e) {
            if (e.getErrorMessage() != null && e.getErrorMessage().contains("Started once")) {
                log.warn("消费者已经启动，忽略再次启动, key: {}", consumerKey);
                subscribedTopics.put(startKey, true);
            } else {
                log.error("启动消费者失败, key: {}, 错误: {}", consumerKey, e.getMessage(), e);
                // 从缓存中移除启动失败的消费者实例
                consumerInstances.remove(consumerKey);
            }
        }
    }
    
    /**
     * 设置消费者开关状态
     */
    public void setConsumerSwitch(String consumerGroup, String topic, boolean enabled) {
        String key = CONSUMER_SWITCH_KEY_PREFIX + consumerGroup + ":" + topic;
        redisTemplate.opsForValue().set(key, String.valueOf(enabled));
        log.info("设置消费者开关状态, 消费组: {}, topic: {}, 状态: {}", consumerGroup, topic, enabled);
    }
    
    /**
     * 获取消费者开关状态
     */
    public boolean getConsumerSwitch(String consumerGroup, String topic) {
        String key = CONSUMER_SWITCH_KEY_PREFIX + consumerGroup + ":" + topic;
        String value = redisTemplate.opsForValue().get(key);
        // 添加null检查，默认为false（使用原集群）
        return value != null && Boolean.parseBoolean(value);
    }
    
    /**
     * 初始化消费者开关状态（只在开关不存在时设置默认值）
     */
    public void initConsumerSwitch(String consumerGroup, String topic, boolean defaultEnabled) {
        String key = CONSUMER_SWITCH_KEY_PREFIX + consumerGroup + ":" + topic;
        try {
            Boolean keyExists = redisTemplate.hasKey(key);
            if (keyExists == null || !keyExists) {
                // 只在键不存在时设置默认值
                redisTemplate.opsForValue().set(key, String.valueOf(defaultEnabled));
                log.info("初始化消费者开关状态, 消费组: {}, topic: {}, 状态: {}", consumerGroup, topic, defaultEnabled);
            } else {
                String value = redisTemplate.opsForValue().get(key);
                log.info("消费者开关状态已存在, 消费组: {}, topic: {}, 当前状态: {}", consumerGroup, topic, value);
            }
        } catch (Exception e) {
            log.error("初始化消费者开关状态失败, 消费组: {}, topic: {}, 错误: {}", consumerGroup, topic, e.getMessage());
        }
    }
    
    /**
     * 停止所有消费者实例并清理资源
     */
    public void shutdown() {
        log.info("开始关闭所有消费者实例...");
        
        for (Map.Entry<String, DefaultMQPushConsumer> entry : consumerInstances.entrySet()) {
            try {
                String consumerKey = entry.getKey();
                DefaultMQPushConsumer consumer = entry.getValue();
                
                log.info("关闭消费者: {}", consumerKey);
                consumer.shutdown();
            } catch (Exception e) {
                log.error("关闭消费者实例时出错: {}", e.getMessage(), e);
            }
        }
        
        // 清空已注册的消费者和订阅记录
        consumerInstances.clear();
        subscribedTopics.clear();
        
        log.info("所有消费者实例已关闭");
    }
    
    /**
     * 停止特定主题的消费者
     */
    public void shutdownConsumer(String consumerGroup, String topic) {
        try {
            // 原始集群消费者
            String originKey = "origin_" + consumerGroup + "_" + topic;
            if (consumerInstances.containsKey(originKey)) {
                log.info("关闭原始集群消费者: {}", originKey);
                consumerInstances.get(originKey).shutdown();
                consumerInstances.remove(originKey);
            }
            
            // 业务集群消费者 - 遍历所有业务集群类型
            for (String clusterType : new String[]{"product", "asset", "operation", "risk", "base"}) {
                String businessKey = clusterType + "_" + consumerGroup + "_" + topic;
                if (consumerInstances.containsKey(businessKey)) {
                    log.info("关闭业务集群消费者: {}", businessKey);
                    consumerInstances.get(businessKey).shutdown();
                    consumerInstances.remove(businessKey);
                }
            }
            
            // 清理相关的订阅记录
            for (String key : new ArrayList<>(subscribedTopics.keySet())) {
                if (key.contains(consumerGroup) && key.contains(topic)) {
                    subscribedTopics.remove(key);
                }
            }
            
            log.info("已关闭 consumerGroup: {}, topic: {} 的所有消费者", consumerGroup, topic);
        } catch (Exception e) {
            log.error("关闭消费者时出错, consumerGroup: {}, topic: {}, 错误: {}", 
                    consumerGroup, topic, e.getMessage(), e);
        }
    }
} 