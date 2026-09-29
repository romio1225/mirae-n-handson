/**
 * modern/api(Spring, 8080) 응답 모양 — com.example.item.UnitResponse · ItemResponse 와 1:1.
 * 서버 DTO가 바뀌면 이 파일과 src/api/items.ts 만 고친다.
 */

/** 난이도 1~5 */
export type Level = 1 | 2 | 3 | 4 | 5;

/** A=공개 D=삭제 R=검수중 */
export type ItemStatus = 'A' | 'D' | 'R';

/** GET /api/units 의 행 */
export interface Unit {
  id: number;
  code: string;
  name: string;
  grade: number;
}

/**
 * GET /api/units/{code}/items 의 행이자 GET /api/items/{id} 의 응답.
 * unit 은 단원 코드(M5-1), tags 는 태그 이름 목록(서버 순서 유지).
 */
export interface Item {
  id: number;
  title: string;
  stem: string;
  unit: string;
  level: Level;
  status: ItemStatus;
  tags: string[];
}

/**
 * GET /api/grades/report?class_id= 의 단원 행 — com.example.grade.UnitReportResponse 의 JSON 필드명(@JsonProperty)과 같다.
 * 점수는 레거시와 같은 자릿수를 살린 문자열(평균 둘째 자리, 최고 · 최저 첫째 자리)이고 값이 없으면 빈 문자열이다.
 */
export interface UnitReport {
  unit: string;
  unit_name: string;
  enrolled: number;
  submitted: number;
  missing: number;
  late: number;
  excluded: number;
  avg_score: string;
  max_score: string;
  min_score: string;
}

/** GET /api/grades/report 의 응답 — com.example.grade.GradeReportResponse 와 1:1. 없는 학급이면 items 가 빈 배열. */
export interface GradeReport {
  items: UnitReport[];
  count: number;
  message: string | null;
}
