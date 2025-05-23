package com.example.rocketmqdemo.controller;

import com.example.rocketmqdemo.consumer.RocketMQConsumerContainer;
import com.example.rocketmqdemo.model.MessageDTO;
import com.example.rocketmqdemo.producer.RocketMQProducer;
import com.example.rocketmqdemo.config.ConsumerSwitchMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/mq")
public class MQController {

    @Autowired
    private RocketMQProducer producer;
    
    @Autowired
    private RocketMQConsumerContainer consumerContainer;
    
    @Autowired
    private ConsumerSwitchMonitor consumerSwitchMonitor;
    
    /**
     * 发送消息
     */
    @PostMapping("/send/{topic}")
    public Map<String, Object> sendMessage(
            @PathVariable String topic,
            @RequestParam(required = false) String tag,
            @RequestParam String cluster,
            @RequestBody(required = false) String content) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 构建消息
            MessageDTO message = MessageDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .type(cluster)
                    .content(content)
                    .businessId(UUID.randomUUID().toString())
                    .createTime(LocalDateTime.now())
                    .build();
            
            // 获取生产者开关状态（仅用于日志记录）
            boolean dualWriteEnabled = producer.getProducerSwitch();
            
            log.info("发送消息到 topic: {}, 双写开关状态: {}, 目标集群: {}", 
                    topic, dualWriteEnabled, cluster);
            
            // 直接使用请求指定的业务集群发送，如果开关打开会自动双写到原集群
            boolean success = producer.sendMessage(topic, tag, message, cluster);
            
            if (success) {
                result.put("success", true);
                result.put("message", "消息发送成功");
                result.put("data", message);
                // 添加使用的集群信息到响应
                result.put("targetCluster", cluster);
                // 添加双写状态到响应
                result.put("dualWriteEnabled", dualWriteEnabled);
            } else {
                result.put("success", false);
                result.put("message", "消息发送失败，详情请查看日志");
                result.put("data", message);
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
            result.put("success", false);
            result.put("message", "发送消息失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 设置生产者开关
     */
    @PostMapping("/producer/switch")
    public Map<String, Object> setProducerSwitch(@RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            producer.setProducerSwitch(enabled);
            
            result.put("success", true);
            result.put("message", "设置生产者开关成功");
            result.put("data", enabled);
        } catch (Exception e) {
            log.error("设置生产者开关失败", e);
            result.put("success", false);
            result.put("message", "设置生产者开关失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取生产者开关状态
     */
    @GetMapping("/producer/switch")
    public Map<String, Object> getProducerSwitch() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean enabled = producer.getProducerSwitch();
            
            result.put("success", true);
            result.put("message", "获取生产者开关状态成功");
            result.put("data", enabled);
        } catch (Exception e) {
            log.error("获取生产者开关状态失败", e);
            result.put("success", false);
            result.put("message", "获取生产者开关状态失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 设置消费者开关
     */
    @PostMapping("/consumer/switch/{consumerId}/{topic}")
    public Map<String, Object> setConsumerSwitch(
            @PathVariable String consumerId,
            @PathVariable String topic,
            @RequestParam boolean enabled) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            consumerContainer.setConsumerSwitch(consumerId, topic, enabled);
            
            result.put("success", true);
            result.put("message", "设置消费者开关成功");
            result.put("data", enabled);
        } catch (Exception e) {
            log.error("设置消费者开关失败", e);
            result.put("success", false);
            result.put("message", "设置消费者开关失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取消费者开关状态
     */
    @GetMapping("/consumer/switch/{consumerId}/{topic}")
    public Map<String, Object> getConsumerSwitch(
            @PathVariable String consumerId,
            @PathVariable String topic) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean enabled = consumerContainer.getConsumerSwitch(consumerId, topic);
            
            result.put("success", true);
            result.put("message", "获取消费者开关状态成功");
            result.put("data", enabled);
        } catch (Exception e) {
            log.error("获取消费者开关状态失败", e);
            result.put("success", false);
            result.put("message", "获取消费者开关状态失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 停止特定消费者
     */
    @PostMapping("/consumer/shutdown/{consumerId}/{topic}")
    public Map<String, Object> shutdownConsumer(
            @PathVariable String consumerId,
            @PathVariable String topic) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            consumerContainer.shutdownConsumer(consumerId, topic);
            
            result.put("success", true);
            result.put("message", "停止消费者成功");
            //result.put("data", Map.of("consumerGroup", consumerId, "topic", topic));
            Map<String, String> data = new HashMap<>();
            data.put("consumerGroup", consumerId);
            data.put("topic", topic);
            result.put("data", data);
        } catch (Exception e) {
            log.error("停止消费者失败", e);
            result.put("success", false);
            result.put("message", "停止消费者失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 手动触发消费者开关检查
     */
    @PostMapping("/consumer/check-switch")
    public Map<String, Object> triggerConsumerSwitchCheck() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            consumerSwitchMonitor.triggerManualCheck();
            
            result.put("success", true);
            result.put("message", "手动触发消费者开关检查成功");
        } catch (Exception e) {
            log.error("手动触发消费者开关检查失败", e);
            result.put("success", false);
            result.put("message", "手动触发消费者开关检查失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 启用/禁用消费者开关监控
     */
    @PostMapping("/consumer/monitor")
    public Map<String, Object> setConsumerMonitor(@RequestParam boolean enabled) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (enabled) {
                consumerSwitchMonitor.enableMonitor();
            } else {
                consumerSwitchMonitor.disableMonitor();
            }
            
            result.put("success", true);
            result.put("message", enabled ? "启用消费者监控成功" : "禁用消费者监控成功");
            result.put("data", enabled);
        } catch (Exception e) {
            log.error("设置消费者监控状态失败", e);
            result.put("success", false);
            result.put("message", "设置消费者监控状态失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 获取消费者监控状态
     */
    @GetMapping("/consumer/monitor")
    public Map<String, Object> getConsumerMonitorStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean enabled = consumerSwitchMonitor.isMonitorEnabled();
            long checkInterval = consumerSwitchMonitor.getCheckInterval();
            
            result.put("success", true);
            result.put("message", "获取消费者监控状态成功");
            // result.put("data", Map.of(
            //     "enabled", enabled,
            //     "checkIntervalMs", checkInterval,
            //     "checkIntervalSeconds", checkInterval / 1000
            // ));
            Map<String, Object> data = new HashMap<>();
            data.put("enabled", enabled);
            data.put("checkIntervalMs", checkInterval);
            data.put("checkIntervalSeconds", checkInterval / 1000);
            result.put("data", data);
        } catch (Exception e) {
            log.error("获取消费者监控状态失败", e);
            result.put("success", false);
            result.put("message", "获取消费者监控状态失败: " + e.getMessage());
        }
        
        return result;
    }
} 