package com.l2c.nexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * docs/api-routes.md is generated from the real route mappings. The test fails when the file no
 * longer matches the code. To regenerate it: set NEXUS_DOCS_WRITE=true and run this test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiRouteDocumentationTest {

    private static final Path FILE = Path.of("..", "docs", "api-routes.md");

    private record Route(String method, String path, String handler) {}

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void theRouteReferenceMatchesTheCode() throws IOException {
        String expected = render();
        if ("true".equals(System.getenv("NEXUS_DOCS_WRITE"))) {
            Files.writeString(FILE, expected);
            return;
        }
        assertThat(Files.exists(FILE))
                .as("docs/api-routes.md is missing: generate it with NEXUS_DOCS_WRITE=true")
                .isTrue();
        assertThat(Files.readString(FILE).replace("\r\n", "\n"))
                .as("docs/api-routes.md is out of date: regenerate it with NEXUS_DOCS_WRITE=true")
                .isEqualTo(expected);
    }

    private String render() {
        List<Route> routes = new ArrayList<>();
        mappings.getHandlerMethods()
                .forEach(
                        (info, handler) -> {
                            if (info.getPathPatternsCondition() == null) {
                                return;
                            }
                            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                            for (String path : info.getPathPatternsCondition().getPatternValues()) {
                                if (!path.startsWith("/api/")
                                        || path.startsWith("/api/test-support")) {
                                    continue;
                                }
                                String owner =
                                        handler.getBeanType().getSimpleName()
                                                + "."
                                                + handler.getMethod().getName();
                                if (methods.isEmpty()) {
                                    routes.add(new Route("ANY", path, owner));
                                }
                                for (RequestMethod method : methods) {
                                    routes.add(new Route(method.name(), path, owner));
                                }
                            }
                        });
        routes.sort(Comparator.comparing(Route::path).thenComparing(Route::method));

        StringBuilder out = new StringBuilder();
        out.append("# API routes\n\n");
        out.append("Generated from the code by `ApiRouteDocumentationTest`; do not edit by hand. ");
        out.append("Regenerate with `NEXUS_DOCS_WRITE=true` when routes change.\n\n");
        out.append(
                "Errors are RFC 9457 Problem Details. Every state-changing call needs the CSRF header. ");
        out.append(
                "Outside the routes below, Spring Security handles `POST /api/auth/login` (form fields ");
        out.append(
                "`email` and `password`, public) and `POST /api/auth/logout` (signed in). Health endpoints ");
        out.append(
                "under `/api/actuator` are for the orchestrator and are not exposed through the proxy.\n\n");
        out.append("| Method | Path | Who may call it | Handler |\n|---|---|---|---|\n");
        for (Route route : routes) {
            out.append("| `")
                    .append(route.method())
                    .append("` | `")
                    .append(route.path())
                    .append("` | ");
            out.append(access(route.path())).append(" | `").append(route.handler()).append("` |\n");
        }
        return out.toString();
    }

    private static String access(String path) {
        if (path.equals("/api/system/ping")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/verify-email")) {
            return "Anyone";
        }
        if (path.startsWith("/api/platform")) {
            return "Platform administrators (anyone else: `404`)";
        }
        if (path.startsWith("/api/orgs/{orgId}")) {
            return "Active members of the organization; the permission is checked per action";
        }
        return "Signed-in users";
    }
}
