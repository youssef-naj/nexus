package com.l2c.nexus.support;

import java.util.UUID;

/**
 * Two organizations and seven signed-in users, the standard cast for tenant isolation tests.
 *
 * <ul>
 *   <li>Org A: alice (OWNER), ann (ADMIN), dan (MANAGER), carol and frank (EMPLOYEE)
 *   <li>Org B: bob (OWNER)
 *   <li>erin: signed in, belongs to no organization
 * </ul>
 */
public record TenantWorld(
        int port,
        UUID orgA,
        UUID orgB,
        Member alice,
        Member ann,
        Member dan,
        Member carol,
        Member frank,
        Member bob,
        Member erin) {

    public record Member(UUID userId, String email, HttpTestClient http) {}

    public HttpTestClient anonymous() {
        return new HttpTestClient(port);
    }
}
