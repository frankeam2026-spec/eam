import type { CustomRecord } from './businessConfig';
import { request } from './api';
export type InventoryMasterKind = 'materials' | 'warehouses' | 'bins';
export type InventoryMaster = {
  id: string;
  code: string;
  name: string;
  enabled: boolean;
  remark: string;
  rowVersion?: number;
  model?: string;
  unit?: string;
  unitId?: string;
  category?: string;
  categoryId?: string;
  usageType?: string;
  referencePrice?: string;
  stockQuantity?: string;
  reservedQuantity?: string;
  availableQuantity?: string;
  batchEnabled?: boolean;
  expiryEnabled?: boolean;
  allocationRule?: string;
  companyId?: string;
  managerId?: string;
  warehouseId?: string;
};
export type InventoryChoices = {
  companies: { id: string; name: string }[];
  users: { id: string; name: string; companyId: string }[];
  warehouses: InventoryMaster[];
};
export const inventoryApi = {
  choices: () => request<{ data: InventoryChoices }>('/inventory/choices'),
  list: (
    kind: InventoryMasterKind,
    q: string,
    page: number,
    size: number,
    filters: Record<string, string> = {},
  ) =>
    request<{ data: InventoryMaster[]; page: { total: number } }>(
      `/inventory/masters/${kind}?${new URLSearchParams({ q, page: String(page), size: String(size), filters: JSON.stringify(filters) })}`,
    ),
  export: (kind: InventoryMasterKind, q: string, filters: Record<string, string>) =>
    request<{ data: InventoryMaster[] }>(
      `/inventory/masters/${kind}/export?${new URLSearchParams({ q, filters: JSON.stringify(filters) })}`,
    ),
  save: (kind: InventoryMasterKind, body: InventoryMaster) =>
    request<{ data: InventoryMaster }>(
      `/inventory/masters/${kind}${body.id ? '/' + body.id : ''}`,
      { method: body.id ? 'PUT' : 'POST', body: JSON.stringify(body) },
    ),
};

export type ReceiptLine = {
  id?: string;
  materialId: string;
  binId: string;
  quantity: string;
  unitPrice: string;
  allocatedFee: string;
  taxRate?: string;
  currency?: string;
  taxAmount?: string;
  grossAmount?: string;
  batchCode: string;
  supplierBatch: string;
  productionDate: string;
  expiryDate: string;
  totalCost?: string;
  materialName?: string;
  materialCode?: string;
  binName?: string;
  unit?: string;
  batchEnabled?: boolean;
  expiryEnabled?: boolean;
};
export type Receipt = CustomRecord & {
  id: string;
  code: string;
  warehouseId: string;
  warehouseName?: string;
  supplier: string;
  remark: string;
  status: string;
  rowVersion?: number;
  lines: ReceiptLine[];
  createdAt?: string;
  confirmedAt?: string;
  actorName?: string;
  events?: { action: string; actorName: string; createdAt: string; reason?: string }[];
};
export type ReceiptChoices = InventoryChoices & {
  materials: InventoryMaster[];
  bins: InventoryMaster[];
};
export const receiptApi = {
  materials: (q: string, page: number, size: number) =>
    request<{ data: InventoryMaster[]; page: { total: number } }>(
      `/inventory/receipts/materials?${new URLSearchParams({ q, page: String(page), size: String(size) })}`,
    ),
  delete: (body: Receipt) =>
    request(`/inventory/receipts/${body.id}`, {
      method: 'DELETE',
      body: JSON.stringify({ rowVersion: body.rowVersion }),
    }),
  allocate: (body: Receipt, totalFee: string) =>
    request<{ data: Receipt }>(`/inventory/receipts/${body.id}/allocate-fees`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion, totalFee }),
    }),
  choices: () => request<{ data: ReceiptChoices }>('/inventory/receipts/choices'),
  list: (
    q: string,
    page: number,
    size: number,
    filters: Record<string, string> = {},
    conditions = '[]',
  ) =>
    request<{ data: Receipt[]; page: { total: number } }>(
      `/inventory/receipts?${new URLSearchParams({ q, page: String(page), size: String(size), filters: JSON.stringify(filters), conditions })}`,
    ),
  export: (q: string, filters: Record<string, string>, conditions = '[]') =>
    request<{ data: Receipt[] }>(
      `/inventory/receipts/export?${new URLSearchParams({ q, filters: JSON.stringify(filters), conditions })}`,
    ),
  detail: (id: string) => request<{ data: Receipt }>(`/inventory/receipts/${id}`),
  save: (body: Receipt) =>
    request<{ data: Receipt }>(`/inventory/receipts${body.id ? '/' + body.id : ''}`, {
      method: body.id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    }),
  confirm: (body: Receipt) =>
    request<{ data: Receipt }>(`/inventory/receipts/${body.id}/confirm`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion }),
    }),
};

export type InventoryQueryKind = 'balances' | 'layers' | 'transactions' | 'transit';
export type InventoryQueryRow = { id: string; [key: string]: string | number | null };
export const inventoryQueryApi = {
  list: (
    kind: InventoryQueryKind,
    q: string,
    filters: Record<string, string>,
    page: number,
    size: number,
  ) =>
    request<{ data: InventoryQueryRow[]; page: { total: number } }>(
      `/inventory/queries/${kind}?${new URLSearchParams({ q, filters: JSON.stringify(filters), page: String(page), size: String(size) })}`,
    ),
  export: (kind: InventoryQueryKind, q: string, filters: Record<string, string>) =>
    request<{ data: InventoryQueryRow[] }>(
      `/inventory/queries/${kind}/export?${new URLSearchParams({ q, filters: JSON.stringify(filters) })}`,
    ),
};

export type RequisitionTask = {
  id: string;
  kind: string;
  code: string;
  companyId: string;
  name: string;
};
export type RequisitionLine = {
  materialModel?: string;
  id?: string;
  materialId: string;
  quantity: string;
  issuedQuantity?: string;
  reservedQuantity?: string;
  materialName?: string;
  materialCode?: string;
  unit?: string;
};
export type Requisition = {
  workflow?: import('./workflow').WorkflowRound[];
  id: string;
  code: string;
  warehouseId: string;
  warehouseName?: string;
  taskKind: string;
  taskId: string;
  taskCode?: string;
  applicantId?: string;
  applicantName?: string;
  remark: string;
  status: string;
  rowVersion?: number;
  createdAt?: string;
  lines: RequisitionLine[];
  issues?: InventoryIssue[];
  reservations?: {
    id: string;
    materialName: string;
    binName: string;
    batchCode?: string;
    remainingQuantity: string;
  }[];
  events?: { action: string; actorName: string; createdAt: string; reason?: string }[];
};
export type RequisitionChoices = ReceiptChoices & { tasks: RequisitionTask[] };
export const requisitionApi = {
  delete: (body: Requisition) =>
    request(`/inventory/requisitions/${body.id}`, {
      method: 'DELETE',
      body: JSON.stringify({ rowVersion: body.rowVersion }),
    }),
  pool: (id: string) =>
    request<{ data: Record<string, string>[] }>(`/inventory/requisitions/${id}/stock-pool`),
  manualReserve: (body: Requisition, selections: Record<string, string>[]) =>
    request<{ data: Requisition }>(`/inventory/requisitions/${body.id}/reserve`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion, selections }),
    }),
  choices: (paged = false, taskKind = '', taskId = '') =>
    request<{ data: RequisitionChoices }>(
      `/inventory/requisitions/choices?${new URLSearchParams({ paged: String(paged), taskKind, ...(taskId ? { taskId } : {}) })}`,
    ),
  list: (q: string, filters: Record<string, string>, page: number, size: number) =>
    request<{ data: Requisition[]; page: { total: number } }>(
      `/inventory/requisitions?${new URLSearchParams({ q, filters: JSON.stringify(filters), page: String(page), size: String(size) })}`,
    ),
  export: (q: string, filters: Record<string, string>) =>
    request<{ data: Requisition[] }>(
      `/inventory/requisitions/export?${new URLSearchParams({ q, filters: JSON.stringify(filters) })}`,
    ),
  detail: (id: string) => request<{ data: Requisition }>(`/inventory/requisitions/${id}`),
  save: (body: Requisition) =>
    request<{ data: Requisition }>(`/inventory/requisitions${body.id ? '/' + body.id : ''}`, {
      method: body.id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    }),
  action: (body: Requisition, action: 'submit' | 'reserve' | 'release' | 'cancel') =>
    request<{ data: Requisition }>(`/inventory/requisitions/${body.id}/${action}`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion }),
    }),
};

export type IssueAllocation = {
  id?: string;
  reservationId: string;
  materialName: string;
  binName: string;
  batchCode?: string;
  quantity: string;
  unitCost: string;
  amount: string;
};
export type InventoryIssue = {
  groupCode?: string;
  reversed?: boolean;
  id: string;
  code: string;
  totalAmount: string;
  actorName: string;
  createdAt: string;
  allocations: IssueAllocation[];
};
export type IssueSelection = { reservationId: string; quantity: string };
export type IssuePreview = {
  allocations: IssueAllocation[];
  totalAmount: string;
  fingerprint: string;
  rowVersion: number;
};
export const issueApi = {
  preview: (body: Requisition, selections: IssueSelection[]) =>
    request<{ data: IssuePreview }>(`/inventory/requisitions/${body.id}/issue-preview`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion, selections }),
    }),
  confirm: (
    body: Requisition,
    selections: IssueSelection[],
    preview: IssuePreview,
    confirmationKey: string,
  ) =>
    request<{ data: Requisition }>(`/inventory/requisitions/${body.id}/issue-confirm`, {
      method: 'POST',
      body: JSON.stringify({
        rowVersion: preview.rowVersion,
        selections,
        fingerprint: preview.fingerprint,
        confirmationKey,
      }),
    }),
};

export type OperationKind = 'returns' | 'transfers' | 'counts' | 'openings' | 'reversals';
export type OperationLine = Record<string, string>;
export type InventoryOperation = {
  id: string;
  code: string;
  kind: OperationKind;
  warehouseId: string;
  warehouseName?: string;
  targetWarehouseId?: string;
  status: string;
  rowVersion?: number;
  createdAt?: string;
  form: {
    remark: string;
    lines: OperationLine[];
    receiptId?: string;
    sourceId?: string;
    sourceKind?: string;
    sourceCode?: string;
    conversionMaterialId?: string;
    receiptCode?: string;
    baselineAt?: string;
  };
  movements?: OperationLine[];
  events?: { actorName: string; action: string; createdAt: string; reason?: string }[];
  [key: string]: unknown;
};
export type OperationChoices = ReceiptChoices & {
  balances: OperationLine[];
  allocations: OperationLine[];
  receipts: OperationLine[];
  correctionSources?: OperationLine[];
  legacy: OperationLine[];
};
export const inventoryMenu = (kind: string) =>
  (
    ({
      materials: 'inventoryMaterial',
      warehouses: 'inventoryWarehouse',
      bins: 'inventoryWarehouse',
      receipts: 'inventoryReceipt',
      requisitions: 'inventoryRequisition',
      'pending-issues': 'inventoryOutbound',
      outbounds: 'inventoryOutbound',
      returns: 'inventoryReturn',
      transfers: 'inventoryTransfer',
      counts: 'inventoryCount',
      openings: 'inventoryOpening',
      reversals: 'inventoryReversal',
      balances: 'inventoryQuery',
      layers: 'inventoryQuery',
      transactions: 'inventoryQuery',
      transit: 'inventoryQuery',
    }) as Record<string, string>
  )[kind] || 'inventoryQuery';
export const operationApi = {
  choices: (kind: OperationKind) =>
    request<{ data: OperationChoices }>(`/inventory/operations/${kind}/choices`),
  list: (
    kind: OperationKind,
    q: string,
    filters: Record<string, string>,
    page: number,
    size: number,
  ) =>
    request<{ data: InventoryOperation[]; page: { total: number } }>(
      `/inventory/operations/${kind}?${new URLSearchParams({ q, filters: JSON.stringify(filters), page: String(page), size: String(size) })}`,
    ),
  export: (kind: OperationKind, q: string, filters: Record<string, string>) =>
    request<{ data: InventoryOperation[] }>(
      `/inventory/operations/${kind}/export?${new URLSearchParams({ q, filters: JSON.stringify(filters) })}`,
    ),
  detail: (kind: OperationKind, id: string) =>
    request<{ data: InventoryOperation }>(`/inventory/operations/${kind}/${id}`),
  save: (kind: OperationKind, body: InventoryOperation) =>
    request<{ data: InventoryOperation }>(
      `/inventory/operations/${kind}${body.id ? '/' + body.id : ''}`,
      {
        method: body.id ? 'PUT' : 'POST',
        body: JSON.stringify({
          ...body.form,
          warehouseId: body.warehouseId,
          targetWarehouseId: body.targetWarehouseId,
          rowVersion: body.rowVersion,
        }),
      },
    ),
  confirm: (kind: OperationKind, body: InventoryOperation, receive = false) =>
    request<{ data: InventoryOperation }>(
      `/inventory/operations/${kind}/${body.id}/${receive ? 'receive' : 'confirm'}`,
      { method: 'POST', body: JSON.stringify({ rowVersion: body.rowVersion }) },
    ),
  delete: (kind: OperationKind, body: InventoryOperation) =>
    request(`/inventory/operations/${kind}/${body.id}`, {
      method: 'DELETE',
      body: JSON.stringify({ rowVersion: body.rowVersion }),
    }),
  freeze: (body: InventoryQueryRow, state: string, reason: string) =>
    request(`/inventory/balances/${body.id}/state`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: body.rowVersion, stockStatus: state, reason }),
    }),
};
