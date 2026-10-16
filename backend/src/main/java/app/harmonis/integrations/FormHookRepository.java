package app.harmonis.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormHookRepository extends JpaRepository<FormHook, Long> {

    List<FormHook> findByFormIdOrderByEventAscSortOrderAsc(Long formId);

    List<FormHook> findByFormIdAndEventOrderBySortOrderAsc(Long formId, String event);

    List<FormHook> findByIntegrationId(Long integrationId);

    long countByIntegrationId(Long integrationId);

    void deleteByFormId(Long formId);
}
