package com.windasoft.eam.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestBody;

/** 复用固定接口契约检查：穿透响应包装、集合及模型字段，并解析PageResult等包装中的类型参数。 */
public final class ContractTypes {
  private ContractTypes() {}

  public record DynamicField(Class<?> owner, String field, String type) {}

  public static void assertExplicit(Type type, DynamicField... allowed) {
    check(type, Map.of(), new HashSet<>(), Set.of(allowed));
  }

  public static void assertEndpoint(Method method, DynamicField... allowed) {
    assertExplicit(method.getGenericReturnType(), allowed);
    for (var parameter : method.getParameters())
      if (parameter.isAnnotationPresent(RequestBody.class))
        assertExplicit(parameter.getParameterizedType(), allowed);
  }

  private static void check(
      Type type,
      Map<TypeVariable<?>, Type> bindings,
      Set<Class<?>> path,
      Set<DynamicField> allowed) {
    if (type instanceof GenericArrayType array) {
      check(array.getGenericComponentType(), bindings, path, allowed);
      return;
    }
    if (type instanceof TypeVariable<?> variable) {
      Type resolved = bindings.get(variable);
      assertNotNull(resolved, "未绑定的类型参数：" + variable);
      check(resolved, bindings, path, allowed);
      return;
    }
    Class<?> model;
    var local = new HashMap<>(bindings);
    if (type instanceof ParameterizedType generic) {
      assertTrue(generic.getRawType() instanceof Class<?>, "不可描述的类型：" + type);
      model = (Class<?>) generic.getRawType();
      var arguments = generic.getActualTypeArguments();
      var variables = model.getTypeParameters();
      for (int i = 0; i < arguments.length; i++) {
        check(arguments[i], bindings, path, allowed);
        local.put(variables[i], arguments[i]);
      }
    } else {
      assertTrue(type instanceof Class<?>, "通配符或不可描述的类型：" + type);
      model = (Class<?>) type;
      assertFalse(Collection.class.isAssignableFrom(model), "原始集合隐藏元素类型：" + model);
    }
    assertNotEquals(Object.class, model, "Object隐藏业务契约");
    assertFalse(Map.class.isAssignableFrom(model), "固定结构不能使用Map：" + type);
    assertFalse(model.getName().startsWith("com.windasoft.eam.entity."), "接口不能暴露Entity：" + model);
    if (model.isArray()) {
      check(model.getComponentType(), bindings, path, allowed);
      return;
    }
    if (model.isEnum() || !model.getName().startsWith("com.windasoft.eam.")) {
      return;
    }
    if (!path.add(model)) {
      return;
    }
    try {
      for (var field : model.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())
            && !field.isSynthetic()
            && !allowed.contains(
                new DynamicField(model, field.getName(), field.getGenericType().getTypeName()))) {
          check(field.getGenericType(), local, path, allowed);
        }
      }
      Class<?> parent = model.getSuperclass();
      if (parent != null && parent.getName().startsWith("com.windasoft.eam.")) {
        check(model.getGenericSuperclass(), local, path, allowed);
      }
    } finally {
      path.remove(model);
    }
  }
}
