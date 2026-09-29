import type { GradeReport, Item, Unit } from '../api/types';

export const units: Unit[] = [
  { id: 1, code: 'M5-1', name: '분수의 덧셈과 뺄셈', grade: 5 },
  { id: 2, code: 'M5-2', name: '분수의 곱셈', grade: 5 },
  { id: 4, code: 'M6-1', name: '분수의 나눗셈', grade: 6 },
];

export const item2: Item = {
  id: 2,
  title: '분모가 다른 분수의 뺄셈',
  stem: '5/6 - 1/4 를 계산하시오.',
  unit: 'M5-1',
  level: 3,
  status: 'A',
  tags: ['계산', '오답률높음'],
};

export const itemsOfM51: Item[] = [
  {
    id: 1,
    title: '분모가 같은 분수의 덧셈',
    stem: '1/5 + 2/5 를 계산하시오.',
    unit: 'M5-1',
    level: 1,
    status: 'A',
    tags: ['계산'],
  },
  item2,
  {
    id: 3,
    title: '분수 덧셈 문장제',
    stem: '지수는 리본 3/4 m 를, 민호는 2/3 m 를 가지고 있습니다. 두 사람이 가진 리본은 모두 몇 m 입니까?',
    unit: 'M5-1',
    level: 5,
    status: 'A',
    tags: [],
  },
];

/** 성적 현황 — 레거시 /report?class_id=C1 의 앞 두 단원과 보너스 단원(값은 동작 보존 스냅샷과 같다) */
export const gradeReportC1: GradeReport = {
  items: [
    {
      unit: 'M5-1',
      unit_name: '분수의 덧셈과 뺄셈',
      enrolled: 10,
      submitted: 9,
      missing: 1,
      late: 1,
      excluded: 1,
      avg_score: '64.28',
      max_score: '97.5',
      min_score: '0.0',
    },
    {
      unit: 'M5-2',
      unit_name: '분수의 곱셈',
      enrolled: 10,
      submitted: 10,
      missing: 0,
      late: 1,
      excluded: 0,
      avg_score: '76.80',
      max_score: '96.0',
      min_score: '62.0',
    },
    {
      unit: 'M6-1',
      unit_name: '분수의 나눗셈',
      enrolled: 10,
      submitted: 0,
      missing: 10,
      late: 0,
      excluded: 0,
      avg_score: '0.00',
      max_score: '',
      min_score: '0.0',
    },
  ],
  count: 3,
  message: null,
};

export const emptyGradeReport: GradeReport = { items: [], count: 0, message: null };
