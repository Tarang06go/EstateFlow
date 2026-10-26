package com.estateflow.model;

public record InspectionFinding(Long id, Long inspectionId, String area, InspectionCondition condition,
                                String observation, String followUpAction, Long maintenanceRequestId) { }
