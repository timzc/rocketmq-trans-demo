package com.example.rocketmqdemo.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

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