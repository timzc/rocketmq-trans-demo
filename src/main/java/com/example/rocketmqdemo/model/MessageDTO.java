package com.example.rocketmqdemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息ID
     */
    private String id;
    
    /**
     * 消息类型
     */
    private String type;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 业务ID
     */
    private String businessId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
} 