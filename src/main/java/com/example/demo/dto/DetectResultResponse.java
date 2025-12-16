package com.example.demo.dto;

import com.example.demo.entity.DetectStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class DetectResultResponse {
    private final String requestId;
    private final DetectStatus status;
    private final String diseaseName;
    private final BigDecimal confidence;
    private final String advice;
    private final String intro;
    private final String originalPath;
    private final String resultPath;
    private final String errorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;
}
