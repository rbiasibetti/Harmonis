package app.harmonis.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {
    List<ProcessInstance> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    long countByStatus(String status);
}
