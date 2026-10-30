package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.windasoft.eam.repository.inventory.MaterialRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.jdbc.core.JdbcTemplate;

class EquipmentMaterialAccessArchitectureTest {
  @Test
  void bomCandidateMethodsDelegateInsteadOfExecutingMaterialSql() throws IOException {
    check(EquipmentRelationsRepository.class);
    check(MaterialRepository.class);
  }

  @Test
  void guardRejectsAnEquipmentRepositoryExecutingBorrowedSql() {
    assertThrows(AssertionError.class, () -> check(BorrowedSql.class));
  }

  private static void check(Class<?> type) throws IOException {
    try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
      if (stream == null) {
        throw new IOException("无法读取待检查的类");
      }
      new ClassReader(stream)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions) {
                  if (!Set.of(
                          "materials",
                          "materialTotal",
                          "materialWhere",
                          "materialArgs",
                          "countEnabledCandidates",
                          "enabledCandidates")
                      .contains(name)) {
                    return null;
                  }
                  return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String method,
                        String descriptor,
                        boolean isInterface) {
                      if (owner.equals("org/springframework/jdbc/core/JdbcTemplate")) {
                        throw new AssertionError("BOM候选单表查询须由物料Repository提供：" + name);
                      }
                    }
                  };
                }
              },
              ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
  }

  private static final class BorrowedSql {
    JdbcTemplate db;

    List<Map<String, Object>> materials(String sql) {
      return db.queryForList(sql);
    }
  }
}
