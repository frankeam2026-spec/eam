package com.windasoft.eam.controller.system;

import org.junit.jupiter.api.Assertions;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.windasoft.eam.dto.system.WorkInboxViews;

import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class WorkInboxContractTest {
  @Test
  void requestAndResponseTypesAreExplicit() {
    for (var m : WorkInboxController.class.getDeclaredMethods())
      if (Modifier.isPublic(m.getModifiers())) {
        ContractTypes.assertExplicit(m.getGenericReturnType());
        for (var p : m.getGenericParameterTypes()) ContractTypes.assertExplicit(p);
      }
  }

  @Test
  void homeCardKeepsItsOriginalFlatJson() throws Exception {
    var mapper = new ObjectMapper();
    var row =
        new WorkInboxViews.Row(
            "1",
            "repair",
            "2",
            "WX-2",
            "设备",
            "待派工",
            "待派工",
            "标题",
            null,
            null,
            1,
            0,
            0,
            "",
            "",
            "2026-11-18 08:00:00");
    var value =
        mapper.valueToTree(
            new WorkInboxViews.Card(row, "WX-2", "标题", "/repairs/2"));
    Assertions.assertFalse(value.has("row"));
    Assertions.assertEquals("1", value.path("id").asText());
    Assertions.assertEquals("WX-2", value.path("code").asText());
    Assertions.assertEquals("/repairs/2", value.path("url").asText());
    Assertions.assertEquals(19, value.size());
  }
}
