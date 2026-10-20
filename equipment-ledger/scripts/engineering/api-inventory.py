"""Generate a reviewable HTTP entrypoint inventory from Spring controller annotations."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
controllers=root/'backend/src/main/java/com/windasoft/eam/controller'
modules=['equipment','repair','maintenance','inspection','inventory','system','planning']
rows=[]
for module in modules:
 for path in sorted((controllers/module).glob('*.java')):
  source=path.read_text();base=re.search(r'@RequestMapping\("([^\"]+)"\)',source)
  if not base:continue
  if module=='system' and path.stem not in ['SystemManagementController']:continue
  for match in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\(([^\n]*)\))?',source):
   route=re.search(r'"([^\"]*)"',match[2] or '');suffix=route[1] if route else ''
   following=source[match.end():];method=re.search(r'public\s+[^\n{]*?\s+(\w+)\s*\(',following)
   rows.append((module,match[1].upper(),base[1]+suffix,path.stem,method[1] if method else '待人工确认'))
header='''# 工程改造接口清单

本清单由 `scripts/engineering/api-inventory.py` 从 Controller 入口生成；权限和事务规则以下方人工维护的契约为准，不通过路径猜测授权。

| 模块 | 调用方 | 权限与数据范围 | 版本及错误约定 |
|---|---|---|---|
| 设备 | 台账、编辑、档案、选择器 | equipment 对应操作；公司/部门/负责人范围，子设备/BOM保存重新校验 | 修改传 rowVersion；扩展配置传 customSchemaVersion |
| 维修 | 工单列表、报修、处理、工作台 | repair 对应操作；报修人/负责人/候选人/验收参与例外保留 | 修改/状态动作传 rowVersion；待派工才可编辑 |
| 保养 | 类型、标准、计划、任务 | maintenanceType/Standard/Plan/Task；任务参与者查看例外与执行/验收判定分开 | rowVersion；计划与任务独立扩展配置 |
| 点巡检 | 标准、日常/周期计划与任务 | inspection 各子菜单；任务参与者范围保留 | rowVersion；暂存/提交校验不同 |
| 库存 | 物料、仓库、入出库、领料与纠正 | 各库存子菜单与仓库范围；金额独立授权，不能由查看仓库代替业务授权 | rowVersion；出库确认另带幂等键和分配指纹 |
| 用户 | 用户列表、编辑、批量操作 | user 对应操作与组织范围；不扩大批量目标 | 保留现有更新版本契约，缺口逐入口登记 |

列表新查询契约：`data`＋`page:{number,size,total}`；错误保留 `code,message,fieldErrors`。400字段错误定位控件，401统一登录失效，403拒绝，409保留草稿；导出叠加导出授权。二进制导出通过公共文件请求读取错误。

## 当前 HTTP 入口

| 模块 | 方法 | 路径 | Controller | 处理方法 |
|---|---|---|---|---|
'''
output=header+'\n'.join('| '+' | '.join(row)+' |' for row in rows)+'\n'
(root/'docs/engineering/API清单.md').write_text(output)
print(f'{len(rows)} controller entrypoints inventoried')
