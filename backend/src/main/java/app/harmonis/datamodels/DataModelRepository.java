package app.harmonis.datamodels;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataModelRepository extends JpaRepository<DataModel, Long> {
    List<DataModel> findAllByOrderByNameAsc();
    List<DataModel> findByStatusOrderByNameAsc(String status);
    boolean existsByNameIgnoreCase(String name);
}
