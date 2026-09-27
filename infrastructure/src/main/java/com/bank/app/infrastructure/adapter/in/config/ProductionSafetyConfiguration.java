package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class ProductionSafetyConfiguration {

    /** Validate before singleton initialization can open connections or start workers. */
    @Bean
    static BeanFactoryPostProcessor productionSafetyChecks(Environment environment) {
        return beanFactory -> new ApplicationStartupValidator(environment).validateProductionConfig();
    }
}
