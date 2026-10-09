package app.harmonis.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProcessTaskRepository extends JpaRepository<ProcessTask, Long> {

    List<ProcessTask> findByInstanceIdOrderByStepIndexAscCreatedAtAsc(Long instanceId);

    List<ProcessTask> findByInstanceIdAndStepIndex(Long instanceId, int stepIndex);

    long countByInstanceIdAndStepIndexAndStatus(Long instanceId, int stepIndex, String status);

    /** Tarefas pendentes de instancias pendentes (candidatas a caixa de aprovacao). */
    @Query("select t from ProcessTask t where t.status = 'pending' and t.instance.status = 'pending'")
    List<ProcessTask> findOpenTasks();
}
