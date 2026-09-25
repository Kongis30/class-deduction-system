/**
 * 周视图统一按业务时区的周一至周日查询。
 * 返回下周一作为独占结束日期，避免周日最后一秒被遗漏。
 */
export function calendarWeekRange(now: Date, timeZone: string) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const datePart = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find(part => part.type === type)?.value);
  const date = new Date(Date.UTC(datePart('year'), datePart('month') - 1, datePart('day')));
  const daysSinceMonday = (date.getUTCDay() + 6) % 7;
  const monday = new Date(date);
  monday.setUTCDate(date.getUTCDate() - daysSinceMonday);
  const nextMonday = new Date(monday);
  nextMonday.setUTCDate(monday.getUTCDate() + 7);
  return {
    startDate: monday.toISOString().slice(0, 10),
    endExclusiveDate: nextMonday.toISOString().slice(0, 10),
  };
}
