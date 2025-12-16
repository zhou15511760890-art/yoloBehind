package com.example.demo.repository;

import com.example.demo.entity.DetectStatus;
import com.example.demo.entity.DetectTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DetectTaskRepository extends JpaRepository<DetectTask, Long> {
    Optional<DetectTask> findByRequestId(String requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM detect_task WHERE status = :status AND next_run_at <= :now ORDER BY created_at ASC LIMIT 1", nativeQuery = true)
    Optional<DetectTask> lockNextPending(@Param("status") String status, @Param("now") Instant now);
}
