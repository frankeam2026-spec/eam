import { describe, expect, it } from 'vitest';
import { shallowMount } from '@vue/test-utils';
import InspectionProjectEditor from '../components/common/InspectionProjectEditor.vue';
import { emptyItem } from '../maintenance';
import { tr } from '../i18n';

describe('inspection standard project headers', () => {
  it('keeps the full schema when creating a standard and after deleting its last project', async () => {
    const wrapper = shallowMount(InspectionProjectEditor, {
      props: { modelValue: [], types: [] },
      global: {
        stubs: {
          AppBusinessTable: { template: '<div><slot /></div>' },
          ElTableColumn: { template: '<header><slot name="header" /></header>' },
        },
      },
    });
    const headers = () => wrapper.findAll('header').map((header) => header.text());
    const emptyHeaders = headers();
    expect(emptyHeaders).toHaveLength(14);
    for (const key of [
      'ui.equipment.text0325', // type
      'ui.equipment.text0399', // content
      'ui.equipment.text0400', // value type
      'ui.equipment.text1471', // lower bound
      'ui.equipment.text1472', // upper bound
      'ui.equipment.text0787', // options
      'ui.equipment.text0789', // normal options
      'ui.equipment.text0794', // required
      'ui.equipment.text1474', // photo required
      'ui.equipment.text0130', // remark
      'ui.equipment.text0798', // references
    ])
      expect(emptyHeaders.some((header) => header.includes(tr(key)))).toBe(true);
    await wrapper.setProps({ modelValue: [emptyItem('')] });
    expect(headers()).toEqual(emptyHeaders);
    await wrapper.setProps({ modelValue: [] });
    expect(headers()).toEqual(emptyHeaders);
    wrapper.unmount();
  });
});
