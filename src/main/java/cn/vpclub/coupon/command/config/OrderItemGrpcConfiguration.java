package cn.vpclub.coupon.command.config;

import cn.vpclub.moses.order.query.api.OrderItemServiceGrpc;
import cn.vpclub.spring.boot.grpc.annotations.GRpcClient;
import io.grpc.ManagedChannel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by kellen on 2018/2/2.
 */
@Configuration
@EnableAutoConfiguration
public class OrderItemGrpcConfiguration {

    @GRpcClient("order-query")
    private ManagedChannel channel;

    @Bean
    OrderItemServiceGrpc.OrderItemServiceBlockingStub orderItemServiceBlockingStub() {
        return OrderItemServiceGrpc.newBlockingStub(channel);
    }

}
