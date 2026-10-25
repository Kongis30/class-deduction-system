import { ElMessage, ElMessageBox } from 'element-plus';
import { h } from 'vue';

import { extractErrorMessage } from '@/api/http';

/** 让排课冲突以可读明细对话框呈现，避免多项冲突被短暂 toast 挤成一行。 */
export async function showScheduleConflictFeedback(error: unknown): Promise<boolean> {
  if (!error || typeof error !== 'object' || !('response' in error)) return false;
  const response = (error as { response?: { data?: { code?: unknown } } }).response;
  if (response?.data?.code !== 'SCHEDULE_CONFLICT') return false;

  const lines = extractErrorMessage(error).split('\n').filter(Boolean);
  try {
    await ElMessageBox.alert(
      h('div', { style: { display: 'grid', gap: '8px' } }, lines.map((line, index) =>
        h('p', { key: index, style: { margin: '0', lineHeight: '1.5' } }, line))),
      'Schedule conflict',
      { type: 'warning', confirmButtonText: 'OK', customClass: 'schedule-conflict-feedback-box' },
    );
  } catch {
    // Alert dismissal is not an application error.
  }
  return true;
}

/** 非排课冲突维持原有的短错误提示。 */
export function showScheduleError(error: unknown, fallback = 'Operation failed.'): void {
  void showScheduleConflictFeedback(error).then((handled) => {
    if (!handled) ElMessage.error(extractErrorMessage(error) || fallback);
  });
}
