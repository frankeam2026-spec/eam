import { ElMessageBox } from 'element-plus';
export async function confirmAction(message: string, title = '确认操作') {
  try {
    await ElMessageBox.confirm(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
    });
    return true;
  } catch {
    return false;
  }
}
