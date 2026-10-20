import { tr } from '../i18n';
import { ElMessageBox } from 'element-plus';
import { request } from '../api';
import { remainingMaterialQuantity } from '../utils/taskMaterialQuantities';
export async function confirmSurplus(
  kind: string,
  taskId: string,
): Promise<Record<string, unknown> | null> {
  const { data } = await request<{ data: { allocations: Record<string, any>[] } }>(
    `/inventory/task-materials/${encodeURIComponent(kind)}/${taskId}`,
  );
  const remaining = data.allocations.filter((a) => Number(remainingMaterialQuantity(a)) > 0);
  if (!remaining.length) return {};
  try {
    const { value } = await ElMessageBox.prompt(
      `当前有 ${remaining.length} 项已领未用物料。任务完成后继续由领用人暂存，可在“我的余料”查询和退料。请填写暂存说明。`,
      '确认余料暂存',
      {
        inputType: 'textarea',
        inputValidator: (v) => !!v?.trim() || tr('ui.validation.message39'),
        get confirmButtonText() { return tr('ui.equipment.text1811'); },
        get cancelButtonText() { return tr('ui.equipment.text1707'); },
        closeOnClickModal: false,
        closeOnPressEscape: false,
      },
    );
    return { surplusRemark: value.trim() };
  } catch {
    return null;
  }
}
