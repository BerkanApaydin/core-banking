package com.bank.app.transfer;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SuppressWarnings("null")
@SpringBootApplication(exclude = RedisAutoConfiguration.class)
@ConfigurationPropertiesScan("com.bank.app")
public class TransferTestApplication {
}
