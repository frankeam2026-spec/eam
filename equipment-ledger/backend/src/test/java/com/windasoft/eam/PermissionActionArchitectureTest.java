package com.windasoft.eam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class PermissionActionArchitectureTest {
  private static final Set<String> MODULES =
      Set.of(
          "basedata", "equipment", "experience", "imports", "inspection", "inventory",
          "knowledge", "label", "maintenance", "planning", "repair", "workflow");
  private static final Set<String> SERVICE_RECEIVERS =
      Set.of(
          "permissions", "this.permissions", "permissionService", "this.permissionService",
          "permission", "this.permission");

  @Test
  void migratedModulesDoNotPassActionLiterals() throws Exception {
    var root = Path.of("src/main/java/com/windasoft/eam");
    var files = new ArrayList<Path>();
    for (String layer : List.of("controller", "service")) {
      for (String module : MODULES) {
        var directory = root.resolve(layer).resolve(module);
        if (Files.isDirectory(directory)) {
          try (var sources = Files.walk(directory)) {
            files.addAll(sources.filter(path -> path.toString().endsWith(".java")).toList());
          }
        }
      }
    }
    assertTrue(!files.isEmpty(), "没有找到已迁移模块的源码");
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertEquals(List.of(), violations(manager.getJavaFileObjectsFromPaths(files)));
    }
  }

  @Test
  void detectsMultilineAndConditionalLiteralsAndAcceptsTypedCalls() throws Exception {
    var example = new SimpleJavaFileObject(
        URI.create("string:///PermissionExample.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return """
            class PermissionExample {
              void run(boolean granted) {
                permissions.allowed(type.menu(),
                    "查看");
                this.permissions.require("base", granted ? ("查看") : EDIT);
                permissions.anyInspection("查看");
                permissions.checkInventoryWarehouse("inventoryReceipt", 1, "入库");
                permissions.scope("base", PermissionAction.VIEW);
                permissions.checkInventoryWarehouse("inventoryReceipt", 1, PermissionAction.STOCK_IN.code());
                permissions.scope("base", requestedAction);
                options.require("base", "显示文字");
              }
            }
            """;
      }
    };
    assertEquals(
        List.of("/PermissionExample.java: allowed", "/PermissionExample.java: require",
            "/PermissionExample.java: anyInspection", "/PermissionExample.java: checkInventoryWarehouse"),
        violations(List.of(example)));
  }

  /** 只检查动作参数，菜单键与其他方法的中文字面量保持各自语义。 */
  private static List<String> violations(Iterable<? extends JavaFileObject> sources) throws Exception {
    var errors = new ArrayList<String>();
    var compiler = ToolProvider.getSystemJavaCompiler();
    var task = (JavacTask) compiler.getTask(null, null, null, List.of("-proc:none"), null, sources);
    for (var source : task.parse()) {
      new TreeScanner<Void, Void>() {
        @Override
        public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
          if (call.getMethodSelect() instanceof MemberSelectTree select
              && SERVICE_RECEIVERS.contains(select.getExpression().toString())) {
            var method = select.getIdentifier().toString();
            int index = switch (method) {
              case "anyMaintenance", "anyInspection", "equipment", "repair" -> 0;
              case "maintenance" -> call.getArguments().size() - 1;
              case "allowed", "require", "scope", "inspection", "checkEquipment", "checkRepair",
                  "filterEquipment", "filterRepair", "equipmentDepartmentAllowed",
                  "checkEquipmentDepartment", "companyAllowed", "checkInventoryCompany",
                  "checkOrg", "checkUser", "inventoryWarehouses" -> 1;
              case "inventoryWarehouseAllowed", "checkInventoryWarehouse" -> 2;
              case "inventoryWarehouseUser" -> 3;
              default -> -1;
            };
            if (index >= 0 && index < call.getArguments().size()
                && hasLiteral(call.getArguments().get(index))) {
              errors.add(source.getSourceFile().getName() + ": " + method);
            }
          }
          return super.visitMethodInvocation(call, unused);
        }
      }.scan(source, null);
    }
    return errors;
  }

  private static boolean hasLiteral(ExpressionTree expression) {
    return switch (expression) {
      case LiteralTree literal -> literal.getValue() instanceof String;
      case ParenthesizedTree grouped -> hasLiteral(grouped.getExpression());
      case ConditionalExpressionTree choice ->
          hasLiteral(choice.getTrueExpression()) || hasLiteral(choice.getFalseExpression());
      default -> false;
    };
  }
}
