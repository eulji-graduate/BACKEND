package com.module06.backend.cap.infrastructure.assembly;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.persistenceunit.ManagedClassNameFilter;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Tag("local-sql")
@Testcontainers
@SpringBootTest(properties={
    "spring.datasource.password=unused", "spring.datasource.username=test",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false",
    "spring.data.redis.password=test", "ai.internal-token=test"
})
@Import(LocalAssemblyMysqlCommitTest.ExcludeFixtures.class)
class LocalAssemblyMysqlCommitTest extends LocalAssemblyCommitIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("local_assembly_check")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/local/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");
    @TestConfiguration
    static class ExcludeFixtures {
        @Bean ManagedClassNameFilter managedClassNameFilter() {
            return name -> !name.startsWith("com.module06.backend.architecture.fixture");
        }
    }
}
