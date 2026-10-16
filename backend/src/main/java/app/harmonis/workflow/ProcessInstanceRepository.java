package app.harmonis.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {
    List<ProcessInstance> findByCreatedByOrderByCreatedAtDesc(Long createdBy);
    long countByStatus(String status);

    /** Remove as instancias do formulario (as tarefas caem por ON DELETE CASCADE no banco). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProcessInstance i where i.formId = :formId")
    void deleteByFormId(@Param("formId") Long formId);
    Optional<ProcessInstance> findFirstBySubmissionId(Long submissionId);
}
