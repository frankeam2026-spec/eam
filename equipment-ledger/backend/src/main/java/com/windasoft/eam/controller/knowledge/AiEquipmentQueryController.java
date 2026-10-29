package com.windasoft.eam.controller.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.knowledge.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge/assistant/equipment-records")
public class AiEquipmentQueryController {
  private final AiEquipmentQueryService service;
  private final ObjectMapper json;
  public AiEquipmentQueryController(AiEquipmentQueryService service,ObjectMapper json){this.service=service;this.json=json;}
  @GetMapping public Object query(@RequestParam(defaultValue="[]")String filters,@RequestParam(required=false)Long equipmentId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size) throws Exception {
    if(filters.length()>4000)throw new IllegalArgumentException("设备查询条件过长");
    final List<AiQuestionRouter.Filter> parsed;
    try{parsed=json.readValue(filters,new TypeReference<List<AiQuestionRouter.Filter>>(){});}
    catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("设备查询条件无效");}
    return service.query(parsed,equipmentId,page,size);
  }
}
