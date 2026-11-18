package com.windasoft.eam.dto.system;

import com.windasoft.eam.common.LocalizedArgumentException;

/** 品牌素材已有业务种类及其上传上限，名称与接口参数保持一致。 */
public enum BrandingAssetKind {
  LOGO("logo", 2),
  BACKGROUND("background", 10);
  private final String code;
  private final int limitMiB;

  BrandingAssetKind(String code, int limitMiB) {
    this.code = code;
    this.limitMiB = limitMiB;
  }

  public String code() {
    return code;
  }

  public int limitMiB() {
    return limitMiB;
  }

  public static BrandingAssetKind from(String code) {
    for (var kind : values()) if (kind.code.equals(code)) return kind;
    throw new LocalizedArgumentException("server.system.message0011");
  }
}
