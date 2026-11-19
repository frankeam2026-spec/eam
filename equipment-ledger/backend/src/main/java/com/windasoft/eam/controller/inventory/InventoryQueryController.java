package com.windasoft.eam.controller.inventory;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.inventory.InventoryQueryRow;
import com.windasoft.eam.service.inventory.InventoryQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/queries")
public class InventoryQueryController {
  private final InventoryQueryService service;

  public InventoryQueryController(InventoryQueryService service) {
    this.service = service;
  }

  @GetMapping("/{kind}")
  public InventoryQueryService.Result list(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(kind, q, filters, page, size);
  }

  @GetMapping("/{kind}/export")
  public DataResponse<List<InventoryQueryRow>> export(
      @PathVariable String kind,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return new DataResponse<>(service.export(kind, q, filters));
  }
}
