package com.windasoft.eam.equipment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NumberCounterMapper {
    @Insert("INSERT INTO sys_number_counter(rule_code, period_key, next_value) VALUES ('equipment', #{year}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_value=LAST_INSERT_ID(next_value+1)")
    int nextEquipmentNumber(@Param("year") String year);
    @Insert("INSERT INTO sys_number_counter(rule_code, period_key, next_value) VALUES ('repair', #{year}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_value=LAST_INSERT_ID(next_value+1)")
    int nextRepairNumber(@Param("year") String year);
    @Insert("INSERT INTO sys_number_counter(rule_code, period_key, next_value) VALUES (#{rule}, #{month}, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE next_value=LAST_INSERT_ID(next_value+1)")
    int nextMonthlyNumber(@Param("rule") String rule, @Param("month") String month);
    @Select("SELECT LAST_INSERT_ID()")
    long currentValue();
}
