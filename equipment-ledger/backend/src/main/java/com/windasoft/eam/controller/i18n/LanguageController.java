package com.windasoft.eam.controller.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.service.i18n.LanguageService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LanguageController {
  private final LanguageService service;

  public LanguageController(LanguageService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/i18n/languages")
  public Object languages() {
    return Map.of("data", service.catalog(false));
  }

  @GetMapping("/api/v1/i18n/resources/{locale}")
  public ResponseEntity<?> resource(
      @PathVariable String locale,
      @RequestHeader(value = "If-None-Match", required = false) String match) {
    var value = service.resource(locale);
    String tag = "\"" + value.get("locale") + "-" + value.get("version") + "\"";
    if (tag.equals(match)) return ResponseEntity.status(304).eTag(tag).build();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .eTag(tag)
        .body(Map.of("data", value));
  }

  @PatchMapping("/api/v1/auth/settings/locale")
  public Object preference(
      @jakarta.validation.Valid @RequestBody com.windasoft.eam.dto.i18n.LocalePreferenceForm form) {
    return Map.of("data", service.savePreference(form.locale()));
  }

  @GetMapping("/api/v1/system/languages")
  public Object management() {
    return Map.of("data", service.management());
  }

  @GetMapping("/api/v1/system/languages/template")
  public Object template(@RequestParam(defaultValue = "fr-FR") String locale) {
    return service.template(locale);
  }

  @PostMapping("/api/v1/system/languages/imports/validate")
  public Object validate(@RequestBody JsonNode pack) {
    return Map.of("data", service.validate(pack));
  }

  @PutMapping("/api/v1/system/languages/{locale}")
  public Object publish(@PathVariable String locale, @RequestBody JsonNode body) {
    return Map.of(
        "data", service.publish(locale, body.path("rowVersion").asInt(-1), body.path("package")));
  }

  @PatchMapping("/api/v1/system/languages/{locale}/state")
  public Object state(@PathVariable String locale, @RequestBody JsonNode body) {
    service.state(locale, body.path("rowVersion").asInt(-1), body.path("enabled").asBoolean());
    return Map.of("data", true);
  }

  @PostMapping("/api/v1/system/languages/{locale}/restore")
  public Object restore(@PathVariable String locale, @RequestBody JsonNode body) {
    service.restore(locale, body.path("rowVersion").asInt(-1));
    return Map.of("data", true);
  }
}
