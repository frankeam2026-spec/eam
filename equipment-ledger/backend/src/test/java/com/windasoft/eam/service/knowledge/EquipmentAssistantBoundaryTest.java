package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult;
import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import com.windasoft.eam.repository.knowledge.AiBusinessQueryRepository;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.inspection.InspectionListService;
import com.windasoft.eam.service.inventory.InventoryQueryService;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.repair.RepairQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EquipmentAssistantBoundaryTest {
  final ObjectMapper json = new ObjectMapper();

  @Test
  void equipmentUsesTypedServiceWithoutCallingTheGenericAiSqlExecutor() throws Exception {
    var generic = mock(AiBusinessQueryRepository.class);
    var equipment = mock(EquipmentQueryService.class);
    var service =
        new AiBusinessQueryService(
            generic,
            mock(PermissionService.class),
            mock(AuthContext.class),
            json,
            equipment,
            mock(RepairQueryService.class),
            mock(MaintenanceListService.class),
            mock(InspectionListService.class),
            mock(InventoryQueryService.class));
    when(equipment.queryAssistant(any()))
        .thenReturn(
            new EquipmentAssistantResult(
                new EquipmentAssistantResult.Count(123, 123),
                LocalDateTime.of(2026, 10, 30, 8, 0),
                null));
    var result =
        service.query(
            json.readTree("{\"domain\":\"equipment\",\"operation\":\"COUNT\"}"), null, 1, 20);
    assertEquals(123L, result.get("value"));
    assertEquals("设备台账", result.get("title"));
    assertEquals("2026-10-30 08:00", result.get("asOf"));
    assertThrows(IllegalArgumentException.class, () -> service.source("equipment"));
    verifyNoInteractions(generic);
  }

  @Test
  void legacyListAndNullTrendPeriodRemainCompatible() throws Exception {
    var plan =
        AiBusinessPlan.parse(json.readTree("{\"domain\":\"equipment\",\"operation\":\"LIST\"}"));
    var request = EquipmentAssistantAdapter.request(plan, null, 1, 20);
    var answer =
        new EquipmentAssistantResult(
            new EquipmentAssistantResult.ListData(
                List.of(
                    new EquipmentAssistantResult.Row(
                        9007199254740993L, "EQ", "设备", "分类", "正常", "在用", "部门", null)),
                1,
                1),
            LocalDateTime.of(2026, 10, 30, 8, 0),
            null);
    var result = EquipmentAssistantAdapter.response(answer, request);
    var row = ((List<Map<String, Object>>) result.get("data")).getFirst();
    assertEquals("9007199254740993", row.get("id"));
    assertTrue(row.containsKey("model"));
    assertNull(row.get("model"));
    var trend =
        AiBusinessPlan.parse(
            json.readTree(
                "{\"domain\":\"equipment\",\"operation\":\"TREND\",\"timeField\":\"acquiredAt\"}"));
    var grouped =
        new EquipmentAssistantResult(
            new EquipmentAssistantResult.Grouped(
                List.of(
                    new EquipmentAssistantResult.GroupRow(
                        null, List.of(), 1, 1, new java.math.BigDecimal("100.00"))),
                1,
                1),
            answer.evaluatedAt(),
            null);
    var wire =
        EquipmentAssistantAdapter.response(
            grouped, EquipmentAssistantAdapter.request(trend, null, 1, 20));
    var period = ((List<Map<String, Object>>) wire.get("data")).getFirst();
    assertTrue(period.containsKey("period"));
    assertNull(period.get("period"));
  }

  @Test
  void equipmentCatalogIsDerivedFromTheDeviceCapabilityRegistry() {
    var fields = AiBusinessCatalog.ALL.get("equipment").fields();
    assertEquals(EquipmentAssistantQuery.Field.values().length, fields.size());
    for (var field : EquipmentAssistantQuery.Field.values()) {
      assertEquals(field.kind().name(), fields.get(field.key()).type());
      assertEquals(
          field.identity() == null ? "" : field.identity().key(),
          fields.get(field.key()).identity());
    }
  }

  @Test
  void typedModelsCannotHideSqlJsonOrUntypedPayloads() {
    assertDoesNotThrow(() -> inspect(EquipmentAssistantQuery.class, new HashSet<>()));
    assertDoesNotThrow(() -> inspect(EquipmentAssistantResult.class, new HashSet<>()));
    assertThrows(AssertionError.class, () -> inspect(Poison.class, new HashSet<>()));
    assertThrows(AssertionError.class, () -> inspect(JsonPoison.class, new HashSet<>()));
    assertThrows(AssertionError.class, () -> inspect(SqlPoison.class, new HashSet<>()));
  }

  @Test
  void compiledDeviceChainCannotReturnToSqlSourceTransport() throws Exception {
    sourceFree(com.windasoft.eam.service.equipment.EquipmentQueryService.class);
    sourceFree(com.windasoft.eam.repository.equipment.EquipmentQueryRepository.class);
    sourceFree(com.windasoft.eam.repository.equipment.EquipmentAssistantRepository.class);
    sourceFree(EquipmentAssistantAdapter.class);
    assertThrows(AssertionError.class, () -> sourceFree(SourcePoison.class));
  }

  @Test
  void genericAiExecutorExplicitlyRefusesEquipmentPlans() throws Exception {
    var db = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var repository = new AiBusinessQueryRepository(db);
    var plan =
        AiBusinessPlan.parse(json.readTree("{\"domain\":\"equipment\",\"operation\":\"COUNT\"}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            repository.execute(
                new com.windasoft.eam.repository.common.AssistantReadSource("未执行", List.of()),
                com.windasoft.eam.repository.common.SqlPredicate.of("1=1"),
                plan,
                1,
                20));
    verifyNoInteractions(db);
  }

  static void sourceFree(Class<?> type) throws Exception {
    try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
      new org.springframework.asm.ClassReader(input)
          .accept(
              new org.springframework.asm.ClassVisitor(org.springframework.asm.Opcodes.ASM9) {
                void check(String reference) {
                  if (reference == null) {
                    return;
                  }
                  assertFalse(reference.contains("AssistantReadSource"), reference);
                  if (type == EquipmentAssistantAdapter.class) {
                    assertFalse(reference.contains("/repository/"), reference);
                  }
                }

                @Override
                public org.springframework.asm.FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                  check(descriptor);
                  check(signature);
                  return null;
                }

                @Override
                public org.springframework.asm.MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  check(descriptor);
                  check(signature);
                  return new org.springframework.asm.MethodVisitor(
                      org.springframework.asm.Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean itf) {
                      check(owner);
                      check(descriptor);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String value) {
                      check(value);
                    }

                    @Override
                    public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                      check(owner);
                      check(descriptor);
                    }
                  };
                }
              },
              org.springframework.asm.ClassReader.SKIP_DEBUG
                  | org.springframework.asm.ClassReader.SKIP_FRAMES);
    }
  }

  static void inspect(Type type, Set<Type> visited) {
    if (!visited.add(type)) {
      return;
    }
    if (type instanceof ParameterizedType p) {
      assertFalse(Map.class.isAssignableFrom((Class<?>) p.getRawType()));
      for (var argument : p.getActualTypeArguments()) {
        inspect(argument, visited);
      }
      return;
    }
    assertInstanceOf(Class.class, type);
    var cls = (Class<?>) type;
    assertNotEquals(Object.class, cls);
    assertFalse(JsonNode.class.isAssignableFrom(cls));
    assertFalse(cls.getName().contains("repository") || cls.getName().contains("Wrapper"));
    if (cls.isSealed()) {
      for (var child : cls.getPermittedSubclasses()) {
        inspect(child, visited);
      }
    }
    if (cls.isRecord()) {
      for (var component : cls.getRecordComponents()) {
        assertFalse(Set.of("sql", "arguments", "table", "predicate").contains(component.getName()));
        inspect(component.getGenericType(), visited);
      }
    }
  }

  record Poison(List<Map<String, Object>> payload) {}

  record JsonPoison(JsonNode payload) {}

  record SqlPoison(String sql, List<String> arguments) {}

  record SourcePoison(com.windasoft.eam.repository.common.AssistantReadSource source) {}
}
