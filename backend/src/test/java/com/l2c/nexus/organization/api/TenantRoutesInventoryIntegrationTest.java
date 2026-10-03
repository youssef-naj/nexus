package com.l2c.nexus.organization.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TenantRoutesInventoryIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void everyOrganizationScopedRouteIsBehindTheGate() {
        TenantWorld world = factory.create(port);
        List<String> checked = new ArrayList<>();

        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!pattern.startsWith("/api/orgs/{")) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (RequestMethod method :
                        methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    boolean hasBody = method != RequestMethod.GET && method != RequestMethod.DELETE;
                    var http = world.erin().http();
                    HttpResponse<String> response =
                            http.request(
                                    method.name(), path, hasBody ? "{}" : null, http.csrfToken());
                    assertThat(response.statusCode())
                            .as(method + " " + pattern + " must be hidden from non-members")
                            .isEqualTo(404);
                    checked.add(method + " " + pattern);
                }
            }
        }

        assertThat(checked).as("tenant routes found by the inventory").isNotEmpty();
    }
}
