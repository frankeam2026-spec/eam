import { ElMessageBox } from 'element-plus';
import { tr } from '../../i18n';

/** Shared unsaved-change dialog. True means explicit consent to discard. */
export function confirmUnsavedChanges(): Promise<boolean> {
  const pending = ElMessageBox.confirm(
    tr('equipment.editor.leaveMessage'),
    tr('equipment.editor.unsaved'),
    {
      get confirmButtonText() {
        return tr('equipment.editor.continue');
      },
      get cancelButtonText() {
        return tr('equipment.editor.discard');
      },
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
      distinguishCancelAndClose: true,
      autofocus: false,
      customClass: 'eam-unsaved-confirmation',
      confirmButtonClass: 'unsaved-continue-button',
    },
  )
    .then(() => false)
    .catch((action) => action === 'cancel');
  requestAnimationFrame(() =>
    document
      .querySelector<HTMLButtonElement>('.eam-unsaved-confirmation .unsaved-continue-button')
      ?.focus(),
  );
  return pending;
}
