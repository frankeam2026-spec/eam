package com.windasoft.eam.dto.system;

import java.util.List;

/** 菜单键可以动态配置，单个授权的动作、范围和关联标识始终保持固定结构。 */
public record MenuGrantView(List<String> actions, List<String> dataScopes, List<String> orgIds) {}
