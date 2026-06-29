package com.bank.app.user;

import com.bank.app.common.adapter.in.api.ApiVersionConfig;
import com.bank.app.common.adapter.in.web.ClientIpResolver;
import com.bank.app.common.adapter.out.security.TokenBlacklistAdapter;
import com.bank.app.common.adapter.config.ModuleTestConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
    ModuleTestConfig.class,
    TokenBlacklistAdapter.class,
    ClientIpResolver.class,
    ApiVersionConfig.class
})
@SuppressWarnings("null")
public class ModuleIntegrationTestConfig {
}
