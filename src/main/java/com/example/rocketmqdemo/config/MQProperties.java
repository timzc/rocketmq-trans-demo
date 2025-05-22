package com.example.rocketmqdemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rocketmq")
public class MQProperties {
    private String nameServer;
    private String mqType = "RocketMQ";
    
    // 业务集群地址配置
    private String productAddress;
    private String assetAddress;
    private String operationAddress;
    private String riskAddress;
    private String baseAddress;
    
    // 兼容原始地址配置 
    public String getOriginAddress() {
        return nameServer;
    }
    
    private Producer producer;
    private Consumer consumer;
    
    @Data
    public static class Producer {
        private boolean enable = true;
        private String group;
        private Integer sendMessageTimeout = 3000;
        
        // 业务特有的配置
        private String topics;
        private String topicClusters;
        private String msgTypes;
        private String checkImmunityTimeInSeconds;
        
        // 支持连字符格式
        public void setTopicClusters(String topicClusters) {
            this.topicClusters = topicClusters;
        }
        
        public void setSendMessageTimeout(Integer sendMessageTimeout) {
            this.sendMessageTimeout = sendMessageTimeout;
        }
        
        public void setCheckImmunityTimeInSeconds(String checkImmunityTimeInSeconds) {
            this.checkImmunityTimeInSeconds = checkImmunityTimeInSeconds;
        }
    }
    
    @Data
    public static class Consumer {
        private boolean enable = true;
        private String group;
        
        // 业务特有的配置
        private String topics;
        private String topicClusters;
        private String msgTypes;
        private String beans;
        private String initSubExps;
        private String subscribeTypes;
        private String suspendTimeMillis;
        private String maxReconsumeTimes;
        private String consumeThreadNums;
        private String consumeTimeouts;
        
        // 支持连字符格式
        public void setTopicClusters(String topicClusters) {
            this.topicClusters = topicClusters;
        }
        
        public void setInitSubExps(String initSubExps) {
            this.initSubExps = initSubExps;
        }
        
        public void setSubscribeTypes(String subscribeTypes) {
            this.subscribeTypes = subscribeTypes;
        }
        
        public void setSuspendTimeMillis(String suspendTimeMillis) {
            this.suspendTimeMillis = suspendTimeMillis;
        }
        
        public void setMaxReconsumeTimes(String maxReconsumeTimes) {
            this.maxReconsumeTimes = maxReconsumeTimes;
        }
        
        public void setConsumeThreadNums(String consumeThreadNums) {
            this.consumeThreadNums = consumeThreadNums;
        }
        
        public void setConsumeTimeouts(String consumeTimeouts) {
            this.consumeTimeouts = consumeTimeouts;
        }
        
        public void setMsgTypes(String msgTypes) {
            this.msgTypes = msgTypes;
        }
    }
} 