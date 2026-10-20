import { tr } from '../i18n';
import { ElMessageBox } from 'element-plus';
export async function confirmAction(message: string, title = tr('common.dialog.confirmTitle')) {
  try {
    await ElMessageBox.confirm(message, title, {
      get confirmButtonText() { return tr('ui.equipment.text0402'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      type: 'warning',
      closeOnClickModal: false,
    });
    return true;
  } catch {
    return false;
  }
}
