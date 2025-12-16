package com.example.demo.service;

import com.example.demo.dto.DetectResultResponse;
import com.example.demo.dto.DetectUploadResponse;
import com.example.demo.entity.DetectRecord;
import com.example.demo.entity.DetectStatus;
import com.example.demo.entity.DetectTask;
import com.example.demo.repository.DetectRecordRepository;
import com.example.demo.repository.DetectTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class DetectService {

    private final DetectRecordRepository detectRecordRepository;
    private final DetectTaskRepository detectTaskRepository;

    @Value("${storage.base-dir:files}")
    private String storageBaseDir;

    public DetectService(DetectRecordRepository detectRecordRepository, DetectTaskRepository detectTaskRepository) {
        this.detectRecordRepository = detectRecordRepository;
        this.detectTaskRepository = detectTaskRepository;
    }

    public DetectUploadResponse createRequest(String userId, MultipartFile file) throws IOException {
        String requestId = UUID.randomUUID().toString();
        Path storageDir = Paths.get(storageBaseDir, "original");
        Files.createDirectories(storageDir);
        Path targetFile = storageDir.resolve(requestId + getExtension(file.getOriginalFilename()));
        file.transferTo(targetFile);

        DetectRecord record = new DetectRecord();
        record.setRequestId(requestId);
        record.setUserId(userId);
        record.setOriginalPath("/files/original/" + targetFile.getFileName());
        record.setStatus(DetectStatus.PENDING);
        detectRecordRepository.save(record);

        DetectTask task = new DetectTask();
        task.setRequestId(requestId);
        task.setStatus(DetectStatus.PENDING);
        task.setNextRunAt(Instant.now());
        detectTaskRepository.save(task);

        return new DetectUploadResponse(requestId);
    }

    public DetectResultResponse getResult(String requestId) {
        Optional<DetectRecord> recordOpt = detectRecordRepository.findByRequestId(requestId);
        DetectRecord record = recordOpt.orElseThrow(() -> new IllegalArgumentException("Request not found"));
        return DetectResultResponse.builder()
                .requestId(record.getRequestId())
                .status(record.getStatus())
                .diseaseName(record.getDiseaseName())
                .confidence(record.getConfidence())
                .advice(record.getAdvice())
                .intro(record.getIntro())
                .originalPath(record.getOriginalPath())
                .resultPath(record.getResultPath())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
