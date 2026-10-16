export type BusinessColumn<Row extends object> = {
  prop: Extract<keyof Row, string>;
  label: string;
  width?: number;
  minWidth?: number;
  fixed?: 'left' | 'right';
  align?: 'left' | 'center' | 'right';
  sortable?: boolean;
  resizable?: boolean;
  overflowTooltip?: boolean;
  formatter?: (value: unknown, row: Row) => string;
};

export type BusinessSort = {
  prop: string | null;
  order: 'ascending' | 'descending' | null;
};
