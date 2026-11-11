package com.windasoft.eam.controller.inspection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.inspection.InspectionUpload;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.inspection.DailyInspectionPlanService;
import com.windasoft.eam.service.inspection.InspectionFileService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

class InspectionEntryTypeContractTest {
  /** 仅登记本批未改动的现有路由；不能以整个包或Object总数豁免新增接口。 */
  private static final Set<String> LEGACY =
      Set.of(
          "GET /daily-device-tasks",
          "POST /daily-plans/assignment-impact",
          "POST /daily-plans/preview",
          "POST /daily-plans/{id}/events",
          "POST /daily-plans/{id}/exemptions",
          "GET /daily-operation-teams",
          "POST /daily-operation-teams",
          "PUT /daily-operation-teams/{id}",
          "GET /list-context",
          "GET /choices",
          "GET /tasks",
          "GET /tasks/{id}",
          "POST /tasks/{id}/{action}",
          "POST /batch-review",
          "POST /export",
          "GET /{kind}",
          "GET /{kind}/{id}",
          "POST /{kind}",
          "PUT /{kind}/{id}",
          "DELETE /{kind}/{id}");

  @Test
  void newlyAddedOrMigratedEndpointsCannotHideDynamicModels() throws Exception {
    Path classes =
        Path.of(
            InspectionController.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path directory = classes.resolve("com/windasoft/eam/controller/inspection");
    var remaining = new HashSet<String>();
    try (var files = Files.walk(directory)) {
      for (Path file : files.filter(p -> p.toString().endsWith("Controller.class")).toList()) {
        String relative = classes.relativize(file).toString().replace('\\', '.').replace('/', '.');
        Class<?> controller = Class.forName(relative.substring(0, relative.length() - 6));
        for (var method : controller.getDeclaredMethods()) {
          var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
          if (mapping == null) continue;
          boolean existing =
              controller == InspectionController.class
                  && method.getReturnType() == Object.class
                  && mapping.path().length == 1
                  && mapping.method().length == 1
                  && LEGACY.contains(mapping.method()[0].name() + " " + mapping.path()[0]);
          if (existing) {
            remaining.add(mapping.method()[0].name() + " " + mapping.path()[0]);
            continue;
          }
          ContractTypes.assertExplicit(method.getGenericReturnType());
          for (var parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(RequestBody.class)) {
              ContractTypes.assertExplicit(parameter.getParameterizedType());
            }
          }
        }
      }
    }
    assertEquals(LEGACY, remaining, "入口完成迁移后必须删除对应存量条目，防止回退继续被豁免");
  }

  @Test
  void retiredEventOptionsHaveAnExplicitEmptyPageContract() throws Exception {
    ContractTypes.assertExplicit(
        InspectionController.class
            .getMethod("eventOptions", String.class, int.class, int.class)
            .getGenericReturnType());
    ContractTypes.assertExplicit(
        DailyInspectionPlanService.class
            .getMethod("dailyEventOptions", String.class, int.class, int.class)
            .getGenericReturnType());
  }

  @Test
  void uploadedFileHasAnExplicitControllerAndServiceResult() throws Exception {
    ContractTypes.assertExplicit(
        InspectionFileController.class
            .getMethod("upload", MultipartFile.class)
            .getGenericReturnType());
    ContractTypes.assertExplicit(
        InspectionFileService.class
            .getMethod("upload", InspectionUpload.class)
            .getGenericReturnType());
    ContractTypes.assertExplicit(InspectionUpload.class);
  }

  @Test
  void deviceCandidatesHaveAnExplicitControllerAndServiceResult() throws Exception {
    ContractTypes.assertExplicit(
        InspectionController.class
            .getMethod("dailyDeviceOptions", String.class, int.class, int.class)
            .getGenericReturnType());
    ContractTypes.assertExplicit(
        DailyInspectionPlanService.class
            .getMethod("dailyDeviceOptions", String.class, int.class, int.class)
            .getGenericReturnType());
    var rows =
        (ParameterizedType)
            EquipmentQueryRepository.class
                .getMethod(
                    "deviceCandidates",
                    long.class,
                    long.class,
                    PermissionScope.class,
                    String.class,
                    int.class,
                    int.class)
                .getGenericReturnType();
    assertEquals(Equipment.class, rows.getActualTypeArguments()[0]);
  }

  @Test
  void nestedMapsObjectsAndUnboundContainersFailWhileTypedPagesPass() throws Exception {
    for (String name :
        List.of("object", "map", "nested", "wildcard", "raw", "array", "inherited")) {
      var type = Invalid.class.getDeclaredMethod(name).getGenericReturnType();
      assertThrows(AssertionError.class, () -> ContractTypes.assertExplicit(type));
    }
    assertDoesNotThrow(
        () ->
            ContractTypes.assertExplicit(
                Valid.class.getDeclaredMethod("page").getGenericReturnType()));
  }

  private record Row(String id, String name) {}

  private record Hidden(Object value) {}

  private static class HiddenParent {
    Object value;
  }

  private static class HiddenChild extends HiddenParent {}

  private static class Valid {
    PageResult<Row> page() {
      return null;
    }
  }

  private static class Invalid {
    Object[] array() {
      return null;
    }

    HiddenChild inherited() {
      return null;
    }

    Object object() {
      return null;
    }

    DataResponse<List<Map<String, Object>>> map() {
      return null;
    }

    PageResult<Hidden> nested() {
      return null;
    }

    DataResponse<?> wildcard() {
      return null;
    }

    @SuppressWarnings("rawtypes")
    DataResponse raw() {
      return null;
    }
  }
}
