package com.example.rocketmqdemo.config;

import com.example.rocketmqdemo.consumer.DemoMessageConsumer;
import com.example.rocketmqdemo.consumer.RocketMQConsumerContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Slf4j
@Component
public class ConsumerSwitchMonitor {

    @Autowired
    private MQProperties mqProperties;
    
    @Autowired
    private RocketMQConsumerContainer consumerContainer;
    
    @Autowired
    private DemoMessageConsumer demoMessageConsumer;
    
    // 启用/禁用监控的标志
    private boolean monitorEnabled;
    
    @PostConstruct
    public void init() {
        // 从配置中获取监控启用状态
        this.monitorEnabled = mqProperties.getMonitor() != null ? 
                mqProperties.getMonitor().isEnabled() : true;
        
        log.info("消费者开关监控服务已启动, 监控状态: {}, 检查间隔: {}ms", 
                monitorEnabled, 
                mqProperties.getMonitor() != null ? mqProperties.getMonitor().getCheckIntervalMs() : 30000);
        
        // 如果配置了启动时立即检查，则执行一次检查
        if (mqProperties.getMonitor() != null && mqProperties.getMonitor().isInitialCheck()) {
            log.info("执行启动时初始检查");
            triggerManualCheck();
        }
    }
    
    /**
     * 定期检查消费者开关状态，使用配置的时间间隔
     */
    @Scheduled(fixedRateString = "${rocketmq.monitor.check-interval-ms:30000}")
    public void monitorConsumerSwitches() {
        if (!monitorEnabled || !mqProperties.getConsumer().isEnable()) {
            return;
        }
        
        try {
            log.debug("开始定期检查消费者开关状态");
            
            // 获取所有配置项
            String[] topics = mqProperties.getConsumer().getTopics().split(";");
            String[] topicClusters = mqProperties.getConsumer().getTopicClusters().split(";");
            String[] consumerGroups = mqProperties.getConsumer().getGroup().split(";");
            
            for (int i = 0; i < topics.length; i++) {
                String topic = topics[i];
                String clusterType = i < topicClusters.length ? topicClusters[i] : "origin";
                String consumerGroup = i < consumerGroups.length ? consumerGroups[i] : "defaultConsumerGroup";
                
                // 检查并切换消费者
                consumerContainer.checkAndSwitchConsumer(consumerGroup, topic, clusterType, demoMessageConsumer);
            }
            
            log.debug("定期检查消费者开关状态完成");
        } catch (Exception e) {
            log.error("监控消费者开关状态时发生错误", e);
        }
    }
    
    /**
     * 启用监控
     */
    public void enableMonitor() {
        this.monitorEnabled = true;
        log.info("消费者开关监控已启用");
    }
    
    /**
     * 禁用监控
     */
    public void disableMonitor() {
        this.monitorEnabled = false;
        log.info("消费者开关监控已禁用");
    }
    
    /**
     * 获取监控状态
     */
    public boolean isMonitorEnabled() {
        return monitorEnabled;
    }
    
    /**
     * 手动触发一次检查
     */
    public void triggerManualCheck() {
        log.info("手动触发消费者开关检查");
        monitorConsumerSwitches();
    }
    
    /**
     * 获取当前配置的检查间隔
     */
    public long getCheckInterval() {
        return mqProperties.getMonitor() != null ? 
                mqProperties.getMonitor().getCheckIntervalMs() : 30000;
    }
} 