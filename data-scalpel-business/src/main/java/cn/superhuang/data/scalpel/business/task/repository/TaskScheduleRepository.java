package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskScheduleRepository extends JpaRepository<TaskSchedule, UUID> {

    List<TaskSchedule> findAllByTaskIdOrderByNameAsc(UUID taskId);

    boolean existsByTaskIdAndName(UUID taskId, String name);

    boolean existsByTaskIdAndNameAndIdNot(UUID taskId, String name, UUID id);

    void deleteAllByTaskId(UUID taskId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select schedule from TaskSchedule schedule where schedule.id = :id")
    Optional<TaskSchedule> findByIdForUpdate(@Param("id") UUID id);
}
