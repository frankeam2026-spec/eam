package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.system.PersonalSettingsForm;
import com.windasoft.eam.service.system.PersonalSettingsService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/settings")
public class PersonalSettingsController {
  private final PersonalSettingsService service;

  public PersonalSettingsController(PersonalSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public Map<String, Object> settings() {
    return service.settings();
  }

  @PutMapping
  public Map<String, Object> save(@Valid @RequestBody PersonalSettingsForm form) {
    return service.save(form);
  }
}
