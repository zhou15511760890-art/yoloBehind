package com.example.demo.repository;

import com.example.demo.entity.DetectRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectRecordRepository extends JpaRepository<DetectRecord, Long> {
    Optional<DetectRecord> findByRequestId(String requestId);
}
