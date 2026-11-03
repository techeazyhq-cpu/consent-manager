/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.cnsqry;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.techeazyhq.cm.cnsqry.app.GetCnsArtQryHandler;
import com.techeazyhq.cm.cnsqry.app.InternalStatusQryHandler;
import com.techeazyhq.cm.cnsqry.app.Projector;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsArtRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.RedisCachedCnsArtRdMdlRepo;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

/**
 * The service's own wiring (#31): the projector and the queries share the cached read model, so the projector's
 * writes evict what the queries cached; the status endpoint reads the database alone.
 */
@Testcontainers
@SpringBootTest(properties = "cm.pulsar.service-url=pulsar://127.0.0.1:1")
class CnsQryCacheWiringIT extends PgIntegrationTestBase {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void servers(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName() + "?schema=cns_qry");
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private Projector projector;

    @Autowired
    private GetCnsArtQryHandler getCnsArt;

    @Autowired
    private InternalStatusQryHandler internalStatus;

    @Test
    void theProjectorAndQueriesShareTheCacheAndTheStatusEndpointBypassesIt() {
        assertInstanceOf(RedisCachedCnsArtRdMdlRepo.class, ReflectionTestUtils.getField(projector, "rdMdlRepo"));
        assertInstanceOf(RedisCachedCnsArtRdMdlRepo.class, ReflectionTestUtils.getField(getCnsArt, "rdMdlRepo"));
        assertInstanceOf(R2dbcCnsArtRdMdlRepoAdapter.class, ReflectionTestUtils.getField(internalStatus, "rdMdlRepo"));
    }
}
