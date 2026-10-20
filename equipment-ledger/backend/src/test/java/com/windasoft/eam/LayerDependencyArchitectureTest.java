package com.windasoft.eam;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.asm.*;

/** Compiled references protect real dependencies, including business configuration and printing. */
class LayerDependencyArchitectureTest {
  private static final Set<String> SQL_FREE =
      Set.of(
          "business",
          "planning",
          "maintenance",
          "inspection",
          "inventory",
          "system",
          "printing",
          "equipment",
          "repair");

  static boolean forbidden(String layer, String target) {
    return switch (layer) {
      case "controller" -> target.startsWith("com/windasoft/eam/repository/");
      case "service" ->
          target.startsWith("org/springframework/jdbc/")
              || target.startsWith("com/windasoft/eam/repository/") && target.endsWith("Mapper");
      case "repository" ->
          target.startsWith("com/windasoft/eam/service/")
              || target.startsWith("com/windasoft/eam/controller/");
      default -> false;
    };
  }

  static Set<String> references(String value) {
    if (value == null) return Set.of();
    var result = new HashSet<String>();
    if (!value.contains(";") && !value.startsWith("(")) result.add(value);
    var matcher = java.util.regex.Pattern.compile("L([^;<]+)").matcher(value);
    while (matcher.find()) result.add(matcher.group(1));
    return result;
  }

  @Test
  void descriptorAndGenericReferencesCannotHideDatabaseDependencies() {
    assertTrue(
        references("Lorg/springframework/jdbc/core/JdbcTemplate;").stream()
            .anyMatch(value -> forbidden("service", value)));
    assertTrue(
        references("Ljava/util/List<Lcom/windasoft/eam/service/system/PermissionService;>;")
            .stream()
            .anyMatch(value -> forbidden("repository", value)));
    assertFalse(
        forbidden("service", "com/windasoft/eam/repository/inventory/InventoryListRepository"));
  }

  @Test
  void layerBoundariesHoldBeyondDirectoryNames() throws Exception {
    Path root =
        Path.of(EamApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .resolve("com/windasoft/eam");
    for (String layer : new String[] {"controller", "service", "repository"})
      try (var files = Files.walk(root.resolve(layer))) {
        for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
          boolean sqlFree =
              layer.equals("service")
                  && SQL_FREE.contains(root.resolve(layer).relativize(file).getName(0).toString());
          new ClassReader(Files.readAllBytes(file))
              .accept(
                  new ClassVisitor(Opcodes.ASM9) {
                    void dependency(String descriptor) {
                      for (String target : references(descriptor)) {
                        assertFalse(
                            forbidden(layer, target), file + " has forbidden dependency " + target);
                        if (layer.equals("service"))
                          for (String module : List.of("maintenance", "inspection")) {
                            String facade =
                                module.equals("maintenance")
                                    ? "MaintenanceService"
                                    : "InspectionService";
                            Path modulePath = root.resolve("service").resolve(module);
                            if (file.startsWith(modulePath)
                                && !file.getFileName().toString().startsWith(facade))
                              assertNotEquals(
                                  "com/windasoft/eam/service/" + module + "/" + facade,
                                  target,
                                  file + " depends back on its compatibility facade");
                          }
                      }
                    }

                    void queryLiteral(Object value) {
                      if (sqlFree && value instanceof String text)
                        assertFalse(
                            text.matches(
                                "(?is).*\\b(SELECT\\s+|INSERT\\s+INTO\\s+|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM\\s+).*"),
                            file + " contains query SQL that belongs in Repository");
                    }

                    void bootstrap(Object value) {
                      queryLiteral(value);
                      if (value instanceof Handle handle) {
                        dependency(handle.getOwner());
                        dependency(handle.getDesc());
                      } else if (value instanceof Type type) dependency(type.getDescriptor());
                    }

                    @Override
                    public void visit(
                        int version,
                        int access,
                        String name,
                        String signature,
                        String parent,
                        String[] interfaces) {
                      dependency(parent);
                      dependency(signature);
                      if (interfaces != null) for (String itf : interfaces) dependency(itf);
                    }

                    @Override
                    public FieldVisitor visitField(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        Object value) {
                      dependency(descriptor);
                      dependency(signature);
                      return null;
                    }

                    @Override
                    public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions) {
                      dependency(descriptor);
                      dependency(signature);
                      return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String method,
                            String descriptor,
                            boolean itf) {
                          dependency(owner);
                          dependency(descriptor);
                        }

                        @Override
                        public void visitFieldInsn(
                            int opcode, String owner, String field, String descriptor) {
                          dependency(owner);
                          dependency(descriptor);
                        }

                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                          dependency(type);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(
                            String name, String descriptor, Handle bootstrap, Object... arguments) {
                          dependency(descriptor);
                          bootstrap(bootstrap);
                          for (Object value : arguments) bootstrap(value);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                          bootstrap(value);
                        }
                      };
                    }
                  },
                  ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
      }
  }
}
