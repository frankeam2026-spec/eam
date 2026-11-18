package com.windasoft.eam.controller.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.i18n.LanguageContracts.LanguagePack;
import com.windasoft.eam.dto.i18n.LanguageContracts.LanguageView;
import com.windasoft.eam.dto.i18n.LanguageContracts.Preference;
import com.windasoft.eam.dto.i18n.LanguageContracts.Publish;
import com.windasoft.eam.dto.i18n.LanguageContracts.Resource;
import com.windasoft.eam.dto.i18n.LanguageContracts.State;
import com.windasoft.eam.dto.i18n.LanguageContracts.Validation;
import com.windasoft.eam.dto.i18n.LanguageContracts.Version;
import com.windasoft.eam.dto.i18n.LocalePreferenceForm;
import com.windasoft.eam.service.i18n.LanguageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LanguageController {
  private final LanguageService service;

  public LanguageController(LanguageService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/i18n/languages")
  public DataResponse<List<LanguageView>> languages() {
    return new DataResponse<>(service.catalog(false));
  }

  @GetMapping("/api/v1/i18n/resources/{locale}")
  public ResponseEntity<DataResponse<Resource>> resource(
      @PathVariable String locale,
      @RequestHeader(value = "If-None-Match", required = false) String match) {
    var value = service.resource(locale);
    String tag = "\"" + value.locale() + "-" + value.version() + "\"";
    if (tag.equals(match)) return ResponseEntity.status(304).eTag(tag).build();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .eTag(tag)
        .body(new DataResponse<>(value));
  }

  @PatchMapping("/api/v1/auth/settings/locale")
  public DataResponse<Preference> preference(
      @jakarta.validation.Valid @RequestBody LocalePreferenceForm form) {
    return new DataResponse<>(service.savePreference(form.locale()));
  }

  @GetMapping("/api/v1/system/languages")
  public DataResponse<List<LanguageView>> management() {
    return new DataResponse<>(service.management());
  }

  @GetMapping("/api/v1/system/languages/template")
  public LanguagePack template(@RequestParam(defaultValue = "fr-FR") String locale) {
    return service.template(locale);
  }

  @PostMapping("/api/v1/system/languages/imports/validate")
  public DataResponse<Validation> validate(@RequestBody JsonNode pack) {
    return new DataResponse<>(service.validate(pack));
  }

  @PutMapping("/api/v1/system/languages/{locale}")
  public DataResponse<Validation> publish(
      @PathVariable String locale, @Valid @RequestBody Publish body) {
    return new DataResponse<>(service.publish(locale, body.rowVersion(), body.languagePackage()));
  }

  @PatchMapping("/api/v1/system/languages/{locale}/state")
  public DataResponse<Boolean> state(@PathVariable String locale, @Valid @RequestBody State body) {
    service.state(locale, body.rowVersion(), body.enabled());
    return new DataResponse<>(true);
  }

  @PostMapping("/api/v1/system/languages/{locale}/restore")
  public DataResponse<Boolean> restore(
      @PathVariable String locale, @Valid @RequestBody Version body) {
    service.restore(locale, body.rowVersion());
    return new DataResponse<>(true);
  }
}
