package cn.vpclub.coupon.command.config;

import cn.vpclub.spring.boot.axon.autoconfigure.EventSourcingRepositoryFactory;
import cn.vpclub.spring.boot.axon.properties.AxonEventStoreProperties;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.amqp.eventhandling.spring.SpringAMQPMessageSource;
import org.axonframework.serialization.Serializer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
@EnableConfigurationProperties({AxonEventStoreProperties.class})
@AllArgsConstructor
public class AxonConfiguration {

    private AxonEventStoreProperties axonEventStoreProperties;

    private EventSourcingRepositoryFactory repositoryFactory;

    @Bean
    public SpringAMQPMessageSource springAMQPMessageSource(Serializer serializer) {
        return new SpringAMQPMessageSource(serializer) {
            @RabbitListener(queues = "bait-coupon-command") // Saga depends on it.
            @Override
            public void onMessage(Message message, Channel channel) throws Exception {
                log.info("message received: {}", message.toString());
                super.onMessage(message, channel);
            }
        };
    }

}
