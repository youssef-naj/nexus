package com.l2c.nexus.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.l2c.nexus.TestcontainersConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UsersSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliedTheMigrations() throws SQLException {
        try (
                Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM flyway_schema_history WHERE success");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void emailUniquenessIgnoresCase() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        insertUser("Ada-" + suffix + "@Example.com", "ACTIVE");

        assertThatThrownBy(() -> insertUser("ada-" + suffix + "@example.com", "ACTIVE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_users_email_lower");
    }

    @Test
    void invalidStatusIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> insertUser("bogus-" + UUID.randomUUID() + "@example.com", "BOGUS"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_users_status");
    }

    private void insertUser(String email, String status) throws SQLException {
        try (
                Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at)
                        VALUES (?, ?, 'not-a-real-hash', 'Test User', ?, now(), now())
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, email);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }
}
