import { fireEvent, render, screen } from '@testing-library/react';

import { emptyGradeReport, gradeReportC1 } from '../test/fixtures';
import { mockFetch, mockFetchNetworkError } from '../test/mockFetch';
import { GradeReportView } from './GradeReportView';

describe('GradeReportView (fetch mock)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('처음에는 C1 을 조회해 요약 카드와 단원별 행을 그린다(점수는 서버 자릿수 그대로, 빈 값은 —)', async () => {
    mockFetch({ '/api/grades/report': { body: gradeReportC1 } });

    render(<GradeReportView />);

    expect(screen.getByRole('status').textContent).toContain('불러오는 중');
    expect(await screen.findByText('분수의 덧셈과 뺄셈')).toBeTruthy();
    expect(screen.getByText('10명')).toBeTruthy();
    expect(screen.getByText('64.28')).toBeTruthy();
    expect(screen.getByText('76.80')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy(); // M6-1 최고 점수 없음
    expect(screen.getAllByText('지연 1')).toHaveLength(2); // M5-1 · M5-2
    expect(screen.getByText('2건')).toBeTruthy(); // 지연 합계 카드
    expect(screen.getAllByRole('row')).toHaveLength(gradeReportC1.items.length + 1); // header 포함
  });

  it('학급을 바꾸면 class_id 를 바꿔 다시 조회한다', async () => {
    const fetchSpy = mockFetch({ '/api/grades/report': { body: gradeReportC1 } });

    render(<GradeReportView />);
    await screen.findByText('분수의 덧셈과 뺄셈');

    fireEvent.click(screen.getByRole('radio', { name: /5학년 2반/ }));
    await screen.findByText('분수의 덧셈과 뺄셈');

    const classIds = fetchSpy.mock.calls.map(([input]) => new URL(String(input)).searchParams.get('class_id'));
    expect(classIds).toEqual(['C1', 'C2']);
    expect(screen.getByRole('radio', { name: /5학년 2반/ }).getAttribute('aria-checked')).toBe('true');
  });

  it('재적 학생이 없는 학급이면 빈 결과 문구를 보여준다', async () => {
    mockFetch({ '/api/grades/report': { body: emptyGradeReport } });

    render(<GradeReportView />);

    expect(await screen.findByText('이 학급에는 재적 학생이 없습니다')).toBeTruthy();
  });

  it('성적 DB 오류(502)면 오류 문구를 보여준다', async () => {
    mockFetch({ '/api/grades/report': { status: 502, body: { status: 502, message: '성적 DB 조회에 실패했습니다' } } });

    render(<GradeReportView />);

    expect((await screen.findByRole('alert')).textContent).toBe('서버 오류 (502)');
  });

  it('API 서버에 연결할 수 없으면 연결 오류 문구를 보여준다', async () => {
    mockFetchNetworkError();

    render(<GradeReportView />);

    expect((await screen.findByRole('alert')).textContent).toBe('API 서버에 연결할 수 없습니다');
  });
});
