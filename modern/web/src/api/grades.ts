import { getJson } from './client';
import type { GradeReport } from './types';

/** GET /api/grades/report?class_id= — 학급 단원별 성적 현황(레거시 usp_class_report 이관) */
export function fetchGradeReport(classId: string, signal?: AbortSignal): Promise<GradeReport> {
  return getJson<GradeReport>(`/api/grades/report?class_id=${encodeURIComponent(classId)}`, signal);
}
