package com.windasoft.eam.inventory;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory/receipts")
public class ReceiptController {
  private final ReceiptService service;

  public ReceiptController(ReceiptService service) {
    this.service = service;
  }

  @GetMapping
  public Object list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(q, filters, page, size);
  }

  @GetMapping("/export")
  public Object export(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "{}") String filters) {
    return Map.of("data", service.export(q, filters));
  }

  @GetMapping("/choices")
  public Object choices() {
    return Map.of("data", service.choices());
  }

  @GetMapping("/materials")
  public Object materials(@RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return service.materials(q, page, size);
  }

  @GetMapping("/{id}")
  public Object detail(@PathVariable long id) {
    return Map.of("data", service.detail(id));
  }

  @PostMapping
  public Object create(@RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(null, body));
  }

  @PutMapping("/{id}")
  public Object update(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.save(id, body));
  }

  @PostMapping("/{id}/confirm")
  public Object confirm(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.confirm(id, body));
  }

  @DeleteMapping("/{id}")
  public Object delete(@PathVariable long id, @RequestBody Map<String, Object> body) {
    service.delete(id, body);
    return Map.of("data", true);
  }

  @PostMapping("/{id}/allocate-fees")
  public Object allocate(@PathVariable long id, @RequestBody Map<String, Object> body) {
    return Map.of("data", service.allocateFees(id, body));
  }
}
