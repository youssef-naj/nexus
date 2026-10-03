package com.l2c.nexus.organization.web;

import com.l2c.nexus.organization.application.OrgContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Hands the gate's OrgContext to controllers. A missing context is a bug, so it fails loudly. */
class CurrentOrgArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOrg.class)
                && OrgContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Object context =
                webRequest.getAttribute(OrgScopeFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (context instanceof OrgContext orgContext) {
            return orgContext;
        }
        throw new IllegalStateException(
                "No tenant context: this route is not behind the organization gate");
    }
}
