package app.harmonis.forms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignatureRepository extends JpaRepository<Signature, Long> {

    List<Signature> findByFormId(long formId);

    /** Assinaturas confirmadas de uma resposta, em ordem cronologica (para o historico do PDF). */
    List<Signature> findBySubmissionIdOrderBySignedAtAsc(long submissionId);
}
