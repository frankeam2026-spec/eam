package com.windasoft.eam.service.experience;

import com.windasoft.eam.common.experience.ExperienceSupport;
import com.windasoft.eam.service.system.PermissionService;
import java.io.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExperienceExportService {
  private final PermissionService permissions;
  private final AnomalyService anomalies;
  private final MaterialCustodyService custody;

  public ExperienceExportService(
      PermissionService permissions, AnomalyService anomalies, MaterialCustodyService custody) {
    this.permissions = permissions;
    this.anomalies = anomalies;
    this.custody = custody;
  }

  public void authorize(String kind) {

    String menu =
        kind.equals("anomalies")
            ? "anomaly"
            : kind.equals("repair-plans") ? "repairPlan" : "materialCustody";
    permissions.require(menu, "导出");
  }

  public void write(String kind, String q, String filters, boolean personal, Writer output)
      throws IOException {
    authorize(kind);
    var writer = new PrintWriter(output);
    var fields =
        kind.equals("material-custody")
            ? List.of(
                "materialCode",
                "materialName",
                "specification",
                "remaining",
                "holderName",
                "warehouse",
                "taskCode",
                "remark")
            : List.of(
                "code",
                "equipmentCode",
                "equipmentName",
                "description",
                "status",
                "severity",
                kind.equals("anomalies") ? "discovered_at" : "planned_at");
    writer.write("\ufeff");
    writer.println(String.join(",", fields));
    for (int page = 1; ; page++) {
      var result =
          kind.equals("anomalies")
              ? anomalies.list(q, null, page, 100, false, filters)
              : kind.equals("repair-plans")
                  ? (Map<String, Object>) anomalies.planList(q, page, 100, filters)
                  : custody.list(personal, q, page, 100, filters);
      var records = ExperienceSupport.rows(result.get("data"));
      if (records.isEmpty()) break;
      for (var r : records) {
        var values = new ArrayList<String>();
        for (String f : fields) {
          String value = Objects.toString(r.get(f), "");
          if (value.matches("^[\\s]*[=+@-].*")) value = "'" + value;
          values.add("\"" + value.replace("\"", "\"\"") + "\"");
        }
        writer.println(String.join(",", values));
      }
      if ((long) page * 100 >= ((Number) result.get("total")).longValue()) break;
    }
    writer.flush();
  }
}
