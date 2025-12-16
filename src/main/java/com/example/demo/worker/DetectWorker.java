package com.example.demo.worker;

import com.example.demo.entity.DetectRecord;
import com.example.demo.entity.DetectStatus;
import com.example.demo.entity.DetectTask;
import com.example.demo.repository.DetectRecordRepository;
import com.example.demo.repository.DetectTaskRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class DetectWorker {

    private static final Logger log = LoggerFactory.getLogger(DetectWorker.class);

    private final DetectTaskRepository detectTaskRepository;
    private final DetectRecordRepository detectRecordRepository;
    private final RestTemplate restTemplate;

    @Value("${yolo.api.base-url:http://localhost:5000}")
    private String yoloApiBaseUrl;

    @Value("${storage.base-dir:files}")
    private String storageBaseDir;

    @Value("${worker.lock-id:worker-1}")
    private String workerId;

    @Value("${worker.retry-delay-seconds:30}")
    private long retryDelaySeconds;

    public DetectWorker(DetectTaskRepository detectTaskRepository,
                        DetectRecordRepository detectRecordRepository,
                        RestTemplate restTemplate) {
        this.detectTaskRepository = detectTaskRepository;
        this.detectRecordRepository = detectRecordRepository;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${worker.delay-ms:1000}")
    @Transactional
    public void processQueue() {
        Optional<DetectTask> taskOpt = detectTaskRepository.lockNextPending(DetectStatus.PENDING.name(), Instant.now());
        if (taskOpt.isEmpty()) {
            return;
        }

        DetectTask task = taskOpt.get();
        task.setStatus(DetectStatus.RUNNING);
        task.setLockedAt(Instant.now());
        task.setLockedBy(workerId);
        detectTaskRepository.save(task);

        DetectRecord record = detectRecordRepository.findByRequestId(task.getRequestId())
                .orElseThrow(() -> new IllegalStateException("Record missing for task"));
        record.setStatus(DetectStatus.RUNNING);
        detectRecordRepository.save(record);

        try {
            processTask(record, task);
        } catch (Exception ex) {
            log.error("Failed to process task {}", task.getRequestId(), ex);
            handleFailure(task, record, ex.getMessage());
        }
    }

    private void processTask(DetectRecord record, DetectTask task) throws IOException {
        Path sourcePath = resolvePath(record.getOriginalPath());
        FileSystemResource resource = new FileSystemResource(sourcePath);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = restTemplate.postForObject(yoloApiBaseUrl + "/api/identify", requestEntity, Map.class);

        ResponseEntity<byte[]> imageResponse = restTemplate.getForEntity(yoloApiBaseUrl + "/api/result_image", byte[].class);
        saveSuccess(record, task, result, imageResponse.getBody());
    }

    private void saveSuccess(DetectRecord record, DetectTask task, Map<String, Object> result, byte[] imageBytes) throws IOException {
        Path resultDir = Paths.get(storageBaseDir, "result");
        Files.createDirectories(resultDir);
        Path target = resultDir.resolve(record.getRequestId() + ".jpg");
        Files.write(target, imageBytes);

        record.setStatus(DetectStatus.SUCCESS);
        record.setResultPath("/files/result/" + target.getFileName());
        if (result != null) {
            record.setDiseaseName((String) result.getOrDefault("disease_name", ""));
            Object confidence = result.get("confidence");
            if (confidence instanceof Number number) {
                record.setConfidence(BigDecimal.valueOf(number.doubleValue()));
            }
            record.setAdvice((String) result.getOrDefault("advice", null));
            record.setIntro((String) result.getOrDefault("intro", null));
        }
        detectRecordRepository.save(record);

        task.setStatus(DetectStatus.SUCCESS);
        task.setNextRunAt(Instant.now());
        detectTaskRepository.save(task);
    }

    private void handleFailure(DetectTask task, DetectRecord record, String errorMessage) {
        record.setStatus(DetectStatus.FAILED);
        record.setErrorMessage(errorMessage);
        detectRecordRepository.save(record);

        task.setStatus(DetectStatus.FAILED);
        task.setRetryCount(task.getRetryCount() + 1);
        task.setLastError(errorMessage);
        task.setNextRunAt(Instant.now().plusSeconds(retryDelaySeconds));
        detectTaskRepository.save(task);
    }

    private Path resolvePath(String publicPath) {
        String filename = Paths.get(publicPath).getFileName().toString();
        return Paths.get(storageBaseDir, "original", filename);
    }
}
