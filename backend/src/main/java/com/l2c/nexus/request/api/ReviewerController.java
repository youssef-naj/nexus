package com.l2c.nexus.request.api;

import com.l2c.nexus.organization.application.OrgContext;
import com.l2c.nexus.organization.web.CurrentOrg;
import com.l2c.nexus.request.application.RequestAssignmentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant route: the gate has already proven the caller belongs to {orgId}. */
@RestController
@RequestMapping("/api/orgs/{orgId}/reviewers")
class ReviewerController {

    private final RequestAssignmentService assignments;

    ReviewerController(RequestAssignmentService assignments) {
        this.assignments = assignments;
    }

    @GetMapping
    List<ReviewerResponse> list(@CurrentOrg OrgContext org) {
        return assignments.reviewers(org).stream()
                .map(
                        reviewer ->
                                new ReviewerResponse(
                                        reviewer.membershipId(),
                                        reviewer.displayName(),
                                        reviewer.role(),
                                        reviewer.userId().equals(org.userId())))
                .toList();
    }
}
