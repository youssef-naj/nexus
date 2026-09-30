package com.l2c.nexus.identity.persistence;

import com.l2c.nexus.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Uses the lower(email) index created in V1, so lookups stay fast and case-insensitive. */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailCaseInsensitive(@Param("email") String email);
}
