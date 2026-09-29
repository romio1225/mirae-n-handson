import { fetchGradeReport } from '../api/grades';
import type { GradeReport } from '../api/types';
import { useApiQuery } from './useApiQuery';
import type { QueryState } from './useApiQuery';

/** 학급 단원별 성적 현황. 학급이 바뀌면 이전 요청을 취소하고 다시 조회한다. */
export function useGradeReport(classId: string): QueryState<GradeReport> {
  return useApiQuery(`grade-report:${classId}`, (signal) => fetchGradeReport(classId, signal));
}
