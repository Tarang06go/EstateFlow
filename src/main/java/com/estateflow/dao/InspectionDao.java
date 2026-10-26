package com.estateflow.dao;
import com.estateflow.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface InspectionDao {
 boolean isAvailable(); InspectionRecord insert(Long propertyId,Long agreementId,LocalDate date,InspectionType type,String summary,Long actor,List<InspectionFinding> findings);
 List<InspectionRecord> search(Long propertyId); Optional<InspectionRecord> find(long id); int complete(long id); InspectionFinding addFinding(long inspectionId,InspectionFinding finding); int linkMaintenance(long inspectionId,long findingId,long requestId);
}
