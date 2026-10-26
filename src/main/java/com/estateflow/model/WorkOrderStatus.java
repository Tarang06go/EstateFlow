package com.estateflow.model;
public enum WorkOrderStatus {
 DRAFT, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED;
 public boolean canTransitionTo(WorkOrderStatus target){return switch(this){case DRAFT->target==ASSIGNED||target==CANCELLED;case ASSIGNED->target==IN_PROGRESS||target==CANCELLED;case IN_PROGRESS->target==COMPLETED||target==CANCELLED;case COMPLETED,CANCELLED->false;};}
}
