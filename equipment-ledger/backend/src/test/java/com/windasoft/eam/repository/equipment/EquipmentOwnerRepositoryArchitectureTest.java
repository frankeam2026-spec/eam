package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;

/** 负责人关系写入归属于 EquipmentOwnerRepository，不得回流至设备主表仓储。 */
class EquipmentOwnerRepositoryArchitectureTest {
  @Test
  void ownerEntityMapsTheRealCompositeKeyTable() {
    var table = EquipmentOwner.class.getAnnotation(
        com.baomidou.mybatisplus.annotation.TableName.class);
    assertEquals("eam_equipment_owner", table.value());
    assertFalse(Arrays.stream(EquipmentOwner.class.getDeclaredFields())
        .anyMatch(field -> field.isAnnotationPresent(
            com.baomidou.mybatisplus.annotation.TableId.class)));
  }

  @Test
  void equipmentWriteRepositoryDoesNotOwnOwnerRelationWrites() throws Exception {
    var methodNames = Arrays.stream(EquipmentWriteRepository.class.getDeclaredMethods())
        .map(Method::getName).toList();
    for (String name : new String[] {"owners", "addOwner", "deleteOwners"}) {
      assertFalse(methodNames.contains(name), "负责人关系归属于 EquipmentOwnerRepository: " + name);
    }
    try (var in = EquipmentWriteRepository.class.getResourceAsStream("EquipmentWriteRepository.class")) {
      var containsOwnerTable = new boolean[1];
      new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
          return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitLdcInsn(Object value) {
              if (value instanceof String literal && literal.contains("eam_equipment_owner")) {
                containsOwnerTable[0] = true;
              }
            }
          };
        }
      }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      assertFalse(containsOwnerTable[0], "设备主表仓储不应直接写入负责人表");
    }
  }
}
