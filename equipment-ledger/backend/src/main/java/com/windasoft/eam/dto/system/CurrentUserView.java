package com.windasoft.eam.dto.system;

import java.util.Map;
import java.util.Set;

/** 菜单键为真实动态字典，值为已授权操作集合。 */
public record CurrentUserView(
    AuthenticatedUserView data, Map<String, Set<String>> permissions, boolean developmentMode) {}
