package com.example.rocketmqdemo.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RocketMQProducer {
    
    private static final String PRODUCER_SWITCH_KEY = "demo-mq:producer:switch";
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private RocketMQTemplate originRocketMQTemplate;
    
    @Autowired
    private Map<String, RocketMQTemplate> clusterTemplates;
    
    /**
     * 初始化生产者开关状态（默认关闭，只写入原集群）
     */
    @javax.annotation.PostConstruct
    public void init() {
        log.info("RocketMQProducer初始化...");
        
        // 打印当前可用的集群模板
        log.info("可用的RocketMQTemplate列表: {}", clusterTemplates.keySet());
        
        // 检查是否存在业务集群模板
        boolean hasBusinessClusters = false;
        for (String key : clusterTemplates.keySet()) {
            if (key.contains("product") || key.contains("asset") || key.contains("operation") || 
                key.contains("risk") || key.contains("base")) {
                hasBusinessClusters = true;
                break;
            }
        }
        
        if (!hasBusinessClusters) {
            log.warn("未找到任何业务集群模板，消息可能无法正确路由到业务集群");
        }
        
        // 初始化生产者开关
        initProducerSwitch();
    }
    
    /**
     * 初始化生产者开关状态
     */
    public void initProducerSwitch() {
        try {
            Boolean keyExists = redisTemplate.hasKey(PRODUCER_SWITCH_KEY);
            if (keyExists == null || !keyExists) {
                // 只在键不存在时设置默认值
                redisTemplate.opsForValue().set(PRODUCER_SWITCH_KEY, "false");
                log.info("初始化生产者开关状态, 状态: false");
            } else {
                String value = redisTemplate.opsForValue().get(PRODUCER_SWITCH_KEY);
                log.info("生产者开关状态已存在, 当前状态: {}", value);
            }
        } catch (Exception e) {
            log.error("初始化生产者开关状态失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送消息
     * @param topic 主题
     * @param tag 标签
     * @param message 消息内容
     * @param cluster 业务集群类型
     * @return 发送结果，true表示成功，false表示失败
     */
    public boolean sendMessage(String topic, String tag, Object message, String cluster) {
        boolean success = true;
        
        // 构建目标topic
        String destination = topic;
        if (tag != null && !tag.isEmpty()) {
            destination = topic + ":" + tag;
        }
        
        // 获取双写开关状态
        boolean dualWriteEnabled = Boolean.parseBoolean(redisTemplate.opsForValue().get(PRODUCER_SWITCH_KEY));
        
        log.info("发送消息到topic: {}, 双写开关状态: {}, 目标集群: {}", destination, dualWriteEnabled, cluster);
        
        // 打印当前可用的集群列表，帮助调试
        log.info("当前可用的业务集群列表: {}", clusterTemplates.keySet());
        
        // 尝试获取并使用正确的模板
        RocketMQTemplate targetTemplate = null;
        
        // 根据集群名称确定使用哪个模板
        if ("origin".equals(cluster)) {
            // 直接使用原始集群模板
            targetTemplate = originRocketMQTemplate;
            log.info("使用原始集群模板");
        } else {
            // 检查是否有以集群名命名的RocketMQTemplate
            String templateBeanName = cluster + "RocketMQTemplate";
            log.info("尝试查找模板: {}, 可用模板: {}", templateBeanName, clusterTemplates.keySet());
            
            if (clusterTemplates.containsKey(templateBeanName)) {
                targetTemplate = clusterTemplates.get(templateBeanName);
                log.info("使用业务集群模板: {}", templateBeanName);
            } else if (clusterTemplates.containsKey(cluster)) {
                targetTemplate = clusterTemplates.get(cluster);
                log.info("使用业务集群模板: {}", cluster);
            } else {
                log.error("未知的集群类型: {}, 可用模板: {}", cluster, clusterTemplates.keySet());
                success = false;
                return success;
            }
        }
        
        // 检查模板是否为空
        if (targetTemplate == null) {
            log.error("集群 {} 的RocketMQTemplate为null", cluster);
            success = false;
            return success;
        }
        
        // 发送消息到目标集群
        try {
            SendResult clusterResult = targetTemplate.syncSend(destination, MessageBuilder.withPayload(message).build());
            if (clusterResult.getSendStatus() != SendStatus.SEND_OK) {
                log.error("消息发送到{}集群失败, topic: {}, status: {}", cluster, destination, clusterResult.getSendStatus());
                success = false;
            } else {
                log.info("消息已发送到{}集群, topic: {}, msgId: {}", cluster, destination, clusterResult.getMsgId());
            }
        } catch (Exception e) {
            log.error("消息发送到{}集群异常, topic: {}, 错误: {}", cluster, destination, e.getMessage(), e);
            success = false;
        }
        
        // 如果开启双写，同时发送到原始集群（除非目标本身就是原始集群）
        if (dualWriteEnabled && !"origin".equals(cluster)) {
            try {
                SendResult originResult = originRocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(message).build());
                if (originResult.getSendStatus() != SendStatus.SEND_OK) {
                    log.error("双写到原始集群失败, topic: {}, status: {}", destination, originResult.getSendStatus());
                    // 双写失败不影响主要的发送结果
                } else {
                    log.info("消息已双写到原始集群, topic: {}, msgId: {}", destination, originResult.getMsgId());
                }
            } catch (Exception e) {
                log.error("双写到原始集群异常, topic: {}, 错误: {}", destination, e.getMessage(), e);
                // 双写失败不影响主要的发送结果
            }
        }
        
        return success;
    }
    
    /**
     * 设置生产者开关状态
     * @param enabled 是否开启
     */
    public void setProducerSwitch(boolean enabled) {
        redisTemplate.opsForValue().set(PRODUCER_SWITCH_KEY, String.valueOf(enabled));
        log.info("设置生产者开关状态, 状态: {}", enabled);
    }
    
    /**
     * 获取生产者开关状态
     * @return 开关状态
     */
    public boolean getProducerSwitch() {
        String value = redisTemplate.opsForValue().get(PRODUCER_SWITCH_KEY);
        // 添加null检查，默认为false（只写原集群）
        return value != null && Boolean.parseBoolean(value);
    }
} 