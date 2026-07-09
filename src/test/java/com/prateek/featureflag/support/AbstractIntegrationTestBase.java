package com.prateek.featureflag.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every Testcontainers-backed integration test in this
 * module. Extend it instead of relying on whatever Postgres
 * {@code application.properties} happens to point at.
 * <p>
 * Deliberately <b>not</b> annotated with {@code @Testcontainers}/
 * {@code @Container}: that JUnit 5 extension ties a container's stop
 * lifecycle to its owning test class, so six integration test classes
 * would each pay their own container startup cost. Instead, this is the
 * documented Testcontainers "singleton container" pattern — the
 * container is started once, here, in a static initializer, and never
 * explicitly stopped; Testcontainers' own Ryuk reaper tears it down when
 * the JVM exits. Every subclass across every test class shares this one
 * running instance.
 * <p>
 * {@code @ServiceConnection} still works without {@code @Container} —
 * it's processed by a Spring Boot test context customizer independent
 * of the JUnit extension, and overrides
 * {@code spring.datasource.url}/{@code username}/{@code password}
 * (and, for Postgres specifically, {@code spring.flyway.*} too) to point
 * at this container instead of whatever {@code application.properties}
 * has configured. Flyway migrations run against it exactly as they
 * would against a real deployment.
 */
public abstract class AbstractIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }
}