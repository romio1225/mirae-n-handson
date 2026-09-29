// 동작 보존 테스트 — 성적 집계(grade)의 학급 단원별 현황 화면 /report
//
// - 대상 주소는 lib/target.mjs 가 정한다(TARGET_BASE_URL, 없으면 레거시 기본 주소). 주소를 여기 적지 않는다.
// - 기대값을 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- grade).
// - 케이스 이름 앞의 [BR-xx] 는 docs/grade/BUSINESS-RULES.md 의 규칙 ID 다.
// - /report 는 보고 시각(submission.reported_at, grade_summary.last_report_at)을 DB 에 쓰지만(BR-23)
//   응답에는 그 값이 나오지 않으므로 스냅샷은 흔들리지 않는다.
// - /aggregate 는 grade_summary 를 UPSERT · DELETE 하는 쓰기 동작이라 이 파일에서 다루지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'grade';
const PATH = '/report';

const report = (params) => fetchNormalized(MODULE, PATH, params);

describe('grade · 학급 단원별 현황(/report)', () => {
  // ---------------------------------------------------------------- 정상 입력
  it('정상 — 학급 C1 단원별 현황 [BR-18 · BR-19 · BR-20 · BR-21 · BR-22]', async () => {
    expect(await report({ class_id: 'C1' })).toMatchSnapshot();
  });

  it('정상 — 학급 C2 [BR-18 · BR-21]', async () => {
    expect(await report({ class_id: 'C2' })).toMatchSnapshot();
  });

  it('정상 — 학급 C3 [BR-18 · BR-21]', async () => {
    expect(await report({ class_id: 'C3' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 경계값
  // C4 는 경계 사례만 모은 시드 학급이다(db/mssql/init/02-seed.sql 끝, 제출 901~910).
  it('경계 — C4: 마감+2일 정확히 · NULL 점수 · 같은 시각 재제출 · 최신 X · 단원 불일치 · 키 대소문자/공백 [BR-01 · BR-02 · BR-05 · BR-06 · BR-21]', async () => {
    expect(await report({ class_id: 'C4' })).toMatchSnapshot();
  });

  it('경계 — 앞뒤 공백 " C1 " 은 trim 되어 C1 [BR-24]', async () => {
    expect(await report({ class_id: ' C1 ' })).toMatchSnapshot();
  });

  it('경계 — 소문자 "c1" (DB 정렬 규칙에 따른 일치 여부) [BR-24]', async () => {
    expect(await report({ class_id: 'c1' })).toMatchSnapshot();
  });

  it('경계 — 11자 "C1" + 공백 8 + "X": 프로시저 인자 VARCHAR(10) 에서 잘리는지 [BR-24]', async () => {
    expect(await report({ class_id: `C1${' '.repeat(8)}X` })).toMatchSnapshot();
  });

  it('경계 — 11자 "C1XXXXXXXXX" (10자로 잘려도 없는 학급) [BR-24]', async () => {
    expect(await report({ class_id: 'C1XXXXXXXXX' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 빈 값 · 누락
  it('누락 — class_id 없음은 기본 학급 C1 [BR-24]', async () => {
    expect(await report({})).toMatchSnapshot();
  });

  it('빈값 — class_id= 은 기본 학급 C1 [BR-24]', async () => {
    expect(await report({ class_id: '' })).toMatchSnapshot();
  });

  it('빈값 — 공백만 있는 class_id 는 기본 학급 C1 [BR-24]', async () => {
    expect(await report({ class_id: '   ' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 이상한 값
  it('이상값 — 없는 학급 C9 — 재적 0명이면 빈 결과 [BR-18]', async () => {
    expect(await report({ class_id: 'C9' })).toMatchSnapshot();
  });

  it('이상값 — 음수 모양 "-1" [BR-24]', async () => {
    expect(await report({ class_id: '-1' })).toMatchSnapshot();
  });

  it('이상값 — 아주 긴 값 1000자 [BR-24]', async () => {
    expect(await report({ class_id: 'C'.repeat(1000) })).toMatchSnapshot();
  });

  it('이상값 — 따옴표가 든 값은 문자열 그대로 비교 [BR-24]', async () => {
    expect(await report({ class_id: "C1' OR '1'='1" })).toMatchSnapshot();
  });

  it('이상값 — class_id 를 두 번 보냄 (C1, C2) [BR-24]', async () => {
    expect(await report({ class_id: ['C1', 'C2'] })).toMatchSnapshot();
  });
});
