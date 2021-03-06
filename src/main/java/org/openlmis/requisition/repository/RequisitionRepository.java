package org.openlmis.requisition.repository;

import org.openlmis.requisition.domain.Requisition;
import org.openlmis.requisition.repository.custom.RequisitionRepositoryCustom;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.UUID;

public interface RequisitionRepository extends
        PagingAndSortingRepository<Requisition, UUID>,
        RequisitionRepositoryCustom {

}
