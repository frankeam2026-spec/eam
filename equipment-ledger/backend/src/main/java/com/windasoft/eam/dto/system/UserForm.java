package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;
import java.util.*;

public record UserForm(
    @NotBlank String account,
    @NotBlank String name,
    String employeeNo,
    String phone,
    @NotNull Long orgId,
    List<Long> roleIds,
    Integer status,
    String password,
    @Email @Size(max=254) String email,
    @Min(0) Integer emailVersion) {
  public UserForm(String account,String name,String employeeNo,String phone,Long orgId,List<Long> roleIds,Integer status,String password) { this(account,name,employeeNo,phone,orgId,roleIds,status,password,null,null); }
  public UserForm(String account,String name,String employeeNo,String phone,Long orgId,List<Long> roleIds,Integer status,String password,String email) { this(account,name,employeeNo,phone,orgId,roleIds,status,password,email,0); }
}
