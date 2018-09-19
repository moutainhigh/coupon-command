package cn.vpclub.coupon.command;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import cn.vpclub.spring.boot.cors.autoconfigure.CorsConfiguration;
import cn.vpclub.spring.boot.cors.autoconfigure.CorsProperties;

@SpringBootApplication
@EnableConfigurationProperties({CorsProperties.class})
public class CouponCommandApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponCommandApplication.class, args);
    }

    @Bean
    public CorsConfiguration corsConfiguration() {
        return new CorsConfiguration();
    }
}
