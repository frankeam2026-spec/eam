package com.windasoft.eam.service.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.support.ContractTypes;
import com.windasoft.eam.dto.imports.PreparedEquipmentImport;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessValueTypeTest {
  @Test
  void normalizedStorageRowsHaveExplicitTypes() throws Exception {
    var result =
        (ParameterizedType)
            BusinessValueService.class
                .getMethod("normalize", String.class, JsonNode.class, JsonNode.class, List.class)
                .getGenericReturnType();
    ContractTypes.assertExplicit(result.getActualTypeArguments()[0]);
    var prepared=(ParameterizedType)PreparedEquipmentImport.class.getDeclaredMethod("custom").getGenericReturnType();
    ContractTypes.assertExplicit(prepared.getActualTypeArguments()[1]);
  }
}
