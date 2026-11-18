package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.business.BusinessReferenceSource;
import com.windasoft.eam.domain.business.BusinessReferenceSource.Kind;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.business.BusinessChoiceQuery;
import com.windasoft.eam.dto.business.BusinessChoices.Choice;
import com.windasoft.eam.dto.business.BusinessChoices.ChoicePage;
import com.windasoft.eam.dto.business.BusinessChoices.InlineChoice;
import com.windasoft.eam.dto.business.BusinessChoices.ReferenceOption;
import com.windasoft.eam.dto.business.BusinessChoices.StoredChoice;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.business.BusinessDictionaryRepository;
import com.windasoft.eam.repository.business.BusinessOptionRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.OrganizationScopeService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 业务字段引用的资格、名称和授权编排；数据访问由各对象仓储承担，固定选项不再通过Map传递。 */
@Service
public class BusinessReferenceService {
  private final BusinessDictionaryRepository dictionaries;
  private final BusinessOptionRepository options;
  private final BaseItemRepository bases;
  private final OrgUnitRepository organizations;
  private final SystemUserRepository users;
  private final OrganizationScopeService scopes;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final BusinessConfigService configs;

  public BusinessReferenceService(
      BusinessDictionaryRepository dictionaries,
      BusinessOptionRepository options,
      BaseItemRepository bases,
      OrgUnitRepository organizations,
      SystemUserRepository users,
      OrganizationScopeService scopes,
      AuthContext auth,
      PermissionService permissions,
      BusinessConfigService configs) {
    this.dictionaries = dictionaries;
    this.options = options;
    this.bases = bases;
    this.organizations = organizations;
    this.users = users;
    this.scopes = scopes;
    this.auth = auth;
    this.permissions = permissions;
    this.configs = configs;
  }

  public String menu(String object) {
    if (!BusinessConfigService.OBJECTS.containsKey(object))
      throw new LocalizedArgumentException("server.business.message0039");
    return BusinessObjects.get(object).menu();
  }

  public void access(String object) {
    String menu = menu(object);
    if (BusinessObjects.get(object).actions().stream().noneMatch(a -> permissions.allowed(menu, a)))
      permissions.require(menu, PermissionAction.VIEW.code());
  }

  private PermissionScope optionalScope(String object) {
    try {
      return permissions.scope(menu(object), PermissionAction.VIEW.code());
    } catch (ResponseStatusException denied) {
      return new PermissionScope(false, null, Set.of(), false);
    }
  }

  public boolean allowed(String object, Long organization, Long user) {
    PermissionScope scope;
    try {
      scope = permissions.scope(menu(object), PermissionAction.VIEW.code());
    } catch (ResponseStatusException denied) {
      return false;
    }
    Long company = organization == null ? null : permissions.companyOfOrganization(organization);
    return matches(scope, company, organization, user);
  }

  private boolean matches(PermissionScope scope, Long company, Long organization, Long user) {
    return scope.matches(company, organization, user, auth.userId())
        || scope.owner()
            && Objects.equals(organization, auth.user().getOrgId())
            && (user == null || user.equals(auth.userId()));
  }

  private record OrganizationContext(
      List<OrgUnit> active,
      Map<Long, OrgUnit> byId,
      Map<Long, Long> companies,
      PermissionScope scope) {}

  /** 公司授权沿用包含停用上级的完整快照，显示路径仍仅由启用组织组成；两种口径不能合并。 */
  private OrganizationContext organizationContext(PermissionScope scope) {
    var all = organizations.organizations(auth.tenantId());
    var active = all.stream().filter(o -> Integer.valueOf(1).equals(o.getStatus())).toList();
    Map<Long, OrgUnit> byId = new LinkedHashMap<>();
    active.forEach(o -> byId.put(o.getId(), o));
    return new OrganizationContext(active, byId, scopes.companiesForOrganizations(all), scope);
  }

  private String path(Long id, Map<Long, OrgUnit> byId) {
    var names = new ArrayList<String>();
    var visited = new HashSet<Long>();
    var node = byId.get(id);
    while (node != null && visited.add(node.getId())) {
      names.addFirst(node.getName());
      node = byId.get(node.getParentId());
    }
    return String.join(" > ", names);
  }

  private List<ReferenceOption> departments(OrganizationContext context) {
    return context.active().stream()
        .filter(
            o ->
                "部门".equals(o.getOrgType())
                    && matches(
                        context.scope(), context.companies().get(o.getId()), o.getId(), null))
        .map(
            o ->
                new ReferenceOption(o.getId().toString(), path(o.getId(), context.byId()), true, 0))
        .toList();
  }

  public List<ReferenceOption> all(String object, String source, boolean inactive) {
    access(object);
    var parsed = BusinessReferenceSource.parse(source);
    return switch (parsed.kind()) {
      case FIELD -> {
        var field =
            configs
                .fields(configs.decode(configs.current(object, false).getPublished()))
                .get(parsed.key());
        if (field == null || !BusinessRules.inlineOptions(field))
          throw new LocalizedArgumentException("server.business.message0040");
        yield !inactive && !field.path("enabled").asBoolean(true)
            ? List.of()
            : fieldOptions(object, field, inactive);
      }
      case DICTIONARY -> {
        var dictionary = dictionaries.find(auth.tenantId(), parsed.key());
        if (dictionary == null || !inactive && !enabled(dictionary.getEnabled())) yield List.of();
        yield options.options(auth.tenantId(), parsed.key()).stream()
            .map(
                o ->
                    new ReferenceOption(
                        o.getId(), o.getName(), enabled(o.getEnabled()), o.getSortNo()))
            .filter(o -> inactive || o.enabled())
            .toList();
      }
      case BASE -> {
        if (!BusinessConfigService.BASES.containsKey(parsed.key()))
          throw new LocalizedArgumentException("server.business.message0002");
        yield bases.referenceItems(auth.tenantId(), parsed.key()).stream()
            .map(
                o ->
                    new ReferenceOption(
                        o.getId().toString(), o.getName(), enabled(o.getStatus()), o.getSortNo()))
            .filter(o -> inactive || o.enabled())
            .toList();
      }
      case DEPARTMENT -> departments(organizationContext(optionalScope(object)));
      case USER -> {
        var context = organizationContext(optionalScope(object));
        var allowed = new HashSet<>(context.scope().departmentIds());
        context
            .companies()
            .forEach(
                (id, company) -> {
                  if (context.scope().matches(company, id, null, auth.userId())) allowed.add(id);
                });
        yield users
            .businessReferences(
                auth.tenantId(),
                context.scope().all(),
                allowed,
                context.scope().owner() ? auth.userId() : null)
            .stream()
            .map(u -> new ReferenceOption(u.getId().toString(), u.getDisplayName(), true, 0))
            .toList();
      }
    };
  }

  /** 授权在分页前进入SQL；字段内置选项已在配置内，继续在该有限集合内筛选及分页。 */
  public ChoicePage choices(
      String object, String source, String keyword, int page, int size, boolean inactive) {
    access(object);
    var query = new BusinessChoiceQuery(keyword, page, size);
    var parsed = BusinessReferenceSource.parse(source);
    if (parsed.kind() == Kind.FIELD) {
      var term = keyword.toLowerCase(Locale.ROOT);
      var matches =
          all(object, source, inactive).stream()
              .filter(o -> o.name().toLowerCase(Locale.ROOT).contains(term))
              .toList();
      long offset = (long) (page - 1) * size;
      List<Choice> rows =
          matches.stream()
              .skip(offset)
              .limit(size)
              .<Choice>map(o -> new InlineChoice(o.id(), o.name(), o.enabled(), o.sortOrder()))
              .toList();
      return new ChoicePage(rows, matches.size());
    }
    if (parsed.kind() == Kind.BASE) {
      if (!BusinessConfigService.BASES.containsKey(parsed.key()))
        throw new LocalizedArgumentException("server.business.message0042");
      var result = bases.businessChoices(auth.tenantId(), parsed.key(), query, inactive);
      List<Choice> rows =
          result.getRecords().stream()
              .<Choice>map(
                  o ->
                      new StoredChoice(
                          o.getId().toString(), o.getName(), o.getStatus(), o.getSortNo()))
              .toList();
      return new ChoicePage(rows, result.getTotal());
    }
    if (parsed.kind() == Kind.DICTIONARY) {
      var result = options.choices(auth.tenantId(), parsed.key(), query, inactive);
      return new ChoicePage(new ArrayList<Choice>(result.getRecords()), result.getTotal());
    }
    var scope = permissions.scope(menu(object), PermissionAction.VIEW.code());
    var context = organizationContext(scope);
    var allowed = new ArrayList<Long>();
    for (var org : context.active()) {
      if (scope.matches(context.companies().get(org.getId()), org.getId(), null, auth.userId())
          || parsed.kind() == Kind.DEPARTMENT
              && scope.owner()
              && Objects.equals(org.getId(), auth.user().getOrgId())) allowed.add(org.getId());
    }
    if (parsed.kind() == Kind.USER) {
      var result =
          users.businessChoices(
              auth.tenantId(), query, allowed, scope.owner() ? auth.userId() : null);
      List<Choice> rows =
          result.getRecords().stream()
              .<Choice>map(
                  u ->
                      new StoredChoice(
                          u.getId().toString(), u.getDisplayName(), u.getStatus(), null))
              .toList();
      return new ChoicePage(rows, result.getTotal());
    }
    var result = organizations.businessChoices(auth.tenantId(), query, allowed);
    List<Choice> rows =
        result.getRecords().stream()
            .<Choice>map(
                o ->
                    new StoredChoice(
                        o.getId().toString(), path(o.getId(), context.byId()), o.getStatus(), null))
            .toList();
    return new ChoicePage(rows, result.getTotal());
  }

  public List<ReferenceOption> fieldOptions(String object, JsonNode field, boolean inactive) {
    access(object);
    if (!BusinessRules.inlineOptions(field))
      return all(object, field.path("source").asText(), inactive);
    var result = new ArrayList<ReferenceOption>();
    for (var option : field.path("options")) {
      if (inactive || option.path("enabled").asBoolean())
        result.add(
            new ReferenceOption(
                option.path("id").asText(),
                option.path("name").asText(),
                option.path("enabled").asBoolean(),
                option.path("sortOrder").asInt()));
    }
    result.sort(
        Comparator.comparingInt(ReferenceOption::sortOrder).thenComparing(ReferenceOption::id));
    return result;
  }

  public String resolve(String object, JsonNode field, String id) {
    if (!BusinessRules.inlineOptions(field))
      return resolve(object, field.path("source").asText(), id);
    return fieldOptions(object, field, false).stream()
        .filter(o -> id.equals(o.id()))
        .map(ReferenceOption::name)
        .findFirst()
        .orElseThrow(() -> new LocalizedArgumentException("server.business.message0043"));
  }

  private record Resolution(String name, boolean enabled, Long organization) {}

  /** 新引用沿用共享锁及父子启用检查；历史引用是否保留快照由动态值服务决定，不在这里自动放行。 */
  public String resolve(String object, String source, String id) {
    var parsed = BusinessReferenceSource.parse(source);
    try {
      if (parsed.kind() == Kind.DICTIONARY) UUID.fromString(id);
      else if (Long.parseLong(id) <= 0) throw new NumberFormatException();
    } catch (IllegalArgumentException invalid) {
      throw new LocalizedArgumentException("server.business.message0044");
    }
    Resolution row =
        switch (parsed.kind()) {
          case DICTIONARY -> {
            var value = options.locked(auth.tenantId(), parsed.key(), id);
            yield value == null ? null : new Resolution(value.name(), value.enabled(), null);
          }
          case BASE -> {
            var value = bases.reference(auth.tenantId(), parsed.key(), Long.parseLong(id));
            yield value == null
                ? null
                : new Resolution(value.getName(), enabled(value.getStatus()), null);
          }
          case USER -> {
            var value = users.referenceForShare(auth.tenantId(), Long.parseLong(id));
            yield value == null
                ? null
                : new Resolution(
                    value.getDisplayName(), enabled(value.getStatus()), value.getOrgId());
          }
          case DEPARTMENT -> {
            var value = organizations.referenceForShare(auth.tenantId(), Long.parseLong(id));
            yield value == null
                ? null
                : new Resolution(value.getName(), enabled(value.getStatus()), value.getId());
          }
          default -> throw new LocalizedArgumentException("server.business.message0002");
        };
    if (row == null || !row.enabled())
      throw new LocalizedArgumentException("server.business.message0045");
    if ((parsed.kind() == Kind.USER || parsed.kind() == Kind.DEPARTMENT)
        && !allowed(
            object, row.organization(), parsed.kind() == Kind.USER ? Long.valueOf(id) : null))
      throw new LocalizedArgumentException("server.business.message0046");
    if (parsed.kind() == Kind.DEPARTMENT)
      return all(object, source, false).stream()
          .filter(o -> o.id().equals(id))
          .map(ReferenceOption::name)
          .findFirst()
          .orElseThrow();
    return row.name();
  }

  public static boolean enabled(Object value) {
    return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() == 1;
  }
}
