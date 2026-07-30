import dayjs from 'dayjs';

import type { LessonOccurrenceItem } from '@/types/domain';

export interface ScheduleConflictGroup {
  key: string;
  startAt: string;
  endAt: string;
  timeLabel: string;
  items: LessonOccurrenceItem[];
}

/**
 * Finds continuous time ranges covered by two or more active lessons.
 * Boundaries come from actual lesson times, so off-grid starts such as 17:15
 * are handled without rounding.
 */
export function findScheduleConflictGroups(lessons: LessonOccurrenceItem[]): ScheduleConflictGroup[] {
  const activeLessons = lessons.filter(lesson => lesson.planningStatus !== 'CANCELLED');
  const boundaries = [...new Set(activeLessons.flatMap(lesson => [
    dayjs(lesson.plannedStartAt).valueOf(),
    dayjs(lesson.plannedEndAt).valueOf(),
  ]))].sort((a, b) => a - b);
  const groups: ScheduleConflictGroup[] = [];

  for (let index = 0; index < boundaries.length - 1; index++) {
    const startMs = boundaries[index];
    const endMs = boundaries[index + 1];
    const overlapping = activeLessons
      .filter(lesson =>
        dayjs(lesson.plannedStartAt).valueOf() < endMs
        && dayjs(lesson.plannedEndAt).valueOf() > startMs
      )
      .sort((a, b) => String(a.id).localeCompare(String(b.id)));
    if (overlapping.length < 2) continue;

    const occurrenceKey = overlapping.map(lesson => String(lesson.id)).join('|');
    const previous = groups[groups.length - 1];
    const previousKey = previous?.items.map(lesson => String(lesson.id)).join('|');
    if (previous && dayjs(previous.endAt).valueOf() === startMs && previousKey === occurrenceKey) {
      previous.endAt = dayjs(endMs).format('YYYY-MM-DDTHH:mm:ss');
      previous.timeLabel = formatConflictTime(previous.startAt, previous.endAt);
      continue;
    }

    const startAt = dayjs(startMs).format('YYYY-MM-DDTHH:mm:ss');
    const endAt = dayjs(endMs).format('YYYY-MM-DDTHH:mm:ss');
    groups.push({
      key: `${startAt}:${occurrenceKey}`,
      startAt,
      endAt,
      timeLabel: formatConflictTime(startAt, endAt),
      items: overlapping,
    });
  }
  return groups;
}

function formatConflictTime(startAt: string, endAt: string) {
  return `${dayjs(startAt).format('ddd MMM D HH:mm')}–${dayjs(endAt).format('HH:mm')}`;
}
