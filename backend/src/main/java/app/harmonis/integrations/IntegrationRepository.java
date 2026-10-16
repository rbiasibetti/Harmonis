package app.harmonis.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationRepository extends JpaRepository<Integration, Long> {

    List<Integration> findAllByOrderByNameAsc();

    List<Integration> findByStatusOrderByNameAsc(String status);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
