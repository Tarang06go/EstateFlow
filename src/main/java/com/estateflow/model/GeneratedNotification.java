package com.estateflow.model;
import java.math.BigDecimal;import java.time.LocalDate;
/** Computed in-app reminder. It is not a sent email, push message or persisted delivery record. */
public record GeneratedNotification(String key,NotificationType type,NotificationSeverity severity,String title,String propertyName,String detail,LocalDate dueDate,Long relatedId,BigDecimal amount,String deliveryState) { }
