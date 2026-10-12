package app.harmonis.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Long> {
    List<Page> findAllByOrderByUpdatedAtDesc();
    List<Page> findByStatusOrderByPublishedAtDesc(String status);
    Optional<Page> findBySlug(String slug);
    boolean existsBySlug(String slug);
    long countByStatus(String status);
}
