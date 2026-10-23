import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, reactive, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';

const wrappers: ReturnType<typeof mount>[] = [];
// Element Plus resolves confirm with an action string; its declared return is an intersection.
type ConfirmResult = Awaited<ReturnType<typeof ElMessageBox.confirm>>;
const confirmed = 'confirm' as unknown as ConfirmResult;
function form() {
  const state = reactive({ name: 'original' });
  const busy = ref(false);
  let guard!: ReturnType<typeof useUnsavedChanges>;
  wrappers.push(
    mount(
      defineComponent({
        setup() {
          guard = useUnsavedChanges(
            () => state,
            () => true,
            () => busy.value,
            { localOnly: true },
          );
          guard.markSaved();
          return () => null;
        },
      }),
    ),
  );
  return { state, busy, guard };
}
beforeEach(() => {
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
    callback(0);
    return 0;
  });
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
});

describe('shared unsaved-change confirmation', () => {
  it('uses the same continue/discard labels and continues editing without acknowledging the draft', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(confirmed);
    const { state, guard } = form();
    state.name = 'edited';
    expect(await guard.permitLeave()).toBe(false);
    expect(guard.dirty.value).toBe(true);
    expect(state.name).toBe('edited');
    const [message, title, options] = confirm.mock.calls[0]!;
    expect(title).toBe('有未保存的修改');
    expect(message).toContain('放弃修改');
    expect(options!.confirmButtonText).toBe('继续编辑');
    expect(options!.cancelButtonText).toBe('放弃修改');
    expect(options!.closeOnClickModal).toBe(false);
    expect(options!.closeOnPressEscape).toBe(false);
  });
  it('leaves only after explicit discard and does not prompt again for that acknowledged draft', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('cancel');
    const { state, guard } = form();
    state.name = 'edited';
    expect(await guard.permitLeave()).toBe(true);
    expect(guard.dirty.value).toBe(false);
    expect(await guard.permitLeave()).toBe(true);
    expect(confirm).toHaveBeenCalledOnce();
  });
  it('keeps changes when the confirmation is closed', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue('close');
    const { state, guard } = form();
    state.name = 'edited';
    expect(await guard.permitLeave()).toBe(false);
    expect(guard.dirty.value).toBe(true);
  });
  it('skips clean forms and blocks departure while saving without showing a dialog', async () => {
    const confirm = vi.spyOn(ElMessageBox, 'confirm');
    const { state, busy, guard } = form();
    expect(await guard.permitLeave()).toBe(true);
    state.name = 'edited';
    busy.value = true;
    expect(await guard.permitLeave()).toBe(false);
    expect(confirm).not.toHaveBeenCalled();
  });
  it('shares one dialog when multiple navigation guards fire together', async () => {
    let resolve!: (value: ConfirmResult) => void;
    const confirm = vi.spyOn(ElMessageBox, 'confirm').mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const { state, guard } = form();
    state.name = 'edited';
    const first = guard.permitLeave();
    const second = guard.permitLeave();
    resolve(confirmed);
    expect(await first).toBe(false);
    expect(await second).toBe(false);
    expect(confirm).toHaveBeenCalledOnce();
  });
});
