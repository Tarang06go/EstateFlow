package com.estateflow.service;

import com.estateflow.dao.*;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.model.enums.MaintenancePriority;
import com.estateflow.security.*;
import java.time.*;
import java.util.*;

/** Structured property inspections. Completed observations remain immutable and searchable. */
public final class InspectionService {
    private final InspectionDao dao; private final PropertyDao properties; private final AgreementDao agreements;
    private final MaintenanceService maintenance; private final TransactionManager tx; private final AuditService audit; private final Clock clock; private final Authorizer auth;
    public InspectionService(InspectionDao dao,PropertyDao properties,AgreementDao agreements,MaintenanceService maintenance,TransactionManager tx,AuditService audit,Clock clock,Authorizer auth){this.dao=dao;this.properties=properties;this.agreements=agreements;this.maintenance=maintenance;this.tx=tx;this.audit=audit;this.clock=clock;this.auth=auth;}
    public boolean isAvailable(){auth.require(Permission.VIEW_DATA);return dao.isAvailable();}
    public List<InspectionRecord> search(Long propertyId){auth.require(Permission.VIEW_DATA);ensure();return dao.search(propertyId);}
    public InspectionRecord get(long id) throws EntityNotFoundException {auth.require(Permission.VIEW_DATA);ensure();return dao.find(id).orElseThrow(()->new EntityNotFoundException("Inspection no longer exists."));}
    public InspectionRecord create(Long propertyId,Long agreementId,LocalDate date,InspectionType type,String summary,List<InspectionFinding> findings) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);ensure();
        if(propertyId==null||properties.findById(propertyId).filter(p->p.isActive()).isEmpty()) throw new ValidationException("Choose an active property.","property");
        if(agreementId!=null&&agreements.findById(agreementId).filter(a->a.getPropertyId().equals(propertyId)).isEmpty()) throw new ValidationException("The lease must belong to the selected property.","agreement");
        if(date==null||date.isAfter(LocalDate.now(clock))) throw new ValidationException("Inspection date is required and cannot be in the future.","date");
        if(type==null) throw new ValidationException("Choose an inspection type.","type");
        if(summary!=null&&summary.length()>500) throw new ValidationException("Summary cannot exceed 500 characters.","summary");
        List<InspectionFinding> validated=new ArrayList<>(); if(findings!=null) for(InspectionFinding f:findings) validated.add(validateFinding(f));
        if(validated.size()>100) throw new ValidationException("An inspection cannot have more than 100 findings.","findings");
        AppUser actor=auth.require(Permission.MANAGE_RECORDS);
        return tx.inTransaction(()->{InspectionRecord row=dao.insert(propertyId,agreementId,date,type,summary,actor.id(),validated);audit.record("INSPECTION",row.id(),"CREATED",null,AuditService.details("propertyId",propertyId,"type",type,"findings",validated.size()));return row;});
    }
    public InspectionFinding addFinding(long inspectionId,InspectionFinding finding)throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);ensure(); InspectionRecord row=get(inspectionId); if(!"DRAFT".equals(row.status())) throw new BusinessRuleException("Completed inspections are preserved as history and cannot be edited.","INSP-02");
        InspectionFinding f=validateFinding(finding); return tx.inTransaction(()->{InspectionFinding saved=dao.addFinding(inspectionId,f);audit.record("INSPECTION",inspectionId,"FINDING_ADDED",null,AuditService.details("area",f.area(),"condition",f.condition()));return saved;});
    }
    public void complete(long id)throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);ensure();InspectionRecord row=get(id);if(!"DRAFT".equals(row.status()))throw new BusinessRuleException("This inspection is already complete.","INSP-03");if(row.findings().isEmpty())throw new BusinessRuleException("Add at least one checklist finding before completing the inspection.","INSP-04");
        tx.runInTransaction(()->{if(dao.complete(id)==0)throw new BusinessRuleException("The inspection changed before it could be completed.","INSP-03");audit.record("INSPECTION",id,"COMPLETED",null,AuditService.details("completedAt",LocalDateTime.now(clock)));});
    }
    /** Creates a normal request in the established maintenance workflow and links it to the finding. */
    public InspectionRecord createMaintenanceFollowUp(long inspectionId,long findingId)throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS);ensure();InspectionRecord record=get(inspectionId);InspectionFinding finding=record.findings().stream().filter(f->Objects.equals(f.id(),findingId)).findFirst().orElseThrow(()->new EntityNotFoundException("Inspection finding no longer exists."));
        if(finding.maintenanceRequestId()!=null)throw new BusinessRuleException("This finding is already linked to a maintenance request.","INSP-05");
        String title=("Inspection: "+finding.area()).substring(0,Math.min(120,("Inspection: "+finding.area()).length()));
        var request=new com.estateflow.model.MaintenanceRequest();request.setPropertyId(record.propertyId());request.setTitle(title);request.setDescription(finding.observation()+ (finding.followUpAction()==null?"":"\nFollow-up: "+finding.followUpAction()));request.setPriority(finding.condition()==InspectionCondition.URGENT?MaintenancePriority.HIGH:MaintenancePriority.MEDIUM);request.setReportedDate(LocalDate.now(clock));
        var created=maintenance.open(request);
        tx.runInTransaction(()->{if(dao.linkMaintenance(inspectionId,findingId,created.getRequestId())==0)throw new BusinessRuleException("The follow-up could not be linked to the inspection finding.","INSP-06");audit.record("INSPECTION",inspectionId,"FOLLOW_UP_LINKED",null,AuditService.details("findingId",findingId,"maintenanceRequestId",created.getRequestId()));});
        return get(inspectionId);
    }
    private void ensure(){if(!dao.isAvailable())throw new ConfigurationException("Inspection history is not ready. Apply db/migrations/v1.6_documents_inspections.sql manually and restart the app.");}
    private static InspectionFinding validateFinding(InspectionFinding f) throws ValidationException {if(f==null)throw new ValidationException("Finding details are required.","finding");String area=f.area()==null?"":f.area().trim();String obs=f.observation()==null?"":f.observation().trim();if(area.isEmpty()||area.length()>80)throw new ValidationException("Area is required (up to 80 characters).","area");if(obs.isEmpty()||obs.length()>500)throw new ValidationException("Observation is required (up to 500 characters).","observation");if(f.condition()==null)throw new ValidationException("Choose a condition.","condition");String action=f.followUpAction()==null?null:f.followUpAction().trim();if(action!=null&&action.isEmpty())action=null;if(action!=null&&action.length()>500)throw new ValidationException("Follow-up action cannot exceed 500 characters.","followUp");return new InspectionFinding(null,null,area,f.condition(),obs,action,null);}
}
