package com.windasoft.eam;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Protects the agreed layer-first layout; business dependency checks progress by migrated slice.
 */
class LayerPackageArchitectureTest {
  private static final String BASE = "com.windasoft.eam.";
  private static final Set<String> OLD_ROOTS =
      Set.of(
          "equipment",
          "repair",
          "system",
          "basedata",
          "label",
          "maintenance",
          "inspection",
          "inventory",
          "experience",
          "printing");

  @Test
  void businessClassesUseLayerFirstPackages() throws Exception {
    Path classes =
        Path.of(EamApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path root = classes.resolve("com/windasoft/eam");
    try (var files = Files.walk(root)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        assertFalse(
            OLD_ROOTS.contains(relative.split("/")[0]), "Old business-first package: " + relative);
        String simple = file.getFileName().toString();
        if (simple.contains("$")) continue;
        if (simple.endsWith("Controller.class"))
          assertTrue(relative.startsWith("controller/"), relative);
        if (simple.endsWith("Mapper.class") || simple.endsWith("Repository.class"))
          assertTrue(relative.startsWith("repository/"), relative);
        if (simple.endsWith("Service.class")) assertTrue(relative.startsWith("service/"), relative);
        Class<?> type =
            Class.forName(
                BASE + relative.substring(0, relative.length() - 6).replace('/', '.'),
                false,
                EamApplication.class.getClassLoader());
        if (type.isAnnotationPresent(com.baomidou.mybatisplus.annotation.TableName.class))
          assertTrue(relative.startsWith("entity/"), relative);
      }
    }
  }

  @Test
  void migratedServicesAndControllersHaveNoDatabaseFields() throws Exception {
    Path root =
        Path.of(EamApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .resolve("com/windasoft/eam");
    for (String layer : Set.of("service", "controller")) {
      for (String module :
          Set.of(
              "equipment",
              "system",
              "repair",
              "basedata",
              "label",
              "maintenance",
              "inspection",
              "inventory",
              "experience")) {
        Path directory = root.resolve(layer).resolve(module);
        if (!Files.exists(directory)) continue;
        try (var files = Files.walk(directory)) {
          for (Path file :
              files
                  .filter(
                      p ->
                          p.toString().endsWith(".class")
                              && !p.getFileName().toString().contains("$"))
                  .toList()) {
            Class<?> type =
                Class.forName(
                    BASE
                        + root.relativize(file)
                            .toString()
                            .replace('/', '.')
                            .replace('\\', '.')
                            .replaceAll("\\.class$", ""),
                    false,
                    EamApplication.class.getClassLoader());
            for (var field : type.getDeclaredFields()) {
              String name = field.getType().getName();
              assertFalse(
                  name.startsWith("org.springframework.jdbc.")
                      || name.startsWith(BASE + "repository.") && name.endsWith("Mapper"),
                  type.getName() + " direct database field " + field.getName());
              if (layer.equals("controller"))
                assertFalse(
                    name.startsWith(BASE + "repository."),
                    type.getName() + " repository field " + field.getName());
            }
          }
        }
      }
    }
  }
}
