package com.example.rocketmqdemo.config;

import com.example.rocketmqdemo.consumer.DemoMessageConsumer;
import com.example.rocketmqdemo.consumer.RocketMQConsumerContainer;
import com.example.rocketmqdemo.producer.RocketMQProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MQInitService implements ApplicationRunner, ApplicationListener<ContextClosedEvent> {

    @Autowired
    private MQProperties mqProperties;
    
    @Autowired
    private RocketMQConsumerContainer consumerContainer;
    
    @Autowired
    private DemoMessageConsumer demoMessageConsumer;
    
    @Autowired
    private RocketMQProducer rocketMQProducer;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (mqProperties.getProducer().isEnable()) {
                initProducers();
            }
            
            if (mqProperties.getConsumer().isEnable()) {
                initConsumers();
            }
        } catch (Exception e) {
            log.error("初始化RocketMQ组件时发生错误", e);
        }
    }
    
    /**
     * 应用关闭时执行清理操作
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("应用关闭中，清理RocketMQ资源...");
        try {
            // 关闭所有消费者
            consumerContainer.shutdown();
            log.info("RocketMQ资源清理完成");
        } catch (Exception e) {
            log.error("清理RocketMQ资源时发生错误", e);
        }
    }
    
    /**
     * 初始化生产者
     */
    private void initProducers() {
        try {
            // 初始化生产者开关状态（默认关闭，只写入原集群）
            rocketMQProducer.initProducerSwitch();
            log.info("初始化生产者成功");
        } catch (Exception e) {
            log.error("初始化生产者失败", e);
        }
    }
    
    /**
     * 初始化消费者
     */
    private void initConsumers() {
        try {
            // 获取所有配置项
            String[] topics = mqProperties.getConsumer().getTopics().split(";");
            String[] topicClusters = mqProperties.getConsumer().getTopicClusters().split(";");
            String[] consumerGroups = mqProperties.getConsumer().getGroup().split(";");
            String[] subExpressions = mqProperties.getConsumer().getInitSubExps().split(";");
            
            // 先验证demoMessageConsumer是否为空
            if (demoMessageConsumer == null) {
                log.error("消息监听器为空，无法初始化任何消费者");
                return;
            }
            
            for (int i = 0; i < topics.length; i++) {
                try {
                    String topic = topics[i];
                    String clusterType = i < topicClusters.length ? topicClusters[i] : "origin";
                    String consumerGroup = i < consumerGroups.length ? consumerGroups[i] : "defaultConsumerGroup";
                    String subExpression = i < subExpressions.length ? subExpressions[i] : "*";
                    
                    // 初始化消费者开关（只在开关不存在时设置默认值）
                    consumerContainer.initConsumerSwitch(consumerGroup, topic, false);
                    
                    // 订阅主题并消费消息
                    consumerContainer.subscribeAndConsume(consumerGroup, topic, subExpression, demoMessageConsumer, clusterType);
                    
                    log.info("初始化消费者 - topic: {}, clusterType: {}, consumerGroup: {}", topic, clusterType, consumerGroup);
                } catch (Exception e) {
                    log.error("初始化单个消费者时发生错误, index: " + i, e);
                    // 继续处理下一个消费者
                }
            }
        } catch (Exception e) {
            log.error("初始化所有消费者时发生错误", e);
        }
    }
} 
 