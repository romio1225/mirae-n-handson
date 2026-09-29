import { useState } from 'react';

import type { GradeReport, UnitReport } from '../api/types';
import { useGradeReport } from '../hooks/useGradeReport';
import { AsyncSection } from './AsyncSection';
import { ClassPicker } from './ClassPicker';
import type { ClassOption } from './ClassPicker';

/** 레거시 안내 화면과 같은 학급 목록(legacy/grade-mssql GradeController.java:41). */
const CLASS_OPTIONS: readonly ClassOption[] = [
  { id: 'C1', label: '5학년 1반' },
  { id: 'C2', label: '5학년 2반' },
  { id: 'C3', label: '6학년 1반' },
];
const DEFAULT_CLASS_ID = 'C1';
/** 점수 만점 — 막대 길이 계산용 */
const MAX_SCORE = 100;
const GOOD_SCORE = 80;
const FAIR_SCORE = 60;

interface ReportSummary {
  enrolled: number;
  units: number;
  late: number;
  excluded: number;
}

function summarize(report: GradeReport): ReportSummary {
  return {
    enrolled: report.items[0]?.enrolled ?? 0,
    units: report.count,
    late: report.items.reduce((sum, row) => sum + row.late, 0),
    excluded: report.items.reduce((sum, row) => sum + row.excluded, 0),
  };
}

function ratio(value: number, total: number): number {
  if (total <= 0) return 0;
  return Math.min(Math.max(value / total, 0), 1);
}

function scoreTone(score: string): string {
  const value = Number(score);
  if (score === '' || Number.isNaN(value)) return 'none';
  if (value >= GOOD_SCORE) return 'good';
  if (value >= FAIR_SCORE) return 'fair';
  return 'low';
}

/** 값이 없으면(레거시 빈 칸) 대시로 보여준다. 숫자 자릿수는 서버 문자열 그대로 쓴다. */
function displayScore(score: string): string {
  return score === '' ? '—' : score;
}

function renderRow(row: UnitReport) {
  const submitRate = ratio(row.submitted, row.enrolled);
  const avgRate = row.avg_score === '' ? 0 : ratio(Number(row.avg_score), MAX_SCORE);
  const tone = scoreTone(row.avg_score);

  return (
    <tr key={row.unit}>
      <td>
        <span className="grade-unit__code">{row.unit}</span>
        <span className="grade-unit__name">{row.unit_name}</span>
      </td>
      <td>
        <div className="meter" aria-label={`제출 ${row.submitted} / ${row.enrolled}`}>
          <div className="meter__bar meter__bar--submit" style={{ width: `${submitRate * 100}%` }} />
        </div>
        <span className="meter__text">
          {row.submitted} / {row.enrolled}
        </span>
      </td>
      <td className={row.missing > 0 ? 'grade-num grade-num--alert' : 'grade-num'}>{row.missing}</td>
      <td>{row.late > 0 ? <span className="chip chip--warn">지연 {row.late}</span> : <span className="grade-num">0</span>}</td>
      <td>{row.excluded > 0 ? <span className="chip chip--muted">제외 {row.excluded}</span> : <span className="grade-num">0</span>}</td>
      <td>
        <div className="meter">
          <div className={`meter__bar meter__bar--${tone}`} style={{ width: `${avgRate * 100}%` }} />
        </div>
        <span className={`score score--${tone}`}>{displayScore(row.avg_score)}</span>
      </td>
      <td className="grade-num">{displayScore(row.max_score)}</td>
      <td className="grade-num">{displayScore(row.min_score)}</td>
    </tr>
  );
}

function renderReport(report: GradeReport) {
  if (report.count === 0) {
    return <p className="hint">이 학급에는 재적 학생이 없습니다</p>;
  }
  const summary = summarize(report);

  return (
    <>
      <dl className="stat-cards">
        <div className="stat-card">
          <dt>재적 인원</dt>
          <dd>{summary.enrolled}명</dd>
        </div>
        <div className="stat-card">
          <dt>단원</dt>
          <dd>{summary.units}개</dd>
        </div>
        <div className="stat-card stat-card--warn">
          <dt>지연 제출</dt>
          <dd>{summary.late}건</dd>
        </div>
        <div className="stat-card stat-card--muted">
          <dt>제외(무효)</dt>
          <dd>{summary.excluded}건</dd>
        </div>
      </dl>

      <div className="grade-table__wrap">
        <table className="grade-table">
          <thead>
            <tr>
              <th scope="col">단원</th>
              <th scope="col">제출</th>
              <th scope="col">미제출</th>
              <th scope="col">지연</th>
              <th scope="col">제외</th>
              <th scope="col">평균</th>
              <th scope="col">최고</th>
              <th scope="col">최저</th>
            </tr>
          </thead>
          <tbody>{report.items.map(renderRow)}</tbody>
        </table>
      </div>
      <p className="footnote">
        평균은 미제출을 0점으로 넣어 재적 인원으로 나눕니다(보너스 단원은 제출자 평균). 자릿수는 레거시 화면과 같습니다.
      </p>
    </>
  );
}

/** 학급 단원별 성적 현황 — GET /api/grades/report(레거시 /report 이관). */
export function GradeReportView() {
  const [classId, setClassId] = useState(DEFAULT_CLASS_ID);
  const report = useGradeReport(classId);

  return (
    <section className="grade-report" aria-labelledby="grade-report-heading">
      <header className="grade-report__header">
        <div>
          <h2 id="grade-report-heading">학급 단원별 현황</h2>
          <p className="hint">학급을 고르면 단원별 제출 · 지연 · 점수를 보여줍니다.</p>
        </div>
        <ClassPicker options={CLASS_OPTIONS} selectedId={classId} onSelect={setClassId} />
      </header>
      <AsyncSection state={report}>{renderReport}</AsyncSection>
    </section>
  );
}
