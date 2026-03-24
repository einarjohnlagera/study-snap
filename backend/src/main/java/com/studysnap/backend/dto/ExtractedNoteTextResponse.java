package com.studysnap.backend.dto;

public record ExtractedNoteTextResponse(
        String inputType,
        String extractedText,
        ExtractionMeta meta
) {
    public record ExtractionMeta(
            Double ocrConfidence,
            boolean lowConfidence
    ) {
    }
}
