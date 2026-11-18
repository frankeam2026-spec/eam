package com.windasoft.eam.dto.system;

/** 匿名登录页只暴露当前品牌展示字段，不返回上传人、版本或内部图片标识。 */
public record PublicBrandingView(
    String systemName, String logoUrl, String backgroundUrl, String backgroundPosition) {}
