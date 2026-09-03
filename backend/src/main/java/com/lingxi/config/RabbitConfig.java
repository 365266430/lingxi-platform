package com.lingxi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档摄取异步管道拓扑：业务交换机 -> 摄取队列（带死信）。
 */
@Configuration
public class RabbitConfig {

    public static final String DOC_EXCHANGE = "lingxi.doc.exchange";
    public static final String DOC_ROUTING_KEY = "doc.ingest";
    public static final String DOC_QUEUE = "lingxi.doc.ingest.queue";
    public static final String DOC_DLX = "lingxi.doc.dlx";
    public static final String DOC_DLQ = "lingxi.doc.ingest.dlq";

    @Bean
    public MessageConverter messageConverter() {
        // "*" 信任自定义消息类型（DocIngestMessage）
        return new Jackson2JsonMessageConverter("*");
    }

    @Bean
    public DirectExchange docExchange() {
        return new DirectExchange(DOC_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange docDlx() {
        return new DirectExchange(DOC_DLX, true, false);
    }

    @Bean
    public Queue docQueue() {
        return QueueBuilder.durable(DOC_QUEUE)
                .deadLetterExchange(DOC_DLX)
                .deadLetterRoutingKey("dlq")
                .quorum()
                .build();
    }

    @Bean
    public Queue docDlq() {
        return QueueBuilder.durable(DOC_DLQ).build();
    }

    @Bean
    public Binding docBinding() {
        return BindingBuilder.bind(docQueue()).to(docExchange()).with(DOC_ROUTING_KEY);
    }

    @Bean
    public Binding docDlqBinding() {
        return BindingBuilder.bind(docDlq()).to(docDlx()).with("dlq");
    }
}
