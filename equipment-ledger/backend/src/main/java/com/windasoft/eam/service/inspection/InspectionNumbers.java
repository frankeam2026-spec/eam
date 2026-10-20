package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InspectionNumbers {
  private final InspectionRepository repository;

  public InspectionNumbers(InspectionRepository repository) {
    this.repository = repository;
  }

  String nextCode(long tenant, String prefix) {
    String month = now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    repository.nextMonthlyNumber("inspection-" + prefix + "-" + tenant, month);
    return prefix + "-" + month + "-" + String.format("%04d", repository.currentNumber());
  }
}
