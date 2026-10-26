package com.estateflow.dao.jdbc;
import com.estateflow.dao.InspectionDao;
import com.estateflow.db.DatabaseManager;
import com.estateflow.model.*;
import java.sql.*; import java.time.*; import java.util.*;
public final class JdbcInspectionDao extends BaseJdbcDao implements InspectionDao {
 public JdbcInspectionDao(DatabaseManager db){super(db);}
 @Override public boolean isAvailable(){return exists("check inspection table","SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='inspection'",Binder.NONE);}
 @Override public InspectionRecord insert(Long property,Long agreement,LocalDate date,InspectionType type,String summary,Long actor,List<InspectionFinding> findings){
  long id=insertReturningKey("insert inspection","INSERT INTO inspection(property_id,agreement_id,inspection_date,inspection_type,summary,created_by) VALUES(?,?,?,?,?,?)",ps->{ps.setLong(1,property); nullable(ps,2,agreement);ps.setObject(3,date);ps.setString(4,type.name());ps.setString(5,summary);nullable(ps,6,actor);});
  for(InspectionFinding f:findings) insertFinding(id,f); return find(id).orElseThrow();
 }
 @Override public List<InspectionRecord> search(Long property){List<Long> ids=queryList("search inspections","SELECT inspection_id FROM inspection WHERE (? IS NULL OR property_id=?) ORDER BY inspection_date DESC,inspection_id DESC LIMIT 500",ps->{nullable(ps,1,property);nullable(ps,2,property);},rs->rs.getLong(1));return ids.stream().map(this::find).flatMap(Optional::stream).toList();}
 @Override public Optional<InspectionRecord> find(long id){return queryOne("find inspection","SELECT inspection_id,property_id,agreement_id,inspection_date,inspection_type,status,summary,created_by,created_at,completed_at FROM inspection WHERE inspection_id=?",ps->ps.setLong(1,id),rs->{long key=rs.getLong("inspection_id"); return new InspectionRecord(key,rs.getLong("property_id"),longOrNull(rs,"agreement_id"),date(rs,"inspection_date"),InspectionType.valueOf(rs.getString("inspection_type")),rs.getString("status"),rs.getString("summary"),longOrNull(rs,"created_by"),dateTime(rs,"created_at"),dateTime(rs,"completed_at"),findings(key));});}
 private List<InspectionFinding> findings(long id){return queryList("find inspection findings","SELECT finding_id,inspection_id,area,condition_rating,observation,follow_up_action,maintenance_request_id FROM inspection_finding WHERE inspection_id=? ORDER BY finding_id",ps->ps.setLong(1,id),rs->new InspectionFinding(rs.getLong("finding_id"),rs.getLong("inspection_id"),rs.getString("area"),InspectionCondition.valueOf(rs.getString("condition_rating")),rs.getString("observation"),rs.getString("follow_up_action"),longOrNull(rs,"maintenance_request_id")));}
 @Override public int complete(long id){return executeUpdate("complete inspection","UPDATE inspection SET status='COMPLETED',completed_at=UTC_TIMESTAMP() WHERE inspection_id=? AND status='DRAFT'",ps->ps.setLong(1,id));}
 @Override public InspectionFinding addFinding(long id,InspectionFinding f){return insertFinding(id,f);}
 private InspectionFinding insertFinding(long id,InspectionFinding f){long key=insertReturningKey("insert finding","INSERT INTO inspection_finding(inspection_id,area,condition_rating,observation,follow_up_action) VALUES(?,?,?,?,?)",ps->{ps.setLong(1,id);ps.setString(2,f.area());ps.setString(3,f.condition().name());ps.setString(4,f.observation());ps.setString(5,f.followUpAction());});return new InspectionFinding(key,id,f.area(),f.condition(),f.observation(),f.followUpAction(),null);}
 @Override public int linkMaintenance(long inspection,long finding,long request){return executeUpdate("link finding to maintenance","UPDATE inspection_finding f JOIN inspection i ON i.inspection_id=f.inspection_id JOIN maintenance_request m ON m.request_id=? AND m.property_id=i.property_id SET f.maintenance_request_id=? WHERE f.inspection_id=? AND f.finding_id=? AND f.maintenance_request_id IS NULL",ps->{ps.setLong(1,request);ps.setLong(2,request);ps.setLong(3,inspection);ps.setLong(4,finding);});}
 private static void nullable(PreparedStatement p,int i,Long x)throws SQLException{if(x==null)p.setNull(i,Types.BIGINT);else p.setLong(i,x);}
}
