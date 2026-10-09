package com.l2c.nexus.request.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.l2c.nexus.TestcontainersConfiguration;
import com.l2c.nexus.support.TenantWorld;
import com.l2c.nexus.support.TenantWorld.Member;
import com.l2c.nexus.support.TenantWorldFactory;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RequestReviewableFilterIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private TenantWorldFactory factory;

    private TenantWorld world;

    @BeforeEach
    void setUp() {
        world = factory.create(port);
    }

    private String requests() {
        return "/api/orgs/" + world.orgA() + "/requests";
    }

    private void create(Member as, String title, boolean submit) {
        HttpResponse<String> created =
                as.http()
                        .postJson(
                                requests(),
                                "{\"title\":\"" + title + "\",\"category\":\"HR\"}",
                                as.http().csrfToken());
        assertThat(created.statusCode()).isEqualTo(201);
        if (submit) {
            String id = first(created.body(), "\"id\":\"([0-9a-f-]{36})\"");
            HttpResponse<String> response =
                    as.http()
                            .postJson(
                                    requests() + "/" + id + "/transitions",
                                    "{\"action\":\"SUBMIT\",\"version\":0}",
                                    as.http().csrfToken());
            assertThat(response.statusCode()).isEqualTo(200);
        }
    }

    private HttpResponse<String> list(Member as, String query) {
        return as.http().get(requests() + query);
    }

    private static String first(String body, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(body);
        assertThat(matcher.find()).as(regex + " in " + body).isTrue();
        return matcher.group(1);
    }

    private static List<String> titles(String body) {
        Matcher matcher = Pattern.compile("\"title\":\"([^\"]*)\"").matcher(body);
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1));
        }
        return titles;
    }

    @Test
    void reviewersSeeOthersSubmissionsButNeverTheirOwnOrDrafts() {
        create(world.carol(), "Carol submission", true);
        create(world.dan(), "Dan submission", true);
        create(world.frank(), "Frank draft", false);

        assertThat(titles(list(world.dan(), "?reviewable=true").body()))
                .containsExactly("Carol submission");
        assertThat(titles(list(world.alice(), "?reviewable=true").body()))
                .containsExactly("Dan submission", "Carol submission");
    }

    @Test
    void employeesGetNothingEvenWhenTheyHaveSubmittedRequests() {
        create(world.frank(), "Frank submission", true);

        assertThat(list(world.frank(), "?reviewable=true").body()).contains("\"totalElements\":0");
        assertThat(list(world.carol(), "?reviewable=true").body()).contains("\"totalElements\":0");
    }

    @Test
    void aConflictingStatusYieldsNothingAndTheSameStatusIsHarmless() {
        create(world.carol(), "Carol submission", true);

        assertThat(list(world.alice(), "?reviewable=true&status=APPROVED").body())
                .contains("\"totalElements\":0");
        assertThat(titles(list(world.alice(), "?reviewable=true&status=SUBMITTED").body()))
                .containsExactly("Carol submission");
    }
}
