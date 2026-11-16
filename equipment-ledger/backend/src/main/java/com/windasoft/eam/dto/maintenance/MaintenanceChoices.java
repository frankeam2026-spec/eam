package com.windasoft.eam.dto.maintenance;

import com.windasoft.eam.dto.system.UserNameOption;
import java.util.List;

/** 兼容原选择器六字段；paged省略的大集合仍以空数组返回。 */
public record MaintenanceChoices(
    List<MaintenanceTypeView> types,
    List<MaintenanceLegacyStockOption> stocks,
    List<MaintenanceEquipmentOption> equipment,
    List<MaintenanceOrganizationOption> organizations,
    List<UserNameOption> users,
    List<MaintenanceCategoryOption> categories) {
  public MaintenanceChoices {
    types = List.copyOf(types);
    stocks = List.copyOf(stocks);
    equipment = List.copyOf(equipment);
    organizations = List.copyOf(organizations);
    users = List.copyOf(users);
    categories = List.copyOf(categories);
  }
}
