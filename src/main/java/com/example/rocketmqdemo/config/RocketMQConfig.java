package com.example.rocketmqdemo.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class RocketMQConfig {

    @Autowired
    private MQProperties mqProperties;

    /**
     * 创建原始集群的RocketMQTemplate
     */
    @Bean
    @Primary
    public RocketMQTemplate originRocketMQTemplate() {
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        DefaultMQProducer producer = new DefaultMQProducer(mqProperties.getProducer().getGroup());
        producer.setNamesrvAddr(mqProperties.getNameServer());
        producer.setSendMsgTimeout(mqProperties.getProducer().getSendMessageTimeout());
        log.info("Origin producer configured with ID: {}", mqProperties.getProducer().getGroup());
        rocketMQTemplate.setProducer(producer);
        rocketMQTemplate.setMessageConverter(new MappingJackson2MessageConverter());
        return rocketMQTemplate;
    }
    
    /**
     * 创建Product集群的RocketMQTemplate
     */
    @Bean
    public RocketMQTemplate productRocketMQTemplate() {
        try {
            String[] producerGroups = mqProperties.getProducer().getGroup().split(";");
            String[] topicClusters = mqProperties.getProducer().getTopicClusters().split(";");
            String producerId = getProducerIdForCluster("product", producerGroups, topicClusters);
            log.info("Creating product cluster template with name server: {}", mqProperties.getProductAddress());
            return createRocketMQTemplate("product", mqProperties.getProductAddress(), producerId);
        } catch (Exception e) {
            log.error("创建Product集群RocketMQTemplate失败", e);
            // 返回一个空实现而不是null，避免空指针异常
            return null;
        }
    }
    
    /**
     * 创建Asset集群的RocketMQTemplate
     */
    @Bean
    public RocketMQTemplate assetRocketMQTemplate() {
        try {
            String[] producerGroups = mqProperties.getProducer().getGroup().split(";");
            String[] topicClusters = mqProperties.getProducer().getTopicClusters().split(";");
            String producerId = getProducerIdForCluster("asset", producerGroups, topicClusters);
            return createRocketMQTemplate("asset", mqProperties.getAssetAddress(), producerId);
        } catch (Exception e) {
            log.error("创建Asset集群RocketMQTemplate失败", e);
            return null;
        }
    }
    
    /**
     * 创建Operation集群的RocketMQTemplate
     */
    @Bean
    public RocketMQTemplate operationRocketMQTemplate() {
        try {
            String[] producerGroups = mqProperties.getProducer().getGroup().split(";");
            String[] topicClusters = mqProperties.getProducer().getTopicClusters().split(";");
            String producerId = getProducerIdForCluster("operation", producerGroups, topicClusters);
            return createRocketMQTemplate("operation", mqProperties.getOperationAddress(), producerId);
        } catch (Exception e) {
            log.error("创建Operation集群RocketMQTemplate失败", e);
            return null;
        }
    }
    
    /**
     * 创建Risk集群的RocketMQTemplate
     */
    @Bean
    public RocketMQTemplate riskRocketMQTemplate() {
        try {
            String[] producerGroups = mqProperties.getProducer().getGroup().split(";");
            String[] topicClusters = mqProperties.getProducer().getTopicClusters().split(";");
            String producerId = getProducerIdForCluster("risk", producerGroups, topicClusters);
            return createRocketMQTemplate("risk", mqProperties.getRiskAddress(), producerId);
        } catch (Exception e) {
            log.error("创建Risk集群RocketMQTemplate失败", e);
            return null;
        }
    }
    
    /**
     * 创建Base集群的RocketMQTemplate
     */
    @Bean
    public RocketMQTemplate baseRocketMQTemplate() {
        try {
            String[] producerGroups = mqProperties.getProducer().getGroup().split(";");
            String[] topicClusters = mqProperties.getProducer().getTopicClusters().split(";");
            String producerId = getProducerIdForCluster("base", producerGroups, topicClusters);
            return createRocketMQTemplate("base", mqProperties.getBaseAddress(), producerId);
        } catch (Exception e) {
            log.error("创建Base集群RocketMQTemplate失败", e);
            return null;
        }
    }

    /**
     * 创建不同业务集群的RocketMQTemplate
     */
    @Bean
    public Map<String, RocketMQTemplate> clusterTemplates() {
        Map<String, RocketMQTemplate> templates = new HashMap<>();
        
        try {
            // 将通过@Bean注解创建的单例模板放入Map中，而不是重新创建实例
            // product集群模板（延迟获取，防止循环依赖）
            if (productRocketMQTemplate() != null) {
                templates.put("product", productRocketMQTemplate());
            }
            
            // asset集群模板
            if (assetRocketMQTemplate() != null) {
                templates.put("asset", assetRocketMQTemplate());
            }
            
            // operation集群模板
            if (operationRocketMQTemplate() != null) {
                templates.put("operation", operationRocketMQTemplate());
            }
            
            // risk集群模板
            if (riskRocketMQTemplate() != null) {
                templates.put("risk", riskRocketMQTemplate());
            }
            
            // base集群模板
            if (baseRocketMQTemplate() != null) {
                templates.put("base", baseRocketMQTemplate());
            }
            
            // 原始集群模板
            templates.put("origin", originRocketMQTemplate());
        } catch (Exception e) {
            log.error("创建业务集群模板时发生错误", e);
        }
        
        // 打印创建完成的模板列表
        log.info("创建完成的业务集群模板: {}", templates.keySet());
        
        return templates;
    }

    /**
     * 创建不同业务集群的消费者模板
     * 这些模板只用于获取配置，不会被直接启动
     */
    @Bean
    public Map<String, DefaultMQPushConsumer> clusterConsumers() {
        Map<String, DefaultMQPushConsumer> consumers = new HashMap<>();
        
        try {
            // 创建各个集群的消费者模板
            consumers.put("product", createConsumerTemplate("product", mqProperties.getProductAddress()));
            consumers.put("asset", createConsumerTemplate("asset", mqProperties.getAssetAddress()));
            consumers.put("operation", createConsumerTemplate("operation", mqProperties.getOperationAddress()));
            consumers.put("risk", createConsumerTemplate("risk", mqProperties.getRiskAddress()));
            consumers.put("base", createConsumerTemplate("base", mqProperties.getBaseAddress()));
            
            // 添加一个origin模板
            consumers.put("origin", createConsumerTemplate("origin", mqProperties.getNameServer()));
        } catch (Exception e) {
            log.error("创建业务集群消费者模板时发生错误", e);
        }
        
        return consumers;
    }

    /**
     * 创建指定集群的RocketMQTemplate
     */
    private RocketMQTemplate createRocketMQTemplate(String cluster, String namesrvAddr, String producerId) {
        try {
            log.info("开始创建 {} 集群的RocketMQTemplate, namesrvAddr: {}", cluster, namesrvAddr);
            
            RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
            DefaultMQProducer producer = new DefaultMQProducer(producerId);
            producer.setNamesrvAddr(namesrvAddr);
            producer.setSendMsgTimeout(mqProperties.getProducer().getSendMessageTimeout());
            
            log.info("{} producer configured with producer ID: {}", cluster, producerId);
            
            rocketMQTemplate.setProducer(producer);
            rocketMQTemplate.setMessageConverter(new MappingJackson2MessageConverter());
            
            log.info("{} 集群的RocketMQTemplate创建成功", cluster);
            
            return rocketMQTemplate;
        } catch (Exception e) {
            log.error("创建 {} 集群的RocketMQTemplate失败: {}", cluster, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 创建消费者模板（只用于配置，不会启动）
     */
    private DefaultMQPushConsumer createConsumerTemplate(String cluster, String namesrvAddr) {
        // 给模板一个临时ID，不会实际启动
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("TEMPLATE_" + cluster);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeThreadMax(20);
        consumer.setConsumeThreadMin(10);
        log.info("{} consumer template created", cluster);
        return consumer;
    }
    
    /**
     * 根据集群类型获取对应的Producer ID
     */
    private String getProducerIdForCluster(String clusterType, String[] producerGroups, String[] topicClusters) {
        try {
            // 如果配置项为空或长度不匹配，使用默认ID
            if (producerGroups == null || topicClusters == null || 
                producerGroups.length == 0 || topicClusters.length == 0) {
                log.warn("生产者组或主题集群配置为空，为{}集群使用默认ID", clusterType);
                return "PID_" + clusterType.toUpperCase() + "_GROUP";
            }
            
            // 如果只有一个生产者组配置，所有集群都使用这个ID
            if (producerGroups.length == 1) {
                log.info("只配置了一个生产者组ID，所有集群都使用: {}", producerGroups[0]);
                return producerGroups[0];
            }
            
            // 查找集群类型在topicClusters中的索引
            for (int i = 0; i < topicClusters.length; i++) {
                if (clusterType.equals(topicClusters[i]) && i < producerGroups.length) {
                    return producerGroups[i];
                }
            }
            
            // 如果找不到对应的ID，则使用集群名称作为默认ID
            log.warn("未找到{}集群对应的生产者组ID，使用默认ID", clusterType);
            return "PID_" + clusterType.toUpperCase() + "_GROUP";
        } catch (Exception e) {
            log.error("获取{}集群的生产者ID时出错: {}", clusterType, e.getMessage(), e);
            return "PID_" + clusterType.toUpperCase() + "_GROUP";
        }
    }
} 