package com.estateflow.dto;

import java.time.LocalDateTime;

/** One locally generated assistant response with its provenance, generation time, and optional safe navigation action. */
public record AssistantReply(String question, String answer, String source, LocalDateTime generatedAt,
                             String navigationTarget) {
    public AssistantReply(String question, String answer, String source, LocalDateTime generatedAt) {
        this(question, answer, source, generatedAt, null);
    }
}
