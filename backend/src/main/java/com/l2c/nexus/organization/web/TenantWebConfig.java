package com.l2c.nexus.organization.web;

import com.l2c.nexus.organization.application.OrgAccessService;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class TenantWebConfig implements WebMvcConfigurer {

    /**
     * Registered as a servlet filter after Spring Security's chain (which sits at order -100), so
     * the authentication is available. The filter is created here and is not itself a bean, so it
     * is not registered a second time.
     */
    @Bean
    FilterRegistrationBean<OrgScopeFilter> orgScopeFilter(OrgAccessService access) {
        FilterRegistrationBean<OrgScopeFilter> registration =
                new FilterRegistrationBean<>(new OrgScopeFilter(access));
        registration.addUrlPatterns("/*"); // the filter itself decides which paths are tenant paths
        registration.setOrder(-90);
        return registration;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentOrgArgumentResolver());
    }
}
