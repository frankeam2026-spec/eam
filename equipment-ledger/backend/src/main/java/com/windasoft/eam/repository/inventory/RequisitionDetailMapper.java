package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.dto.inventory.RequisitionDetail.Allocation;
import com.windasoft.eam.dto.inventory.RequisitionDetail.Event;
import com.windasoft.eam.dto.inventory.RequisitionDetail.Issue;
import com.windasoft.eam.dto.inventory.RequisitionDetail.Line;
import com.windasoft.eam.dto.inventory.RequisitionDetail.Reservation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Joined detail reads are explicit SQL; CRUD uses the header and line BaseMappers. */
@Mapper
public interface RequisitionDetailMapper {
  List<Line> lines(@Param("tenant") long tenant, @Param("id") long id);

  List<Reservation> reservations(@Param("tenant") long tenant, @Param("id") long id);

  List<Issue> issues(@Param("tenant") long tenant, @Param("id") long id);

  List<Allocation> allocations(@Param("tenant") long tenant, @Param("id") long id);

  List<Event> events(@Param("tenant") long tenant, @Param("id") long id);
}
