package com.windasoft.eam.service.workflow;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Script;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Each calculation runs in a bounded, credential-free child process and returns a typed scalar. */
@Component
public class WorkflowScriptRunner {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Semaphore slots = new Semaphore(2);

  public void validate(Script script) {
    run(script, Map.of(), true, 8000);
  }

  public Object execute(Script script, Map<String, Object> values) {
    return execute(script, values, 8000);
  }

  public Object execute(Script script, Map<String, Object> values, long timeoutMillis) {
    if (script == null
        || script.outputType() == null
        || !Set.of("TEXT", "NUMBER", "BOOLEAN").contains(script.outputType()))
      throw new IllegalArgumentException("请声明脚本结果类型");
    var result = run(script, values, false, timeoutMillis).get("value");
    if (result == null || result.isNull()) throw new IllegalArgumentException("脚本必须返回声明类型的结果");
    return switch (script.outputType()) {
      case "TEXT" -> {
        if (!result.isTextual() || result.textValue().length() > 1000)
          throw new IllegalArgumentException("脚本须返回不超过1000字的文本");
        yield result.textValue();
      }
      case "BOOLEAN" -> {
        if (!result.isBoolean()) throw new IllegalArgumentException("脚本须返回布尔值");
        yield result.booleanValue();
      }
      case "NUMBER" -> {
        if (!result.isTextual()) throw new IllegalArgumentException("数值脚本须返回十进制字符串，请使用decimal辅助函数");
        try {
          var number = new BigDecimal(result.textValue()).stripTrailingZeros();
          if (number.scale() > 12 || number.precision() - number.scale() > 26)
            throw new NumberFormatException();
          yield number;
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("脚本数值超出26位整数和12位小数范围");
        }
      }
      default -> throw new IllegalArgumentException("脚本结果类型无效");
    };
  }

  private JsonNode run(
      Script script, Map<String, Object> values, boolean validate, long timeoutMillis) {
    if (timeoutMillis <= 0) throw new IllegalArgumentException("本轮流程计算已超时，请缩短计算脚本");
    if (script == null
        || !"javascript".equals(script.language())
        || script.source() == null
        || script.source().isBlank()
        || script.source().length() > 32000)
      throw new IllegalArgumentException("JavaScript脚本必填且最多32000字符");
    if (!slots.tryAcquire()) throw new IllegalArgumentException("脚本执行繁忙，请稍后重试");
    Process process = null;
    Path directory = null;
    var tasks = Executors.newVirtualThreadPerTaskExecutor();
    try {
      byte[] input =
          mapper.writeValueAsBytes(
              Map.of("source", script.source(), "values", precise(values), "validate", validate));
      if (input.length > 300000) throw new IllegalArgumentException("脚本输入过大");
      directory = Files.createTempDirectory("eam-workflow-script-");
      List<String> args =
          new ArrayList<>(
              List.of(
                  "-Xmx128m",
                  "-XX:MaxMetaspaceSize=128m",
                  "-XX:MaxDirectMemorySize=16m",
                  "-Dfile.encoding=UTF-8"));
      var currentArgs = ProcessHandle.current().info().arguments().orElse(new String[0]);
      String applicationJar = null;
      // Windows may not expose ProcessHandle arguments. The launch classpath remains reliable for
      // -jar.
      String launchClasspath = System.getProperty("java.class.path", "");
      if (!launchClasspath.contains(java.io.File.pathSeparator)
          && launchClasspath.endsWith(".jar")) {
        var candidate = Path.of(launchClasspath).toAbsolutePath();
        try (var zip = new java.util.zip.ZipFile(candidate.toFile())) {
          if (zip.getEntry("BOOT-INF/classes/com/windasoft/eam/EamApplication.class") != null)
            applicationJar = candidate.toString();
        }
      }
      for (int i = 0; i < currentArgs.length - 1; i++)
        if (currentArgs[i].equals("-jar")) {
          var candidate = Path.of(currentArgs[i + 1]).toAbsolutePath();
          try (var zip = new java.util.zip.ZipFile(candidate.toFile())) {
            if (zip.getEntry("BOOT-INF/classes/com/windasoft/eam/EamApplication.class") != null)
              applicationJar = candidate.toString();
          }
        }
      if (applicationJar != null)
        args.addAll(List.of("-jar", applicationJar, "--workflow-script-worker"));
      else
        args.addAll(
            List.of(
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                "com.windasoft.eam.common.workflow.WorkflowScriptWorker"));
      Path arguments = directory.resolve("worker.args");
      Files.writeString(
          arguments,
          args.stream()
              .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
              .collect(java.util.stream.Collectors.joining("\n")),
          StandardCharsets.UTF_8);
      var builder =
          new ProcessBuilder(
              ProcessHandle.current().info().command().orElseThrow(), "@" + arguments);
      var systemRoot = System.getenv("SystemRoot");
      builder.environment().clear();
      if (systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
      builder.directory(directory.toFile());
      process = builder.start();
      final Process child = process;
      var writer =
          tasks.submit(
              () -> {
                try (var output = child.getOutputStream()) {
                  output.write(input);
                }
                return true;
              });
      var output = tasks.submit(() -> child.getInputStream().readNBytes(16385));
      var errors = tasks.submit(() -> child.getErrorStream().readNBytes(4097));
      boolean finished = process.waitFor(Math.min(8000, timeoutMillis), TimeUnit.MILLISECONDS);
      if (!finished) {
        process.destroyForcibly();
        process.waitFor(2, TimeUnit.SECONDS);
        throw new IllegalArgumentException("脚本执行超时（8秒），已终止");
      }
      writer.get(1, TimeUnit.SECONDS);
      byte[] response = output.get(1, TimeUnit.SECONDS);
      byte[] diagnostic = errors.get(1, TimeUnit.SECONDS);
      if (process.exitValue() != 0 || response.length > 16384) {
        org.slf4j.LoggerFactory.getLogger(WorkflowScriptRunner.class)
            .warn(
                "Workflow worker exit {}: {}",
                process.exitValue(),
                new String(diagnostic, StandardCharsets.UTF_8)
                    .substring(
                        0,
                        Math.min(1024, new String(diagnostic, StandardCharsets.UTF_8).length())));
        throw new IllegalArgumentException("脚本进程失败或超过资源限制");
      }
      var result = mapper.readTree(response);
      if (result == null) throw new IllegalArgumentException("脚本未返回结果");
      if (result.has("error"))
        throw new IllegalArgumentException("脚本执行失败：" + result.get("error").asText());
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("脚本执行中断");
    } catch (Exception e) {
      throw new IllegalArgumentException("脚本进程无法完成执行", e);
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      tasks.shutdownNow();
      if (directory != null)
        try {
          Files.deleteIfExists(directory.resolve("worker.args"));
          Files.deleteIfExists(directory);
        } catch (Exception ignored) {
        }
      slots.release();
    }
  }

  private Object precise(Object value) {
    if (value instanceof Number) return value.toString();
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((k, v) -> copy.put(k.toString(), precise(v)));
      return copy;
    }
    if (value instanceof List<?> list) return list.stream().map(this::precise).toList();
    return value;
  }
}
