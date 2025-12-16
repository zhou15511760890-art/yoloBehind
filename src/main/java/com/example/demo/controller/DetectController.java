package com.example.demo.controller;

import com.example.demo.dto.DetectResultResponse;
import com.example.demo.dto.DetectUploadResponse;
import com.example.demo.service.DetectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/detect")
@Validated
public class DetectController {

    private final DetectService detectService;

    public DetectController(DetectService detectService) {
        this.detectService = detectService;
    }

    @PostMapping
    public ResponseEntity<DetectUploadResponse> create(@RequestPart("file") MultipartFile file, Authentication authentication) throws Exception {
        String userId = authentication.getName();
        return ResponseEntity.ok(detectService.createRequest(userId, file));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<DetectResultResponse> getResult(@PathVariable String requestId) {
        return ResponseEntity.ok(detectService.getResult(requestId));
    }
}
