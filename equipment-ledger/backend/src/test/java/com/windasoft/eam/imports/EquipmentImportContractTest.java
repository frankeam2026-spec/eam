package com.windasoft.eam.imports;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.imports.EquipmentImportFields;
import java.util.*;
import org.junit.jupiter.api.Test;

class EquipmentImportContractTest {
  @Test
  void everyCoreFormFieldHasAnExplicitStagingColumn() {
    var excluded =
        Set.of(
            "ownerId",
            "ownerIds",
            "customFields",
            "customSchemaVersion",
            "imageName",
            "imageFileId",
            "images",
            "documents",
            "children",
            "relatedSpares");
    var expected = new HashSet<String>();
    for (var c : EquipmentForm.class.getRecordComponents())
      if (!excluded.contains(c.getName())) expected.add(c.getName());
    var actual = new HashSet<>(EquipmentImportFields.STAGING.keySet());
    actual.remove("parentCode");
    assertEquals(expected, actual, "New core fields require an explicit import storage decision");
    assertFalse(EquipmentImportFields.STAGING.containsValue("payload"));
  }
}
