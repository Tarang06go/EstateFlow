package com.estateflow.service;

import com.estateflow.dao.*;import com.estateflow.exception.*;import com.estateflow.model.*;import com.estateflow.security.Authorizer;import com.estateflow.support.TestAuth;
import org.junit.jupiter.api.Test;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;

class InspectionServiceTest {
 private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"),ZoneOffset.UTC);
 private InspectionService service(InspectionDao dao,PropertyDao props,AgreementDao agreements,Authorizer auth){return new InspectionService(dao,props,agreements,mock(MaintenanceService.class),new PassThroughTransactionManager(),AuditService.noop(),CLOCK,auth);}
 @Test void createValidatesAndPersistsStructuredFindings(){InspectionDao dao=mock(InspectionDao.class);PropertyDao props=mock(PropertyDao.class);AgreementDao agreements=mock(AgreementDao.class);Property p=new Property();p.setPropertyId(5L);when(props.findById(5L)).thenReturn(Optional.of(p));InspectionRecord saved=new InspectionRecord(8L,5L,null,LocalDate.of(2026,10,10),InspectionType.ROUTINE,"DRAFT",null,1L,null,null,List.of());when(dao.isAvailable()).thenReturn(true);when(dao.insert(eq(5L),isNull(),any(),eq(InspectionType.ROUTINE),isNull(),eq(1L),anyList())).thenReturn(saved);
  var result=assertDoesNotThrow(()->service(dao,props,agreements,TestAuth.admin()).create(5L,null,LocalDate.of(2026,10,10),InspectionType.ROUTINE,null,List.of(new InspectionFinding(null,null,"Roof",InspectionCondition.FAIR,"Loose tile",null,null))));assertEquals(8L,result.id());verify(dao).insert(eq(5L),isNull(),any(),eq(InspectionType.ROUTINE),isNull(),eq(1L),argThat(list->list.size()==1&&list.getFirst().area().equals("Roof")));
 }
 @Test void rejectsFutureInspectionDateAndInvalidFinding(){InspectionDao dao=mock(InspectionDao.class);when(dao.isAvailable()).thenReturn(true);PropertyDao props=mock(PropertyDao.class);Property p=new Property();p.setPropertyId(5L);when(props.findById(5L)).thenReturn(Optional.of(p));var service=service(dao,props,mock(AgreementDao.class),TestAuth.admin());
  assertThrows(ValidationException.class,()->service.create(5L,null,LocalDate.of(2026,10,11),InspectionType.ROUTINE,null,List.of()));assertThrows(ValidationException.class,()->service.create(5L,null,LocalDate.of(2026,10,10),InspectionType.ROUTINE,null,List.of(new InspectionFinding(null,null,"",InspectionCondition.GOOD,"",null,null))));verify(dao,never()).insert(any(),any(),any(),any(),any(),any(),any());
 }
 @Test void viewerCannotCreateOrReadThroughMissingPermission(){InspectionDao dao=mock(InspectionDao.class);var service=service(dao,mock(PropertyDao.class),mock(AgreementDao.class),TestAuth.as(com.estateflow.model.enums.Role.VIEWER));assertThrows(AuthorizationException.class,()->service.create(5L,null,LocalDate.now(CLOCK),InspectionType.ROUTINE,null,List.of()));verifyNoInteractions(dao);}
}
