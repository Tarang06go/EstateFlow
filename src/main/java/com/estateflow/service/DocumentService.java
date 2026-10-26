package com.estateflow.service;

import com.estateflow.dao.DocumentDao;
import com.estateflow.dao.TransactionManager;
import com.estateflow.exception.*;
import com.estateflow.model.*;
import com.estateflow.security.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Authorized document metadata and private filesystem storage. */
public final class DocumentService {
    private final DocumentDao dao; private final DocumentStorage storage; private final TransactionManager tx;
    private final AuditService audit; private final Authorizer auth;
    public DocumentService(DocumentDao dao,DocumentStorage storage,TransactionManager tx,AuditService audit,Authorizer auth){this.dao=dao;this.storage=storage;this.tx=tx;this.audit=audit;this.auth=auth;}
    public boolean isAvailable(){auth.require(Permission.VIEW_DATA);return dao.isAvailable();}
    public List<DocumentRecord> search(String query,boolean archived){auth.require(Permission.VIEW_DATA);ensure();return dao.search(query,archived);}
    public DocumentRecord add(Path source,Long property,Long agreement,Long tenant,Long maintenance,Long workOrder,DocumentCategory category) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS); ensure(); validateLink(property,agreement,tenant,maintenance,workOrder); if(workOrder!=null&&!dao.supportsWorkOrderLink())throw new ConfigurationException("Linking documents to work orders requires the manual v1.7 migration.");if(category==null) throw new ValidationException("Choose a document category.","category");
        DocumentStorage.Stored file=storage.store(source); boolean saved=false;
        try {
            var user=auth.require(Permission.MANAGE_RECORDS);
            DocumentRecord d=new DocumentRecord(null,property,agreement,tenant,maintenance,workOrder,category,file.displayName(),file.storageName(),file.mediaType(),file.size(),LocalDateTime.now(ZoneOffset.UTC),user==null?null:user.id(),null);
            DocumentRecord result=tx.inTransaction(()->{DocumentRecord row=dao.insert(d);audit.record("DOCUMENT",row.id(),"UPLOADED",null,AuditService.details("category",category,"size",file.size()));return row;}); saved=true; return result;
        } finally { if(!saved) storage.remove(file.storageName()); }
    }
    public DocumentRecord replace(long oldId,Path source) throws EstateFlowException {
        auth.require(Permission.MANAGE_RECORDS); ensure();
        DocumentRecord old=dao.find(oldId).orElseThrow(()->new EntityNotFoundException("Document no longer exists."));
        if(old.archivedAt()!=null) throw new BusinessRuleException("An archived document cannot be replaced.","DOC-03");
        DocumentStorage.Stored file=storage.store(source); boolean saved=false;
        try {
            DocumentRecord next=new DocumentRecord(null,old.propertyId(),old.agreementId(),old.tenantId(),old.maintenanceRequestId(),old.workOrderId(),old.category(),file.displayName(),file.storageName(),file.mediaType(),file.size(),LocalDateTime.now(ZoneOffset.UTC),auth.require(Permission.MANAGE_RECORDS).id(),null);
            DocumentRecord result=tx.inTransaction(()->{DocumentRecord row=dao.insert(next); if(dao.markReplacement(oldId,row.id())==0) throw new EntityNotFoundException("Document was changed by another user."); audit.record("DOCUMENT",oldId,"REPLACED",null,AuditService.details("replacementId",row.id())); audit.record("DOCUMENT",row.id(),"UPLOADED",null,AuditService.details("replacesId",oldId)); return row;});
            saved=true; return result;
        } finally { if(!saved) storage.remove(file.storageName()); }
    }
    public Path open(long id) throws EstateFlowException { auth.require(Permission.VIEW_DATA); ensure(); DocumentRecord row=dao.find(id).orElseThrow(()->new EntityNotFoundException("Document no longer exists.")); if(row.archivedAt()!=null) throw new BusinessRuleException("Archived documents cannot be opened.","DOC-02"); return storage.resolve(row.storageName()); }
    public void archive(long id) throws EstateFlowException { auth.require(Permission.MANAGE_RECORDS);ensure(); DocumentRecord row=dao.find(id).orElseThrow(()->new EntityNotFoundException("Document no longer exists.")); if(row.archivedAt()!=null) return; tx.runInTransaction(()->{if(dao.archive(id)==0) throw new EntityNotFoundException("Document no longer exists.");audit.record("DOCUMENT",id,"ARCHIVED",null,AuditService.details("name",row.displayName()));}); }
    private void ensure(){if(!dao.isAvailable()) throw new ConfigurationException("Document storage is not ready. Apply db/migrations/v1.6_documents_inspections.sql manually and restart the app.");}
    private static void validateLink(Long p,Long a,Long t,Long m,Long w) throws ValidationException { int n=(p==null?0:1)+(a==null?0:1)+(t==null?0:1)+(m==null?0:1)+(w==null?0:1); if(n!=1)throw new ValidationException("Link the document to exactly one property, lease, tenant, maintenance request or work order.","link");if((p!=null&&p<1)||(a!=null&&a<1)||(t!=null&&t<1)||(m!=null&&m<1)||(w!=null&&w<1))throw new ValidationException("Related record ID must be positive.","link"); }
}
