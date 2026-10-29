export type EquipmentRecords = {
  data: {
    id: string;
    code: string;
    name: string;
    model: string;
    state: string;
    useState: string;
  }[];
  total: number;
  filters: { field: string; operator: string; value: string }[];
  equipmentId: string;
};
