import { tr } from './i18n';
import { currentLocale } from './i18n';
export type EquipmentRecord = import('./businessConfig').CustomRecord & {
  id: string;
  code: string;
  name: string;
  category: string;
  categoryId?: string;
  unitId?: string;
  useStateId?: string;
  sourceId?: string;
  ownerId?: string;
  ownerIds?: string[];
  owners?: { id: string; name: string }[];
  departmentPath?: string;
  model: string;
  serial: string;
  company: string;
  companyOrgId?: string;
  departmentOrgId?: string;
  department: string;
  owner: string;
  brand: string;
  state: string;
  useState: string;
  source: string;
  acquiredAt: string;
  amount: string;
  warranty: string;
  remark: string;
  rowVersion?: number;
  assetNo?: string;
  tagCode?: string;
  unit?: string;
  supplier?: string;
  purchaseDate?: string;
  inServiceDate?: string;
  retirementDate?: string;
  level?: string;
  functionLocation?: string;
  locationId?: string;
  specialEquipment?: string;
  metrologyEquipment?: string;
  depreciationEnabled?: string;
  technicalParams?: string;
  imageName?: string;
  imageFileId?: string;
  images?: Array<{ id: string; name: string }>;
  calibrationDate?: string;
  calibrationValidUntil?: string;
  inspectorName?: string;
  documents?: Array<{
    id?: string;
    url?: string;
    size?: number;
    originalName?: string;
    code: string;
    name: string;
    type: string;
    security: string;
    quantity: number;
    remark: string;
  }>;
  children?: Array<{
    equipmentId?: string;
    code: string;
    name: string;
    brand: string;
    model: string;
    legacy?: boolean;
    legacyIndex?: number;
    legacyReason?: string;
  }>;
  relatedSpares?: Array<{
    materialId?: string;
    code: string;
    name: string;
    specification: string;
    unit?: string;
    quantity: string;
    legacy?: boolean;
    legacyIndex?: number;
    legacyReason?: string;
  }>;
};
export type BaseRecord = {
  companyId?: string;
  id: string;
  code: string;
  name: string;
  parentId: string;
  precision?: number;
  builtin?: boolean;
  status: string;
  sortNo?: number;
};
export type RepairAttachment = {
  id: string;
  name: string;
  contentType: string;
  size: number;
  url: string;
  dataUrl?: string;
};
export type RepairRecord = import('./businessConfig').CustomRecord & {
  equipmentCategoryId?: string;
  faultTypeId?: string;
  faultCauseId?: string;
  processingResultId?: string;
  faultCause?: string;
  repairMethod?: string;
  processingResult?: string;
  preventiveMeasures?: string;
  collaborators?: { id: string; name: string }[];
  processAttachments?: RepairAttachment[];
  outsourceVendor?: string;
  id: string;
  orderNo: string;
  equipmentCode: string;
  equipmentName: string;
  department: string;
  faultType: string;
  severity: string;
  description: string;
  reporter: string;
  assignee: string;
  status: '待派工' | '待接单' | '处理中' | '挂起' | '待验收' | '已完成' | '已撤销';
  repairAction: string;
  downtimeMinutes: number;
  reportedAt: string;
  attachments: RepairAttachment[];
  rowVersion?: number;
};
export type OrganizationRecord = {
  id: string;
  name: string;
  code: string;
  type: '集团' | '公司' | '部门';
  parentId: string;
  disabled?: boolean;
  manager: string;
  status: '启用' | '停用';
};
export type SystemUserRecord = {
  id: string;
  account: string;
  name: string;
  employeeNo: string;
  phone: string;
  email?: string;
  emailVersion?: number;
  orgId: string;
  roleIds: string[];
  status: '启用' | '停用';
  password?: string;
};
export type CurrentUser = {
  id: string;
  tenantId: string;
  account: string;
  name: string;
  orgId: string;
  company: string;
  department: string;
};
export type SystemRoleRecord = {
  id: string;
  code: string;
  name: string;
  description: string;
  status: '启用' | '停用';
  menuPermissions: import('./rolePermissions').MenuGrants;
  dataScope?: string;
  orgIds?: string[];
  permissions?: string[];
};
const root = '/api/v1';
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public fieldErrors: Record<string, string> = {},
    public code: string = 'REQUEST_ERROR',
  ) {
    super(message);
  }
}
async function checkedResponse(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);
  headers.set('Accept-Language', currentLocale.value);
  if (!(init?.body instanceof FormData) && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json');
  const response = await fetch(root + path, { ...init, credentials: 'same-origin', headers });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== '/auth/me' && path !== '/auth/login')
      window.dispatchEvent(new Event('eam-session-expired'));
    throw new ApiError(
      payload.message || '请求失败 (' + response.status + ')',
      response.status,
      payload.fieldErrors || {},
      payload.code || 'REQUEST_ERROR',
    );
  }
  return response;
}
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await checkedResponse(path, init);
  return response.json().catch(() => ({})) as Promise<T>;
}
export async function requestFile(path: string, init?: RequestInit): Promise<Blob> {
  return (await checkedResponse(path, init)).blob();
}
export async function listEquipment(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  return request<{
    data: EquipmentRecord[];
    page: { number: number; size: number; total: number };
  }>(`/equipment?${search}`);
}
export async function saveEquipment(form: EquipmentRecord, isNew: boolean, detailsOnly = false) {
  const body = {
    customFields: form.customFields,
    customSchemaVersion: form.customSchemaVersion,
    code: form.code?.trim() || '',
    name: form.name,
    category: form.category,
    categoryId: form.categoryId || null,
    unitId: form.unitId || null,
    useStateId: form.useStateId || null,
    sourceId: form.sourceId || null,
    ownerId: form.ownerId || null,
    ownerIds: form.ownerIds ?? (form.ownerId ? [form.ownerId] : []),
    model: form.model,
    serial: form.serial,
    company: form.company,
    departmentOrgId: form.departmentOrgId || null,
    department: form.department,
    owner: form.owner,
    brand: form.brand,
    state: form.state,
    useState: form.useState,
    source: form.source,
    acquiredAt: form.acquiredAt || null,
    amount: form.amount || null,
    warranty: form.warranty || null,
    remark: form.remark,
    assetNo: form.assetNo,
    tagCode: form.tagCode,
    unit: form.unit,
    supplier: form.supplier,
    purchaseDate: form.purchaseDate || null,
    inServiceDate: form.inServiceDate || null,
    retirementDate: form.retirementDate || null,
    level: form.level,
    functionLocation: form.functionLocation,
    locationId: form.locationId || null,
    specialEquipment: form.specialEquipment,
    metrologyEquipment: form.metrologyEquipment,
    depreciationEnabled: form.depreciationEnabled,
    technicalParams: form.technicalParams,
    imageName: form.imageName,
    imageFileId: form.imageFileId || '',
    images: form.images,
    calibrationDate: form.calibrationDate || null,
    calibrationValidUntil: form.calibrationValidUntil || null,
    inspectorName: form.inspectorName,
    documents: form.documents || [],
    children: form.children || [],
    relatedSpares: form.relatedSpares || [],
  };
  return request<{ data: EquipmentRecord }>(
    isNew
      ? '/equipment'
      : '/equipment/' +
          form.id +
          (detailsOnly ? '/details' : '') + '?rowVersion=' + requiredRowVersion(form.rowVersion),
    { method: isNew ? 'POST' : 'PUT', body: JSON.stringify(body) },
  );
}
export async function saveEquipmentRelations(
  id: string,
  kind: 'children' | 'bom',
  rowVersion: number | undefined,
  rows: EquipmentRecord['children'] | EquipmentRecord['relatedSpares'],
) {
  return request<{ data: EquipmentRecord }>(
    `/equipment/${id}/${kind}?rowVersion=${requiredRowVersion(rowVersion)}`,
    { method: 'PUT', body: JSON.stringify({ rows }) },
  );
}
export async function deleteEquipment(id: string) {
  return request('/equipment/' + id, { method: 'DELETE' });
}
export async function listRepairs(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  return request<{
    data: RepairRecord[];
    page: { number: number; size: number; total: number };
    counts: Record<
      'pending' | 'owned' | 'mine' | 'accept' | 'created' | 'dispatch' | 'all',
      number
    >;
  }>(`/repairs?${search}`);
}
export async function saveRepair(form: RepairRecord, isNew: boolean) {
  const body = {
    customFields: form.customFields,
    customSchemaVersion: form.customSchemaVersion,
    equipmentCode: form.equipmentCode,
    equipmentName: form.equipmentName,
    department: form.department,
    faultTypeId: form.faultTypeId || null,
    severity: form.severity,
    description: form.description,
    reporter: form.reporter,
    assignee: form.assignee,
    attachments: form.attachments || [],
  };
  return request<{ data: RepairRecord }>(
    isNew
      ? '/repairs'
      : '/repairs/' + form.id + '?rowVersion=' + requiredRowVersion(form.rowVersion),
    {
      method: isNew ? 'POST' : 'PUT',
      body: JSON.stringify(body),
    },
  );
}
export async function transitionRepair(
  id: string,
  action: string,
  assignee: string,
  repairAction: string,
  downtimeMinutes: number,
  rowVersion: number | undefined,
) {
  return request<{ data: RepairRecord }>(
    `/repairs/${id}/actions?rowVersion=${requiredRowVersion(rowVersion)}`,
    {
      method: 'POST',
      body: JSON.stringify({ action, assignee, repairAction, downtimeMinutes }),
    },
  );
}
export async function uploadRepairFile(file: File) {
  const form = new FormData();
  form.append('file', file);
  return request<{ data: RepairAttachment }>('/repairs/attachments', {
    method: 'POST',
    body: form,
  });
}
const basePaths: Record<string, string> = {
  设备分类: 'categories',
  物料分类: 'material-categories',
  存放地点: 'locations',
  单位: 'units',
  使用状态: 'use-statuses',
  设备来源: 'sources',
  故障类型: 'fault-types',
  故障原因: 'fault-causes',
  处理结果: 'processing-results',
};
export async function listBase(tab: string, companyId?: string) {
  return request<{ data: BaseRecord[] }>(
    `/base-data/${basePaths[tab]}${companyId ? `?companyId=${encodeURIComponent(companyId)}` : ''}`,
  );
}
export async function saveBase(tab: string, item: Partial<BaseRecord>, isNew: boolean) {
  const resource = basePaths[tab],
    id = item.id;
  const body = {
    companyId: item.companyId || null,
    code: item.code,
    name: item.name,
    parentId: item.parentId || null,
    precision: item.precision || 0,
    status: item.status === '停用' ? 0 : 1,
    sortNo: item.sortNo || 0,
  };
  return request<{ data: BaseRecord }>(
    isNew ? `/base-data/${resource}` : `/base-data/${resource}/${id}`,
    { method: isNew ? 'POST' : 'PUT', body: JSON.stringify(body) },
  );
}
export async function changeBaseStatus(tab: string, item: { id: string }, status: string) {
  return request(`/base-data/${basePaths[tab]}/${item.id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: status === '启用' ? 1 : 0 }),
  });
}
export async function deleteBase(tab: string, id: string) {
  return request('/base-data/' + basePaths[tab] + '/' + id, { method: 'DELETE' });
}
export async function listOrganizations() {
  return request<{ data: OrganizationRecord[] }>('/system/organizations');
}
export async function saveOrganization(item: Partial<OrganizationRecord>, isNew: boolean) {
  const body = {
    name: item.name,
    code: item.code,
    type: item.type,
    parentId: item.parentId || null,
    manager: item.manager,
    status: item.status === '停用' ? 0 : 1,
  };
  return request<{ data: OrganizationRecord }>(
    isNew ? '/system/organizations' : '/system/organizations/' + item.id,
    { method: isNew ? 'POST' : 'PUT', body: JSON.stringify(body) },
  );
}
export async function deleteOrganization(id: string) {
  return request('/system/organizations/' + id, { method: 'DELETE' });
}
export type UserListParameters = {
  page: number;
  size: number;
  q: string;
  conditions: string;
  filters: string;
  organization: string;
};
export async function querySystemUsers(parameters: UserListParameters) {
  return request<{
    data: SystemUserRecord[];
    page: { number: number; size: number; total: number };
  }>(
    '/system/users?' +
      new URLSearchParams(Object.entries(parameters).map(([key, value]) => [key, String(value)])),
  );
}
export const systemUserSummary = () =>
  request<{ roleCounts: Record<string, number> }>('/system/users/summary');
export const exportSystemUsers = (parameters: UserListParameters) =>
  request<{ data: SystemUserRecord[] }>(
    '/system/users/export?' +
      new URLSearchParams(Object.entries(parameters).map(([key, value]) => [key, String(value)])),
  );
export async function saveSystemUser(item: Partial<SystemUserRecord>, isNew: boolean) {
  const body = {
    account: item.account,
    name: item.name,
    employeeNo: item.employeeNo,
    phone: item.phone,
    email: item.email,
    emailVersion: item.emailVersion,
    orgId: item.orgId,
    roleIds: item.roleIds || [],
    status: item.status === '停用' ? 0 : 1,
    password: item.password || '',
  };
  return request<{ data: SystemUserRecord }>(isNew ? '/system/users' : '/system/users/' + item.id, {
    method: isNew ? 'POST' : 'PUT',
    body: JSON.stringify(body),
  });
}
export async function deleteSystemUser(id: string) {
  return request('/system/users/' + id, { method: 'DELETE' });
}
export async function batchSystemUsers(
  ids: string[],
  operation: 'department' | 'password',
  values: { orgId?: string; password?: string },
) {
  return request<{ data: SystemUserRecord[] }>('/system/users/batch', {
    method: 'POST',
    body: JSON.stringify({ ids, operation, ...values }),
  });
}
export async function setSystemUserStatus(id: string, status: '启用' | '停用') {
  return request<{ data: SystemUserRecord }>(`/system/users/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: status === '启用' ? 1 : 0 }),
  });
}
export async function listSystemRoles() {
  return request<{ data: SystemRoleRecord[] }>('/system/roles');
}
export async function saveSystemRole(item: Partial<SystemRoleRecord>, isNew: boolean) {
  const body = {
    code: item.code,
    name: item.name,
    description: item.description,
    status: item.status === '停用' ? 0 : 1,
    menuPermissions: item.menuPermissions || {},
  };
  return request<{ data: SystemRoleRecord }>(isNew ? '/system/roles' : '/system/roles/' + item.id, {
    method: isNew ? 'POST' : 'PUT',
    body: JSON.stringify(body),
  });
}
export async function deleteSystemRole(id: string) {
  return request('/system/roles/' + id, { method: 'DELETE' });
}
export async function login(tenantId: string, account: string, password: string) {
  return request<{ data: CurrentUser }>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ tenantId, account, password }),
  });
}
export async function me() {
  return request<{
    data: CurrentUser;
    permissions: Record<string, string[]>;
    developmentMode?: boolean;
  }>('/auth/me');
}
export async function logout() {
  return request('/auth/logout', { method: 'POST' });
}

export type HistoryRow = {
  number: string;
  time: string;
  description: string;
  person: string;
  status: string;
  result: string;
};
export type EquipmentHistory = {
  data: EquipmentRecord;
  repairs: HistoryRow[];
  maintenance: HistoryRow[];
  inspections: HistoryRow[];
  spareChanges: HistoryRow[];
  installedSpares: InstalledSpare[];
};
export function equipmentHistory(id: string) {
  return request<EquipmentHistory>(`/equipment/${id}/history`);
}
export function batchEquipment(
  ids: string[],
  operation: string,
  value: string,
  ownerIds?: string[],
) {
  return request<{ data: { count: number } }>('/equipment/batch', {
    method: 'POST',
    body: JSON.stringify({ ids, operation, value, ownerIds }),
  });
}
export function equipmentOwnerCandidates(
  q: string,
  page: number,
  size: number,
  action: '新增' | '编辑',
) {
  return request<{
    data: { id: string; name: string; account: string }[];
    page: { number: number; size: number; total: number };
  }>(
    '/equipment/owner-candidates?' +
      new URLSearchParams({ q, page: String(page), size: String(size), action }),
  );
}
export function equipmentBatchOptions(action: '新增' | '编辑' = '编辑') {
  return request<{ departments: OrganizationRecord[]; users: SystemUserRecord[] }>(
    '/equipment/batch-options?action=' + encodeURIComponent(action),
  );
}

export type LabelElement = {
  id: string;
  type: string;
  x: number;
  y: number;
  width: number;
  height: number;
  fontSize: number;
  prefix: string;
  bold: boolean;
  text: string;
  image: string;
  align: 'left' | 'center' | 'right';
};
export type LabelTemplate = {
  id: string;
  name: string;
  widthMm: number;
  heightMm: number;
  remark: string;
  elements: LabelElement[];
  layout?: import('./printing/grid').GridLayout | null;
  rowVersion: number;
  updatedAt: string;
};
export function listLabelTemplates() {
  return request<{ data: LabelTemplate[] }>('/label-templates');
}
export function saveLabelTemplate(template: LabelTemplate) {
  return request<{ data: LabelTemplate }>(
    template.id ? '/label-templates/' + template.id : '/label-templates',
    { method: template.id ? 'PUT' : 'POST', body: JSON.stringify(template) },
  );
}
export function deleteLabelTemplate(id: string) {
  return request('/label-templates/' + id, { method: 'DELETE' });
}
export function labelPrintData(id: string, equipmentIds: string[]) {
  return request<{ template: LabelTemplate; equipment: Record<string, string>[] }>(
    '/label-templates/' + id + '/print-data',
    { method: 'POST', body: JSON.stringify({ equipmentIds }) },
  );
}

export async function labelPdf(id: string, equipmentIds: string[], copies: number) {
  const r = await fetch(root + '/label-templates/' + id + '/pdf', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value, 'Content-Type': 'application/json' },
    body: JSON.stringify({ equipmentIds, copies }),
  });
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    if (r.status === 401) window.dispatchEvent(new Event('eam-session-expired'));
    throw new ApiError(e.message || tr('ui.validation.message1'), r.status);
  }
  return r.blob();
}
export async function labelPreview(template: LabelTemplate) {
  const r = await fetch(root + '/label-templates/preview', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value, 'Content-Type': 'application/json' },
    body: JSON.stringify(template),
  });
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    if (r.status === 401) window.dispatchEvent(new Event('eam-session-expired'));
    throw new ApiError(e.message || tr('ui.validation.message2'), r.status);
  }
  return r.blob();
}

function requiredRowVersion(version: number | undefined): number {
  if (version === undefined || !Number.isInteger(version) || version < 0) {
    throw new Error(tr('ui.equipment.text0065'));
  }
  return version;
}

export async function exportRepairs(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  return requestFile('/repairs/export?' + search);
}

export type DispatchUser = {
  id: string;
  name: string;
  account: string;
  department: string;
  unfinished: number;
};
export type MaterialRow = {
  id: string;
  stockId: string;
  name: string;
  code: string;
  model: string;
  quantity: number;
  issuedQuantity: number;
  installedQuantity: number;
  status: string;
};
export type InstalledSpare = {
  id: string;
  code: string;
  name: string;
  model: string;
  quantity: number;
  status: string;
  installedAt: string;
};
export type StockRow = { id: string; code: string; name: string; model: string; quantity: number };
export type WorkflowEvent = {
  id: string;
  action: string;
  actor: string;
  time: string;
  details: Record<string, unknown>;
};
export type WorkflowDetail = {
  categoryId?: string;
  order: RepairRecord;
  model: string;
  candidates: { id: string; name: string; status: string }[];
  events: WorkflowEvent[];
  materials: MaterialRow[];
  installed: InstalledSpare[];
  processingFields?: import('./businessConfig').CustomRecord;
  canClaim: boolean;
  canProcess: boolean;
  canAccept: boolean;
  canDispatch: boolean;
  canIssue: boolean;
  results: { id: string; name: string; resolved: boolean }[];
};
export type InboxItem = {
  planName?: string;
  shiftName?: string;
  owner?: string;
  due?: string;
  module?: string;
  id: string;
  orderId: string;
  orderNo: string;
  equipmentName: string;
  status: string;
  title: string;
  kind: string;
  todo: number;
  done: number;
  isRead: number;
  time: string;
};
export function repairUserChoices() {
  return request<{ data: DispatchUser[] }>('/repairs/user-choices');
}
export function repairWorkflow(id: string) {
  return request<{ data: WorkflowDetail }>(`/repairs/${id}/workflow`);
}
export function repairWorkflowAction(
  id: string,
  version: number | undefined,
  body: Record<string, unknown>,
) {
  return request<{ data: RepairRecord }>(
    `/repairs/${id}/workflow?rowVersion=${requiredRowVersion(version)}`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}
export function repairInbox() {
  return request<{ data: InboxItem[] }>('/repairs/inbox');
}
export function readRepairInbox(id: string) {
  return request(`/repairs/inbox/${id}/read`, { method: 'PATCH' });
}
export function repairStocks(id: string) {
  return request<{ data: StockRow[] }>(`/repairs/${id}/stocks`);
}
export function listSpareStocks() {
  return request<{ data: StockRow[] }>('/repairs/stocks');
}
export function createSpareStock(body: Record<string, unknown>) {
  return request('/repairs/stocks', { method: 'POST', body: JSON.stringify(body) });
}

export function maintenanceInbox() {
  return request<{ data: InboxItem[] }>('/maintenance/inbox');
}
export function readMaintenanceInbox(id: string) {
  return request('/maintenance/inbox/' + id + '/read', { method: 'PATCH' });
}

export type InboxSummary = {
  counts: { pending: number; completed: number; unread: number };
  kinds: { module: string; kind: string; count: number; completed: number }[];
};
export function workInboxSummary() {
  return request<InboxSummary>('/work-inbox/summary');
}
export function workInbox(params: Record<string, string | number>) {
  const query = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]));
  return request<{ data: InboxItem[]; total: number }>('/work-inbox?' + query);
}
export function readWorkInbox(row?: Pick<InboxItem, 'id' | 'module'>) {
  return request('/work-inbox/read', {
    method: 'PATCH',
    body: JSON.stringify(row ? { id: row.id, module: row.module } : {}),
  });
}
export function changeOwnPassword(currentPassword: string, newPassword: string) {
  return request('/auth/password', {
    method: 'POST',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

export function listLocationCompanies() {
  return request<{ data: OrganizationRecord[] }>('/base-data/location-companies');
}

export async function uploadEquipmentFile(file: File): Promise<RepairAttachment> {
  const data = new FormData();
  data.append('file', file);
  return (
    await request<{ data: RepairAttachment }>('/equipment/files', { method: 'POST', body: data })
  ).data;
}

export function equipmentChildCandidates(
  parentId: string,
  departmentOrgId: string,
  q: string,
  page: number,
  size: number,
) {
  const params = new URLSearchParams({ q, page: String(page), size: String(size) });
  if (parentId) params.set('parentId', parentId);
  if (departmentOrgId) params.set('departmentOrgId', departmentOrgId);
  return request<{ data: EquipmentRecord[]; page: { total: number } }>(
    '/equipment/child-candidates?' + params,
  );
}
export function equipmentBomMaterials(q: string, page: number, size: number) {
  return request<{ data: import('./inventory').InventoryMaster[]; page: { total: number } }>(
    '/equipment/bom-materials?' +
      new URLSearchParams({ q, page: String(page), size: String(size) }),
  );
}

export type PersonalSettings = { locale: string; effectiveLocale: string; theme: import('./stores/ui').Theme; roles: string[] };
export function getPersonalSettings() {
  return request<{ data: PersonalSettings }>('/auth/settings');
}
export function savePersonalTheme(theme: import('./stores/ui').Theme) {
  return request<{ data: Pick<PersonalSettings, 'theme'> }>('/auth/settings', {
    method: 'PUT',
    body: JSON.stringify({ theme }),
  });
}

export const getEquipment = (id: string) => request<{ data: EquipmentRecord }>(`/equipment/${id}`);
export async function exportEquipment(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  return requestFile('/equipment/export?' + search);
}
