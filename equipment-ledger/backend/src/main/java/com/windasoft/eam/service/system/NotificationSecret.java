package com.windasoft.eam.service.system;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationSecret {
  private final String key;
  public NotificationSecret(@Value("${eam.notification.secret-key:${EAM_NOTIFICATION_SECRET_KEY:}}") String key) {this.key=key;}
  private SecretKeySpec key() {
    try {var bytes=Base64.getDecoder().decode(key);if(bytes.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(bytes,"AES");}
    catch(Exception e){throw new IllegalStateException("请配置EAM_NOTIFICATION_SECRET_KEY（Base64编码的32字节密钥）");}
  }
  public String encrypt(String value) {
    try {byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,nonce));return Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}
  }
  public String decrypt(String value) {
    if(value==null || value.isBlank())return "";
    try {var parts=value.split(":");var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}
    catch(Exception e){throw new IllegalStateException("无法解密邮件凭据，请检查系统密钥",e);}
  }
}
