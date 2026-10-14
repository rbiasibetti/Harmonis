package app.harmonis.forms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormAttachmentRepository extends JpaRepository<FormAttachment, Long> {

    List<FormAttachment> findBySubmissionId(Long submissionId);

    List<FormAttachment> findByFormId(Long formId);
}
