package app.harmonis.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadAtIsNull(Long userId);
    List<Notification> findByUserIdAndReadAtIsNull(Long userId);

    /** instance_id nao tem FK: remove as notificacoes das instancias de um formulario. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Notification n where n.instanceId in "
            + "(select i.id from ProcessInstance i where i.formId = :formId)")
    void deleteByProcessFormId(@Param("formId") Long formId);
}
