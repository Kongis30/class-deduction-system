/** 所有周排课日历共用的显示范围；24:00 是结束边界，不是可选开课时间。 */
export const SCHEDULE_DAY_START_MINUTES = 6 * 60 + 30;
export const SCHEDULE_DAY_END_MINUTES = 24 * 60;
export const SCHEDULE_SLOT_MINUTES = 30;
export const SCHEDULE_DAY_START_TIME = '06:30';
export const SCHEDULE_LAST_SLOT_START_TIME = '23:30';

/** 将周排课中的次日 00:00 边界统一显示为当天 24:00。 */
export function scheduleEndTimeForDisplay(startTime: string, endTime: string): string {
  const start = normalizeTime(startTime);
  const end = normalizeTime(endTime);
  return end === '00:00' && start !== '00:00' ? '24:00' : end;
}

/** LocalTime 接口无法解析 24:00，提交时用 00:00 表示当天的结束边界。 */
export function scheduleEndTimeForApi(endTime: string): string {
  return normalizeTime(endTime) === '24:00' ? '00:00' : normalizeTime(endTime);
}

export function scheduleTimeMinutes(time: string): number {
  const normalized = normalizeTime(time);
  if (normalized === '24:00') return 24 * 60;
  const [hours, minutes] = normalized.split(':').map(Number);
  return hours * 60 + minutes;
}

export function scheduleDurationMinutes(startTime: string, endTime: string): number {
  const start = scheduleTimeMinutes(startTime);
  const end = scheduleTimeMinutes(scheduleEndTimeForDisplay(startTime, endTime));
  return end - start;
}

/** 课次结束于次日 00:00 时，在所属课程日历中显示为当天 24:00。 */
export function scheduleOccurrenceTimeRange(startAt: string, endAt: string): string {
  const start = String(startAt || '').trim();
  const end = String(endAt || '').trim();
  if (!start || !end) return '';

  const startDate = start.includes('T') ? start.slice(0, 10) : '';
  const endDate = end.includes('T') ? end.slice(0, 10) : '';
  const startTime = normalizeTime(start.includes('T') ? start.split('T')[1] : start);
  let endTime = normalizeTime(end.includes('T') ? end.split('T')[1] : end);
  if (endTime === '00:00' && startDate && endDate && startDate !== endDate) {
    endTime = '24:00';
  }
  return `${startTime}–${endTime}`;
}

function normalizeTime(value: string): string {
  const text = String(value || '').trim();
  const time = text.length > 5 ? text.slice(0, 5) : text;
  return time === '24:00' ? '24:00' : time;
}
