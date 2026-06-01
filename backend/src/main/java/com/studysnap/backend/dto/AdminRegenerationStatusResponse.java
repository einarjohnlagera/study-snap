package com.studysnap.backend.dto;

public record AdminRegenerationStatusResponse(int total, int processed, int failed, boolean done) {
}
