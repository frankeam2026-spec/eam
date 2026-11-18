package com.windasoft.eam.controller.business;

import com.windasoft.eam.dto.business.BusinessChoices.Choice;
import com.windasoft.eam.support.ContractTypes;
import org.junit.jupiter.api.Test;

class BusinessChoicesContractTest {
  @Test
  void choicePageHasExplicitContract() throws Exception {
    ContractTypes.assertExplicit(
        BusinessConfigController.class
            .getMethod(
                "options",
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                boolean.class)
            .getGenericReturnType());
    for (var variant : Choice.class.getPermittedSubclasses()) ContractTypes.assertExplicit(variant);
  }
}
