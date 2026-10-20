package com.windasoft.eam.controller.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.service.business.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/business-config")
public class BusinessConfigController {
  private final BusinessConfigService service;
  private final BusinessReferenceService refs;
  private final BusinessPublicationService publication;

  public BusinessConfigController(
      BusinessConfigService service,
      BusinessReferenceService refs,
      BusinessPublicationService publication) {
    this.service = service;
    this.refs = refs;
    this.publication = publication;
  }

  @GetMapping("/objects")
  public Object objects() {
    return Map.of("data", service.objects());
  }

  @GetMapping("/objects/{object}")
  public Object configuration(@PathVariable String object) {
    return service.configuration(object);
  }

  @PutMapping("/objects/{object}/draft")
  public Object save(@PathVariable String object, @RequestBody JsonNode body) {
    return service.save(object, body);
  }

  @PostMapping("/objects/{object}/publish")
  public Object publish(@PathVariable String object, @RequestBody JsonNode body) {
    return publication.publish(object, body.path("version").asInt(-1));
  }

  @GetMapping("/objects/{object}/versions")
  public Object versions(@PathVariable String object) {
    return Map.of("data", service.versions(object));
  }

  @PostMapping("/objects/{object}/restore")
  public Object restore(@PathVariable String object, @RequestBody JsonNode body) {
    return service.restore(object, body.path("target").asInt(), body.path("version").asInt(-1));
  }

  @GetMapping("/objects/{object}/events")
  public Object events(@PathVariable String object) {
    return Map.of("data", service.events(object));
  }

  @GetMapping("/runtime/{object}")
  public Object runtime(@PathVariable String object) {
    refs.access(object);
    return service.runtime(object);
  }

  @GetMapping("/runtime/{object}/options")
  public Object options(
      @PathVariable String object,
      @RequestParam String source,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean inactive) {
    return refs.choices(object, source, q, page, size, inactive);
  }
  @RequestMapping({"/dictionaries", "/dictionaries/{*path}"})
  public void retiredDictionaries() {
    throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.GONE, "选项字典已移除，请直接在字段中维护选项");
  }
}
