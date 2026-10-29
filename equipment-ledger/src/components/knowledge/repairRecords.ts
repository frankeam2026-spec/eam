export type RepairRecords = {
  data: {
    id: string;
    orderNo: string;
    equipmentName: string;
    equipmentCode: string;
    status: string;
    reportedAt: string;
    completedAt?: string;
  }[];
  total: number;
  dateField: 'reportedAt' | 'completedAt';
  criteria: { from: string; to: string; relation: string; completed: boolean; equipmentId: string };
};
