package com.estateflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InspectionRecord(Long id, Long propertyId, Long agreementId, LocalDate inspectionDate,
                               InspectionType type, String status, String summary, Long createdBy,
                               LocalDateTime createdAt, LocalDateTime completedAt, List<InspectionFinding> findings) {
    public InspectionRecord { findings = List.copyOf(findings); }
}
