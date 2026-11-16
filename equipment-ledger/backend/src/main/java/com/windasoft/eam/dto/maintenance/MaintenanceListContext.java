package com.windasoft.eam.dto.maintenance;

import java.util.List;

public record MaintenanceListContext(List<MaintenanceOrganizationOption> organizations) {
  public MaintenanceListContext { organizations = List.copyOf(organizations); }
}
