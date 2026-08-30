package com.techeazyhq.cm.testkit;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Postgres Testcontainers base (plan §6/§16) — a fresh container per test class,
 * R2DBC/Flyway connection properties registered via each service's own
 * {@code @DynamicPropertySource} (kept there, not here, since the property keys differ
 * per service's {@code application.yml}). Concrete test classes add {@code @SpringBootTest}
 * themselves — this base stays framework-agnostic beyond Testcontainers/JUnit.
 */
@Testcontainers
public abstract class PgIntegrationTestBase {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
