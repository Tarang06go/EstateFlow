package com.estateflow.model;

import java.time.LocalDateTime;

/** Safe document metadata; the generated storage name never comes from the original file name. */
public record DocumentRecord(Long id, Long propertyId, Long agreementId, Long tenantId, Long maintenanceRequestId, Long workOrderId,
                             DocumentCategory category, String displayName, String storageName, String mediaType,
                             long fileSize, LocalDateTime uploadedAt, Long uploadedBy, LocalDateTime archivedAt) { }
