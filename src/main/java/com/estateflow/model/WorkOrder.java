package com.estateflow.model;
import com.estateflow.model.enums.MaintenancePriority;import java.math.BigDecimal;import java.time.LocalDate;import java.time.LocalDateTime;
public record WorkOrder(Long id,Long propertyId,Long maintenanceRequestId,Long vendorId,Long expenseId,String title,String scope,MaintenancePriority priority,LocalDate dueDate,WorkOrderStatus status,BigDecimal estimatedCost,BigDecimal actualCost,String completionNotes,Long createdBy,LocalDateTime createdAt,LocalDateTime updatedAt,LocalDateTime completedAt) { }
