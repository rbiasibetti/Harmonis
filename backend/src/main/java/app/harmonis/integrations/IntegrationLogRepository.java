package app.harmonis.integrations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface IntegrationLogRepository
        extends JpaRepository<IntegrationLog, Long>, JpaSpecificationExecutor<IntegrationLog> {

    Optional<IntegrationLog> findFirstByIntegrationIdOrderByCreatedAtDescIdDesc(Long integrationId);

    long countByStatusAndCreatedAtGreaterThanEqual(String status, Instant since);
}
