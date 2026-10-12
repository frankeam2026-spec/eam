/** Local calendar ranges; never convert business dates through UTC. */
function dayRange(start: Date, end: Date): [Date, Date] {
  start.setHours(0, 0, 0, 0);
  end.setHours(23, 59, 59, 999);
  return [start, end];
}
function recent(days: number): [Date, Date] {
  const end = new Date();
  const start = new Date(end);
  start.setDate(start.getDate() - days + 1);
  return dayRange(start, end);
}
export const dateRangeShortcuts = [
  { text: '今天', value: () => recent(1) },
  {
    text: '昨天',
    value: () => {
      const date = new Date();
      date.setDate(date.getDate() - 1);
      return dayRange(new Date(date), date);
    },
  },
  ...[7, 14, 30].map((days) => ({ text: `最近${days}天`, value: () => recent(days) })),
  {
    text: '上个月',
    value: () => {
      const now = new Date();
      return dayRange(
        new Date(now.getFullYear(), now.getMonth() - 1, 1),
        new Date(now.getFullYear(), now.getMonth(), 0),
      );
    },
  },
  {
    text: '本月',
    value: () => {
      const now = new Date();
      return dayRange(new Date(now.getFullYear(), now.getMonth(), 1), now);
    },
  },
];
