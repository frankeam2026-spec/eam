package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.windasoft.eam.controller.equipment.EquipmentQueryController;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.asm.*;

/** Enforces HTTP and persistence boundaries of the equipment query slice. */
class EquipmentQueryArchitectureTest {
  @Test
  void queryControllerCannotAccessDatabaseOrOwnTransactions() throws Exception {
    inspect(EquipmentQueryController.class, true);
  }

  @Test
  void queryServiceCannotAccessMappersJdbcOrHttp() throws Exception {
    inspect(EquipmentQueryService.class, false);
  }

  private void inspect(Class<?> type, boolean web) throws Exception {
    try (InputStream input = type.getResourceAsStream(type.getSimpleName() + ".class")) {
      new ClassReader(input)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                void check(String value) {
                  if (value == null) return;
                  assertFalse(
                      value.contains("JdbcTemplate")
                          || value.contains("Mapper;") && !value.contains("com/fasterxml/jackson/")
                          || value.endsWith("Mapper")
                              && !value.startsWith("com/fasterxml/jackson/"),
                      type.getSimpleName() + " must not access " + value);
                  if (web) {
                    assertFalse(
                        value.contains("/repository/") || value.contains("/transaction/"),
                        "Query controller must not access persistence or manage transactions: "
                            + value);
                  } else {
                    assertFalse(
                        value.contains("/controller/")
                            || value.contains("jakarta/servlet/")
                            || value.contains("org/springframework/http/"),
                        "Query service must not depend on HTTP: " + value);
                  }
                }

                @Override
                public FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                  check(descriptor);
                  check(signature);
                  return null;
                }

                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  check(descriptor);
                  check(signature);
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode, String owner, String name, String descriptor, boolean itf) {
                      check(owner);
                      check(descriptor);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                      check(type);
                    }

                    @Override
                    public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor) {
                      check(owner);
                      check(descriptor);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                      check(descriptor);
                      return null;
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                      if (value instanceof String text)
                        assertFalse(
                            text.matches(
                                "(?is).*\\b(SELECT .+ FROM|INSERT INTO|UPDATE .+ SET|DELETE"
                                    + " FROM)\\b.*"),
                            "SQL belongs to repository: " + text);
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
  }
}
