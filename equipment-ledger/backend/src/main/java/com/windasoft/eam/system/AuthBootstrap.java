package com.windasoft.eam.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap implements CommandLineRunner {
    private final SystemUserMapper users;
    private final long tenantId;
    private final String password;
    public AuthBootstrap(SystemUserMapper users,@Value("${eam.security.demo-tenant-id:10001}") long tenantId,@Value("${eam.security.bootstrap-admin-password:}") String password){this.users=users;this.tenantId=tenantId;this.password=password;}
    @Override public void run(String... args){
        if(password.isBlank())return;
        if(password.length()<12||password.length()>72)throw new IllegalStateException("EAM_BOOTSTRAP_ADMIN_PASSWORD 长度需为 12 至 72 位");
        var admin=users.selectOne(new LambdaQueryWrapper<SystemUser>().eq(SystemUser::getTenantId,tenantId).eq(SystemUser::getAccount,"admin"));
        if(admin!=null&&admin.getPasswordHash()==null){admin.setPasswordHash(new BCryptPasswordEncoder().encode(password));users.updateById(admin);}
    }
}
