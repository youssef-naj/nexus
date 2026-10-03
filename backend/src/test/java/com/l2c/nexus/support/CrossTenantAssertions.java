package com.l2c.nexus.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.support.TenantWorld.Member;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

/** Reusable isolation checks. Add one call per tenant endpoint; it must stay green. */
public final class CrossTenantAssertions {

    /** Calls an endpoint of the organization named by the path segment, as the given client. */
    @FunctionalInterface
    public interface OrgEndpoint {
        HttpResponse<String> call(HttpTestClient client, String organizationSegment);
    }

    private CrossTenantAssertions() {}

    /**
     * Verifies that an endpoint of Org A is invisible to everyone outside Org A: anonymous callers
     * get 401, outsiders and malformed ids get a 404 that is byte-identical to the 404 for an
     * organization that does not exist, and a real member is let through.
     */
    public static void assertIsolated(TenantWorld world, OrgEndpoint endpoint) {
        String org = world.orgA().toString();

        assertThat(endpoint.call(world.anonymous(), org).statusCode())
                .as("anonymous caller")
                .isEqualTo(401);

        HttpResponse<String> missing =
                endpoint.call(world.bob().http(), UUID.randomUUID().toString());
        assertThat(missing.statusCode()).as("organization that does not exist").isEqualTo(404);
        assertThat(contentType(missing)).contains("application/problem+json");

        for (Member outsider : List.of(world.bob(), world.erin())) {
            HttpResponse<String> response = endpoint.call(outsider.http(), org);
            assertThat(response.statusCode())
                    .as("outsider " + outsider.email() + " must not see Org A")
                    .isEqualTo(404);
            assertThat(response.body())
                    .as("indistinguishable from a missing organization")
                    .isEqualTo(missing.body());
        }

        for (String junk : List.of("not-a-uuid", "123")) {
            assertThat(endpoint.call(world.alice().http(), junk).statusCode())
                    .as("malformed id '" + junk + "'")
                    .isEqualTo(404);
        }

        assertThat(endpoint.call(world.alice().http(), org).statusCode())
                .as("a real member is admitted by the gate")
                .isNotIn(401, 404);
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }
}
