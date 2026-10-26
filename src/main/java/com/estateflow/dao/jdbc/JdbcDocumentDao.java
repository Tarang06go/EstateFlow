package com.estateflow.dao.jdbc;

import com.estateflow.dao.DocumentDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.DocumentCategory;
import com.estateflow.model.DocumentRecord;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class JdbcDocumentDao extends BaseJdbcDao implements DocumentDao {
    private static final String COLUMNS = "document_id,property_id,agreement_id,tenant_id,maintenance_request_id,category,display_name,storage_name,media_type,file_size,uploaded_at,uploaded_by,archived_at";
    private static final RowMapper<DocumentRecord> MAPPER = rs -> new DocumentRecord(rs.getLong("document_id"),
            longOrNull(rs,"property_id"), longOrNull(rs,"agreement_id"), longOrNull(rs,"tenant_id"),
            longOrNull(rs,"maintenance_request_id"),hasColumn(rs,"work_order_id")?longOrNull(rs,"work_order_id"):null, DocumentCategory.valueOf(rs.getString("category")),
            rs.getString("display_name"),rs.getString("storage_name"),rs.getString("media_type"),rs.getLong("file_size"),
            dateTime(rs,"uploaded_at"),longOrNull(rs,"uploaded_by"),dateTime(rs,"archived_at"));
    public JdbcDocumentDao(DatabaseManager db) { super(db); }
    @Override public boolean isAvailable() { return exists("check document table", "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='property_document'", Binder.NONE); }
    @Override public boolean supportsWorkOrderLink() { return exists("check document work-order link", "SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='property_document' AND column_name='work_order_id'", Binder.NONE); }
    @Override public DocumentRecord insert(DocumentRecord d) {
        boolean hasWorkOrder=supportsWorkOrderLink();
        String sql=hasWorkOrder?"INSERT INTO property_document(property_id,agreement_id,tenant_id,maintenance_request_id,work_order_id,category,display_name,storage_name,media_type,file_size,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)":"INSERT INTO property_document(property_id,agreement_id,tenant_id,maintenance_request_id,category,display_name,storage_name,media_type,file_size,uploaded_by) VALUES(?,?,?,?,?,?,?,?,?,?)";
        long id=insertReturningKey("insert document", sql, ps -> {
            nullable(ps,1,d.propertyId()); nullable(ps,2,d.agreementId()); nullable(ps,3,d.tenantId()); nullable(ps,4,d.maintenanceRequestId());int i=5;if(hasWorkOrder)nullable(ps,i++,d.workOrderId());
            ps.setString(i++,d.category().name()); ps.setString(i++,d.displayName()); ps.setString(i++,d.storageName()); ps.setString(i++,d.mediaType()); ps.setLong(i++,d.fileSize()); nullable(ps,i,d.uploadedBy());
        }); return find(id).orElseThrow();
    }
    @Override public List<DocumentRecord> search(String query, boolean includeArchived) {
        String where=includeArchived?"":" AND archived_at IS NULL";
        String term=query==null||query.isBlank()?null:"%"+query.trim()+"%";
        String columns=columns();
        return queryList("search documents", "SELECT "+columns+" FROM property_document WHERE (? IS NULL OR display_name LIKE ? OR category LIKE ?)"+where+" ORDER BY uploaded_at DESC LIMIT 1000", ps->{
            if(term==null){ps.setNull(1,Types.VARCHAR);ps.setNull(2,Types.VARCHAR);ps.setNull(3,Types.VARCHAR);} else {ps.setString(1,term);ps.setString(2,term);ps.setString(3,term);}
        },MAPPER);
    }
    @Override public Optional<DocumentRecord> find(long id) { return queryOne("find document", "SELECT "+columns()+" FROM property_document WHERE document_id=?", ps->ps.setLong(1,id),MAPPER); }
    @Override public int archive(long id) { return executeUpdate("archive document", "UPDATE property_document SET archived_at=UTC_TIMESTAMP() WHERE document_id=? AND archived_at IS NULL",ps->ps.setLong(1,id)); }
    @Override public int markReplacement(long oldId,long newId) { return executeUpdate("mark replaced document", "UPDATE property_document SET archived_at=UTC_TIMESTAMP(), replaced_by_document_id=? WHERE document_id=? AND archived_at IS NULL",ps->{ps.setLong(1,newId);ps.setLong(2,oldId);}); }
    private static void nullable(PreparedStatement ps,int index,Long value) throws SQLException { if(value==null) ps.setNull(index,Types.BIGINT); else ps.setLong(index,value); }
    private String columns(){return supportsWorkOrderLink()?COLUMNS.replace("maintenance_request_id,category","maintenance_request_id,work_order_id,category"):COLUMNS;}
    private static boolean hasColumn(java.sql.ResultSet rs,String name)throws SQLException{var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++)if(name.equalsIgnoreCase(md.getColumnLabel(i)))return true;return false;}
}
