package com.windasoft.eam.service.imports;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.imports.*;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.imports.*;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.equipment.*;
import com.windasoft.eam.service.system.*;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentImportService {
  private static final Logger log = LoggerFactory.getLogger(EquipmentImportService.class);
  private final ImportBatchRepository batches;
  private final EquipmentImportRepository repo;
  private final ImportWorkbookService excel;
  private final EquipmentImportValidation validation;
  private final EquipmentCommandService commands;
  private final EquipmentRelationsService relations;
  private final BusinessConfigService configs;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate tx;
  private final Path directory;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(8),
          r -> {
            var t = new Thread(r, "equipment-import");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public EquipmentImportService(
      ImportBatchRepository batches,
      EquipmentImportRepository repo,
      ImportWorkbookService excel,
      EquipmentImportValidation validation,
      EquipmentCommandService commands,
      EquipmentRelationsService relations,
      BusinessConfigService configs,
      AuthContext auth,
      PermissionService permissions,
      PlatformTransactionManager manager,
      @Value("${eam.upload.directory}") String directory) {
    this.batches = batches;
    this.repo = repo;
    this.excel = excel;
    this.validation = validation;
    this.commands = commands;
    this.relations = relations;
    this.configs = configs;
    this.auth = auth;
    this.permissions = permissions;
    this.tx = new TransactionTemplate(manager);
    this.tx.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.directory = Path.of(directory).toAbsolutePath().resolve("private-imports");
  }

  private void access() {
    permissions.require("equipment", "导入");
    permissions.require("equipment", "新增");
  }

  private Path file(String id) {
    UUID.fromString(id);
    return directory.resolve(id + ".xlsx");
  }

  private Map<String, Object> owned(String id) {
    access();
    UUID.fromString(id);
    return batches.find(id, auth.tenantId(), auth.userId(), false);
  }

  public ImportBatchView state(String id) {
    return ImportBatchView.of(owned(id));
  }

  public void template(OutputStream out) throws IOException {
    var c = validation.context();
    excel.write(out, c.version(), c.columns(), c.choices(), List.of(), List.of());
  }

  public ImportBatchView upload(MultipartFile upload) throws IOException {
    access();
    if (upload.isEmpty() || upload.getSize() > 10L * 1024 * 1024)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0002");
    String name = Objects.toString(upload.getOriginalFilename(), "导入.xlsx");
    if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0003");
    if (name.length() > 255)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0004");
    String id = UUID.randomUUID().toString();
    Files.createDirectories(directory);
    byte[] bytes = upload.getBytes();
    String sha = HexFormat.of().formatHex(hash(bytes));
    Files.write(file(id), bytes, StandardOpenOption.CREATE_NEW);
    try {
      batches.create(id, auth.tenantId(), auth.userId(), name, bytes.length, sha);
    } catch (RuntimeException e) {
      Files.deleteIfExists(file(id));
      throw e;
    }
    enqueue(id, false);
    return state(id);
  }

  public ImportBatchView confirm(String id, int version, String key) {
    UUID.fromString(key);
    var batch = owned(id);
    String state = batch.get("state").toString();
    if (Set.of("SUCCEEDED", "IMPORTING").contains(state)) {
      if (!key.equals(batch.get("confirm_key"))
          || !Objects.equals(version, batch.get("confirm_version")))
        throw new com.windasoft.eam.common.LocalizedStatusException(
            HttpStatus.CONFLICT, "server.imports.message0005");
      return ImportBatchView.of(batch);
    }
    if (!"READY".equals(state))
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.CONFLICT, "server.imports.message0006");
    if (expired(batch))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0007");
    try {
      if (!batches.claim(id, auth.tenantId(), auth.userId(), version, key))
        throw new com.windasoft.eam.common.LocalizedStatusException(
            HttpStatus.CONFLICT, "server.imports.message0008");
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.CONFLICT, "server.imports.message0009");
    }
    enqueue(id, true);
    return state(id);
  }

  private void enqueue(String id, boolean confirm) {
    var languageSnapshot = com.windasoft.eam.common.MessageContext.capture();
    try {
      workers.execute(
          () -> {
            var b = batches.internal(id);
            var actor = repo.user(((Number) b.get("actor_id")).longValue());
            try (var languageScope =
                com.windasoft.eam.common.MessageContext.open(languageSnapshot)) {
              if (actor == null
                  || !Objects.equals(
                      actor.getTenantId(), ((Number) b.get("tenant_id")).longValue()))
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.imports.message0010");
              SystemExecution.run(
                  actor,
                  () -> {
                    if (confirm) commit(id);
                    else validate(id);
                    return null;
                  });
            } catch (Exception e) {
              log.warn("Equipment import batch {} failed", id, e);
              batches.failed(id, message(e));
            }
          });
    } catch (RejectedExecutionException e) {
      batches.failed(
          id, com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3252"));
    }
  }

  private void validate(String id) {
    access();
    var book = excel.read(file(id));
    var context = validation.context();
    var checked = validation.check(book, context);
    tx.executeWithoutResult(
        status -> {
          batches.columns(id, book.columns());
          batches.saveCells(id, book.rows());
          for (var row : checked.rows())
            repo.stage(id, row.row(), row.form(), row.form() == null ? null : row.parentCode());
          batches.saveIssues(id, checked.issues());
          batches.validated(
              id,
              context.version(),
              book.rows().size(),
              (int)
                  book.rows().stream()
                      .filter(r -> r.values().getOrDefault("code", "").isBlank())
                      .count(),
              checked.issues());
        });
  }

  private void commit(String id) {
    access();
    var original = batches.internal(id);
    if (expired(original))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0011");
    var book = excel.read(file(id));
    var initial = validation.check(book, validation.context());
    if (!initial.issues().isEmpty()) {
      replaceErrors(id, book, initial);
      return;
    }
    var companies =
        initial.rows().stream().map(r -> r.entity().getCompanyOrgId()).distinct().sorted().toList();
    tx.executeWithoutResult(
        status -> {
          var b = batches.find(id, auth.tenantId(), auth.userId(), true);
          if ("SUCCEEDED".equals(b.get("state"))) return;
          if (!"IMPORTING".equals(b.get("state")))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.imports.message0012");
          relations.enter(companies);
          configs.current("equipment", true);
          repo.lockReferences(auth.tenantId());
          var checked = validation.check(book, validation.context());
          if (!checked.issues().isEmpty()) {
            replaceErrors(id, book, checked);
            return;
          }
          if (!companies.containsAll(
              checked.rows().stream().map(r -> r.entity().getCompanyOrgId()).toList()))
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.imports.message0013");
          commands.insertImported(checked.rows());
          var byCode = new HashMap<String, Equipment>();
          checked
              .rows()
              .forEach(
                  r ->
                      byCode.put(
                          checked.context().codeKey(r.entity().getEquipmentCode()), r.entity()));
          var parents = new LinkedHashMap<Equipment, List<Equipment>>();
          for (var row : checked.rows()) {
            if (!row.parentCode().isBlank()) {
              String key = checked.context().codeKey(row.parentCode());
              Equipment parent = byCode.getOrDefault(key, checked.context().existing.get(key));
              if (parent == null)
                throw new com.windasoft.eam.common.LocalizedArgumentException(
                    "server.imports.message0014");
              parents.computeIfAbsent(parent, x -> new ArrayList<>()).add(row.entity());
            }
            repo.result(id, row.row(), row.entity().getId());
          }
          relations.appendImportedChildren(parents);
          batches.succeed(id, checked.rows().size());
        });
  }

  private void replaceErrors(
      String id, ImportWorkbook book, EquipmentImportValidation.Checked checked) {
    tx.executeWithoutResult(
        status -> {
          batches.clearValidation(id);
          for (var r : checked.rows())
            repo.stage(id, r.row(), r.form(), r.form() == null ? null : r.parentCode());
          batches.saveIssues(id, checked.issues());
          batches.validated(
              id,
              checked.context().version(),
              book.rows().size(),
              (int)
                  book.rows().stream()
                      .filter(r -> r.values().getOrDefault("code", "").isBlank())
                      .count(),
              checked.issues());
        });
  }

  public Map<String, Object> preview(String id, ListQuery q, boolean errors) {
    var batch = owned(id);
    var numbers = batches.rowNumbers(id, errors, q.page(), q.size());
    var cells = batches.cells(id, numbers);
    var issues = batches.issues(id, numbers);
    var data = new ArrayList<Map<String, Object>>();
    for (int row : numbers) {
      var values = new LinkedHashMap<String, String>();
      for (var cell : cells)
        if (((Number) cell.get("row_no")).intValue() == row)
          values.put(cell.get("field_key").toString(), cell.get("raw_value").toString());
      data.add(
          Map.of(
              "row",
              row,
              "values",
              values,
              "issues",
              issues.stream().filter(e -> ((Number) e.get("row")).intValue() == row).toList()));
    }
    return Map.of(
        "data",
        data,
        "columns",
        batches.columns(id),
        "page",
        new PageResult.Page(
            q.page(),
            q.size(),
            ((Number) batch.get(errors ? "error_rows" : "total_rows")).longValue()));
  }

  public Map<String, Object> results(String id, ListQuery query) {
    var batch = owned(id);
    permissions.require("equipment", "查看");
    if (!"SUCCEEDED".equals(batch.get("state")))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0015");
    var result =
        repo.results(
            id, auth.tenantId(), query.page(), query.size(), q -> permissions.filterEquipment(q));
    return Map.of(
        "data",
        result.getRecords().stream()
            .map(
                e ->
                    Map.of(
                        "id",
                        e.getId().toString(),
                        "code",
                        e.getEquipmentCode(),
                        "name",
                        e.getName(),
                        "department",
                        Objects.toString(e.getDepartmentPath(), "")))
            .toList(),
        "page",
        new PageResult.Page(query.page(), query.size(), result.getTotal()));
  }

  public void report(String id, OutputStream out) throws IOException {
    var b = owned(id);
    if (expired(b) || b.get("purged_at") != null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0016");
    if (!Files.exists(file(id)))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0017");
    var original = excel.read(file(id));
    var issues =
        batches.allIssues(id).stream()
            .map(
                r ->
                    new ImportIssue(
                        ((Number) r.get("row")).intValue(),
                        r.get("field").toString(),
                        r.get("label").toString(),
                        r.get("value").toString(),
                        r.get("reason").toString(),
                        r.get("suggestion").toString()))
            .toList();
    excel.write(
        out, original.schemaVersion(), original.columns(), Map.of(), original.rows(), issues);
  }

  private static boolean expired(Map<String, Object> b) {
    Object t = b.get("expires_at");
    return t instanceof java.sql.Timestamp timestamp
            && timestamp.toInstant().isBefore(java.time.Instant.now())
        || t instanceof java.time.LocalDateTime date
            && date.isBefore(java.time.LocalDateTime.now());
  }

  private static byte[] hash(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String message(Exception e) {
    if (e instanceof ResponseStatusException r && r.getStatusCode().value() == 409)
      return Objects.toString(
              r.getReason(),
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3253"))
          + com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3254");
    if (e instanceof IllegalArgumentException)
      return Objects.toString(
              e.getMessage(),
              com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3255"))
          + com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3254");
    return com.windasoft.eam.common.MessageContext.text("output.import.diagnostic3256");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    batches.recover();
  }

  @Scheduled(
      fixedDelayString = "${eam.import.cleanup-delay-ms:3600000}",
      initialDelayString = "${eam.import.cleanup-initial-ms:60000}")
  public void cleanup() {
    for (String id : batches.expired())
      try {
        if (Boolean.TRUE.equals(tx.execute(s -> batches.expire(id))))
          Files.deleteIfExists(file(id));
      } catch (Exception e) {
        log.warn("Import cleanup failed for {}", id, e);
      }
  }

  @PreDestroy
  public void close() {
    workers.shutdown();
    try {
      if (!workers.awaitTermination(30, TimeUnit.SECONDS)) workers.shutdownNow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      workers.shutdownNow();
    }
  }
}
