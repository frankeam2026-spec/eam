import { describe, it, expect, vi, afterEach } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { reactive, nextTick } from 'vue';
import EquipmentEditor from '../components/EquipmentEditor.vue';
import EquipmentRelationsEditor from '../components/equipment/EquipmentRelationsEditor.vue';
import * as api from '../api';

afterEach(() => vi.restoreAllMocks());

describe('equipment editor save boundaries', () => {
  it('retains the core draft when a relation is saved and advances only the version and relation', async () => {
    vi.spyOn(api, 'equipmentBatchOptions').mockResolvedValue({ departments: [], users: [] });
    const record = reactive({
      id: '901',
      code: 'EQ-901',
      name: 'Original',
      rowVersion: 2,
      children: [],
      relatedSpares: [],
      documents: [],
    } as unknown as api.EquipmentRecord);
    const wrapper = shallowMount(EquipmentEditor, {
      props: { modelValue: record, open: true, isNew: false, busy: false, dirty: true },
      global: {
        plugins: [createPinia()],
        renderStubDefaultSlot: true,
        stubs: {
          ElButton: { template: '<button><slot /></button>' },
          ElTableColumn: { template: '<span />' },
        },
      },
    });
    await flushPromises();
    record.name = 'Unsaved equipment name';
    await nextTick();
    const button = wrapper.findAll('button').find((b) => b.text() === '维护设备 BOM');
    expect(button).toBeDefined();
    await button!.trigger('click');
    const relation = wrapper.findComponent(EquipmentRelationsEditor);
    expect(relation.props('record').name).toBe('Original');
    expect(relation.props('coreDirty')).toBe(true);
    relation.vm.$emit('saved', {
      ...record,
      name: 'Original',
      rowVersion: 3,
      relatedSpares: [{ materialId: '5', quantity: '2.00' }],
    });
    await nextTick();
    expect(record.name).toBe('Unsaved equipment name');
    expect(record.rowVersion).toBe(3);
    expect(record.relatedSpares?.[0]?.quantity).toBe('2.00');
    expect(wrapper.emitted('save')).toBeUndefined();
    wrapper.unmount();
  });

  it('sends only relation rows and refuses a missing version before issuing a request', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ data: {} }), { status: 200 }));
    vi.stubGlobal('fetch', fetcher);
    try {
      await api.saveEquipmentRelations('901', 'bom', 4, []);
      expect(fetcher.mock.calls[0]?.[0]).toBe('/api/v1/equipment/901/bom?rowVersion=4');
      expect(JSON.parse(fetcher.mock.calls[0]?.[1]?.body)).toEqual({ rows: [] });
      await expect(api.saveEquipmentRelations('901', 'bom', undefined, [])).rejects.toThrow();
      expect(fetcher).toHaveBeenCalledTimes(1);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
