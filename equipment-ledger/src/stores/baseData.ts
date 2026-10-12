import { ref } from 'vue';
import { defineStore } from 'pinia';
import * as api from '../api';
export const BASE_TABS = [
  '设备分类',
  '物料分类',
  '单位',
  '故障类型',
  '故障原因',
  '使用状态',
  '设备来源',
  '处理结果',
];
export type BaseItem = api.BaseRecord;
export const useBaseDataStore = defineStore('base-data', () => {
  const categoryData = ref<BaseItem[]>([]);
  const materialCategoryData = ref<BaseItem[]>([]);
  const locationData = ref<BaseItem[]>([]);
  const faultTypeData = ref<BaseItem[]>([]);
  const faultCauseData = ref<BaseItem[]>([]);
  const unitData = ref<BaseItem[]>([]);
  const useStateData = ref<BaseItem[]>([]);
  const sourceData = ref<BaseItem[]>([]);
  const processingResultData = ref<BaseItem[]>([]);
  const collections: Record<string, typeof categoryData> = {
    设备分类: categoryData,
    物料分类: materialCategoryData,
    单位: unitData,
    存放地点: locationData,
    故障类型: faultTypeData,
    故障原因: faultCauseData,
    使用状态: useStateData,
    设备来源: sourceData,
    处理结果: processingResultData,
  };
  const pending = new Map<string, Promise<void>>();
  async function load(tab: string) {
    if (pending.has(tab)) return pending.get(tab);
    const target = collections[tab];
    if (!target) return;
    const request = api
      .listBase(tab)
      .then((result) => {
        target.value = result.data.map((item) => ({ ...item, parentId: item.parentId || '' }));
      })
      .finally(() => pending.delete(tab));
    pending.set(tab, request);
    return request;
  }
  function clear() {
    Object.values(collections).forEach((items) => {
      items.value = [];
    });
  }
  return {
    categoryData,
    materialCategoryData,
    locationData,
    faultTypeData,
    faultCauseData,
    unitData,
    useStateData,
    sourceData,
    processingResultData,
    load,
    clear,
  };
});
