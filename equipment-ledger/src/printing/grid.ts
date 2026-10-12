import type { LabelTemplate } from '../api';
export type GridCell = {
  row: number;
  col: number;
  rowSpan: number;
  colSpan: number;
  content: string;
  kind: 'text' | 'image' | 'qrcode' | 'barcode';
  image: string;
  fontSize: number;
  bold: boolean;
  italic: boolean;
  underline: boolean;
  align: 'left' | 'center' | 'right';
  vertical: 'top' | 'middle' | 'bottom';
  color: string;
  background: string;
  border: boolean;
  borderSides?: { top: boolean; right: boolean; bottom: boolean; left: boolean };
  borderColor?: string;
  borderWidth?: number;
  overflow: 'wrap' | 'shrink' | 'clip';
};
export type GridLayout = {
  version: 1;
  columns: number[];
  rows: number[];
  cells: GridCell[];
  margin: { top: number; right: number; bottom: number; left: number };
  repeatHeader: number;
  pageNumbers: boolean;
  repeat?: { startRow: number; endRow: number; source: 'repairs' } | null;
  area: { startRow: number; endRow: number; startCol: number; endCol: number } | null;
};
export const fields = [
  { key: 'name', label: '设备名称' },
  { key: 'code', label: '设备编号' },
  { key: 'model', label: '规格型号' },
  { key: 'purchaseDate', label: '购置日期' },
  { key: 'owner', label: '负责人' },
  { key: 'category', label: '类别' },
  { key: 'department', label: '部门' },
  { key: 'equipment_qrcode', label: '二维码' },
  { key: 'equipment_barcode', label: '条形码' },
  { key: 'fixed_text', label: '固定文本' },
  { key: 'brand', label: '品牌' },
  { key: 'functionLocation', label: '功能位置' },
  { key: 'assetNo', label: '资产编号' },
  { key: 'serial', label: '序列号' },
  { key: 'unit', label: '单位' },
  { key: 'supplier', label: '供应商' },
  { key: 'calibrationDate', label: '计量日期' },
  { key: 'level', label: '设备等级' },
  { key: 'fixed_image', label: '图片' },
  { key: 'calibrationValidUntil', label: '计量有效期' },
  { key: 'inspectorName', label: '检验员' },
  { key: 'inServiceDate', label: '启用日期' },
];
export const sample: Record<string, string> = {
  name: '数控车床',
  code: 'EQ-2026-0001',
  owner: '张建国',
  department: '机加工一部',
  model: 'CK6150',
  company: '苏州风塔精密制造有限公司',
  category: '机床设备',
  state: '正常',
  serial: 'CK6150-2024-031',
  purchaseDate: '2024-01-15',
  brand: '沈阳机床',
  functionLocation: 'A车间',
  assetNo: 'ZC-0001',
  unit: '台',
  supplier: '设备供应商',
  level: 'A级',
  calibrationDate: '2026-10-10',
  calibrationValidUntil: '2027-10-10',
  inspectorName: '李明',
  inServiceDate: '2024-02-01',
  repair_orderNo: 'WX-2026-0001',
  repair_faultAt: '2026-10-10 09:00',
  repair_description: '主轴运行时有异响',
  repair_assignee: '王师傅',
  repair_status: '已完成',
};
export const cellKey = (row: number, col: number) => `${row}:${col}`;
export function emptyCell(row: number, col: number): GridCell {
  return {
    row,
    col,
    rowSpan: 1,
    colSpan: 1,
    content: '',
    kind: 'text',
    image: '',
    fontSize: 10,
    bold: false,
    italic: false,
    underline: false,
    align: 'left',
    vertical: 'middle',
    color: '#000000',
    background: '#ffffff',
    border: false,
    overflow: 'wrap',
  };
}
export function defaultLayout(): GridLayout {
  return {
    version: 1,
    columns: Array(8).fill(10),
    rows: Array(10).fill(5),
    cells: [
      {
        ...emptyCell(0, 0),
        rowSpan: 2,
        colSpan: 8,
        content: '{name}',
        fontSize: 16,
        bold: true,
        align: 'center',
      },
      { ...emptyCell(3, 0), colSpan: 8, content: '设备编码：{code}' },
      { ...emptyCell(5, 0), colSpan: 8, content: '所属部门：{department}' },
      { ...emptyCell(7, 0), colSpan: 8, content: '负责人：{owner}' },
    ],
    margin: { top: 0, right: 0, bottom: 0, left: 0 },
    repeatHeader: 0,
    pageNumbers: false,
    area: null,
  };
}
export function getCell(g: GridLayout, row: number, col: number) {
  return g.cells.find(
    (c) => row >= c.row && row < c.row + c.rowSpan && col >= c.col && col < c.col + c.colSpan,
  );
}
export function ensureCell(g: GridLayout, row: number, col: number) {
  let c = getCell(g, row, col);
  if (!c) {
    c = emptyCell(row, col);
    g.cells.push(c);
  }
  return c;
}
export function columnName(n: number) {
  let s = '';
  for (n++; n > 0; n = Math.floor((n - 1) / 26)) s = String.fromCharCode(65 + ((n - 1) % 26)) + s;
  return s;
}
export function resolve(content: string, data: Record<string, string> = sample) {
  return content.replace(/\{([a-zA-Z0-9_]+)\}/g, (_, key) => data[key] ?? '');
}
export function legacyToGrid(t: LabelTemplate): GridLayout {
  const edges = (max: number, key: 'x' | 'y', size: 'width' | 'height') =>
    [
      ...new Set(
        [0, max, ...t.elements.flatMap((e) => [e[key], Math.min(max, e[key] + e[size])])].map((v) =>
          Number(v.toFixed(2)),
        ),
      ),
    ].sort((a, b) => a - b);
  const xs = edges(t.widthMm, 'x', 'width'),
    ys = edges(t.heightMm, 'y', 'height');
  const g: GridLayout = {
    ...defaultLayout(),
    columns: xs.slice(1).map((v, i) => v - xs[i]!),
    rows: ys.slice(1).map((v, i) => v - ys[i]!),
    cells: [],
  };
  for (const e of t.elements) {
    const row = ys.indexOf(Number(e.y.toFixed(2))),
      col = xs.indexOf(Number(e.x.toFixed(2))),
      er = ys.indexOf(Number(Math.min(t.heightMm, e.y + e.height).toFixed(2))),
      ec = xs.indexOf(Number(Math.min(t.widthMm, e.x + e.width).toFixed(2)));
    if (row < 0 || col < 0 || er <= row || ec <= col)
      throw new Error('旧模板存在无效坐标，请保留原模板并新建表格模板');
    for (let r = row; r < er; r++)
      for (let c = col; c < ec; c++)
        if (getCell(g, r, c))
          throw new Error('旧模板含重叠内容，不能直接转换；请保留原模板并新建表格模板');
    g.cells.push({
      ...emptyCell(row, col),
      rowSpan: er - row,
      colSpan: ec - col,
      kind: e.type === 'image' ? 'image' : 'text',
      image: e.image,
      content: e.prefix + (e.type === 'text' ? e.text : e.type === 'image' ? '' : `{${e.type}}`),
      fontSize: e.fontSize,
      bold: e.bold,
      align: e.align,
    });
  }
  return g;
}
