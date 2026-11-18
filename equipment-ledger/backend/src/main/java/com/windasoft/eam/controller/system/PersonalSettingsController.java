package com.windasoft.eam.controller.system;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.system.PersonalSettingsForm;
import com.windasoft.eam.dto.system.PersonalSettingsView;
import com.windasoft.eam.dto.system.ThemeView;
import com.windasoft.eam.service.system.PersonalSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/settings")
public class PersonalSettingsController {
  private final PersonalSettingsService service;

  public PersonalSettingsController(PersonalSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public DataResponse<PersonalSettingsView> settings() {
    return new DataResponse<>(service.settings());
  }

  @PutMapping
  public DataResponse<ThemeView> save(@Valid @RequestBody PersonalSettingsForm form) {
    return new DataResponse<>(service.save(form));
  }
}
