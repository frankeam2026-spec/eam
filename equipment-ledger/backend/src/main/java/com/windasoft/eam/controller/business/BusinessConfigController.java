package com.windasoft.eam.controller.business;

import com.windasoft.eam.dto.business.BusinessConfigurationContracts.EventView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.ObjectOption;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.RestoreRequest;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.RuntimeView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SaveDraftRequest;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.SchemaView;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.VersionRequest;
import com.windasoft.eam.dto.business.BusinessConfigurationContracts.VersionView;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.business.BusinessPublicationService;
import com.windasoft.eam.service.business.BusinessReferenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
  public DataResponse<List<ObjectOption>> objects() {
    return new DataResponse<>(service.objects());
  }

  @GetMapping("/objects/{object}")
  public SchemaView configuration(@PathVariable String object) {
    return service.configuration(object);
  }

  @PutMapping("/objects/{object}/draft")
  public SchemaView save(@PathVariable String object, @Valid @RequestBody SaveDraftRequest body) {
    return service.save(object, body);
  }

  @PostMapping("/objects/{object}/publish")
  public SchemaView publish(@PathVariable String object, @Valid @RequestBody VersionRequest body) {
    return publication.publish(object, body.version());
  }

  @GetMapping("/objects/{object}/versions")
  public DataResponse<List<VersionView>> versions(@PathVariable String object) {
    return new DataResponse<>(service.versions(object));
  }

  @PostMapping("/objects/{object}/restore")
  public SchemaView restore(@PathVariable String object, @Valid @RequestBody RestoreRequest body) {
    return service.restore(object, body.target(), body.version());
  }

  @GetMapping("/objects/{object}/events")
  public DataResponse<List<EventView>> events(@PathVariable String object) {
    return new DataResponse<>(service.events(object));
  }

  @GetMapping("/runtime/{object}")
  public RuntimeView runtime(@PathVariable String object) {
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
