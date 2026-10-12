package app.harmonis.forms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {
    List<FormSubmission> findByFormIdOrderByCreatedAtDesc(Long formId);
    long countByFormId(Long formId);
    List<FormSubmission> findTop5ByOrderByCreatedAtDesc();
}
