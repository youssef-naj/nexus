package com.l2c.nexus.platform.web;

import com.l2c.nexus.platform.application.PlatformAccessService;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class PlatformWebConfig implements WebMvcConfigurer {

    /** After Spring Security's chain (order -100), so the authentication is already established. */
    @Bean
    FilterRegistrationBean<PlatformGateFilter> platformGateFilter(PlatformAccessService access) {
        FilterRegistrationBean<PlatformGateFilter> registration =
                new FilterRegistrationBean<>(new PlatformGateFilter(access));
        registration.addUrlPatterns("/*"); // the filter decides which paths are platform paths
        registration.setOrder(-89);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentPlatformAdminArgumentResolver());
    }
}
