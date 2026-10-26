package com.estateflow.model;
import com.estateflow.model.enums.MaintenancePriority;import java.math.BigDecimal;import java.time.LocalDate;
public record WorkOrderRow(Long id,String propertyName,String vendorName,Long maintenanceRequestId,String title,MaintenancePriority priority,LocalDate dueDate,WorkOrderStatus status,BigDecimal estimatedCost,BigDecimal actualCost,Long expenseId) { }
