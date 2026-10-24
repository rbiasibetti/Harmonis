package app.harmonis.datamodels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DataModelRowRepository extends JpaRepository<DataModelRow, Long> {
    List<DataModelRow> findByDataModelIdOrderByIdAsc(long dataModelId);
    long countByDataModelId(long dataModelId);

    @Transactional
    @Modifying
    @Query("delete from DataModelRow r where r.dataModelId = :id")
    void deleteByDataModelId(long id);
}
