package com.l2c.nexus.platform.web;

import com.l2c.nexus.platform.application.PlatformAdmin;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Hands the gate's PlatformAdmin to controllers. A missing one is a bug, so it fails loudly. */
class CurrentPlatformAdminArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentPlatformAdmin.class)
                && PlatformAdmin.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object admin =
                webRequest.getAttribute(
                        PlatformGateFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (admin instanceof PlatformAdmin platformAdmin) {
            return platformAdmin;
        }
        throw new IllegalStateException(
                "No platform administrator: this route is not behind the platform gate");
    }
}
