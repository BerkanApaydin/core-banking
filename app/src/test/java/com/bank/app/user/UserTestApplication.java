package com.bank.app.user;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Test slice anchor for {@code @WebMvcTest} classes in this package: it keeps
 * component scanning inside the user context so the full application and its
 * infrastructure filters are not loaded. The test context discovers it
 * implicitly; do not delete it as "unused".
 */
@SuppressWarnings("null")
@SpringBootApplication(exclude = RedisAutoConfiguration.class)
@ConfigurationPropertiesScan("com.bank.app")
public class UserTestApplication {
}
