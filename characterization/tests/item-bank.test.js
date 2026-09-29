// 동작 보존 테스트 — 문항 은행(item-bank)의 문항 검색 화면 search.php
//
// - 대상 주소는 lib/target.mjs 가 정한다(TARGET_BASE_URL, 없으면 레거시 기본 주소). 주소를 여기 적지 않는다.
// - 기대값을 적지 않는다. 지금 시스템의 실제 응답이 기대값이다(npm run baseline -- item-bank).
// - 케이스 이름 앞의 [BR-xx] 는 docs/item-bank/BUSINESS-RULES.md 의 규칙 ID 다.
// - 이관 후 새 API 에도 그대로 돌리므로 레거시 전용 건너뛰기를 두지 않는다.
import { describe, expect, it } from 'vitest';
import { fetchNormalized } from '../lib/target.mjs';

const MODULE = 'item-bank';
const PATH = '/search.php';

const search = (params) => fetchNormalized(MODULE, PATH, params);

describe('item-bank · 문항 검색(search.php)', () => {
  // ---------------------------------------------------------------- 정상 입력
  it('정상 — 조건 없이 기본 검색 [BR-02 · BR-11 · BR-01]', async () => {
    expect(await search({})).toMatchSnapshot();
  });

  it('정상 — 키워드 "분수" 는 제목 또는 지문에서 찾는다 [BR-06]', async () => {
    expect(await search({ q: '분수' })).toMatchSnapshot();
  });

  it('정상 — 단원 M5-1 + 난이도 3 [BR-07 · BR-03]', async () => {
    expect(await search({ unit: 'M5-1', level: '3' })).toMatchSnapshot();
  });

  it('정상 — 태그 "문장제" 가 붙은 문항 [BR-09 · BR-17]', async () => {
    expect(await search({ tag: '문장제' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 경계값
  it('경계 — 난이도 5 를 명시하면 나온다 (기본 검색은 level < 5) [BR-02 · BR-03]', async () => {
    expect(await search({ level: '5' })).toMatchSnapshot();
  });

  it('경계 — 난이도 1 (허용 범위 아래 끝) [BR-03]', async () => {
    expect(await search({ level: '1' })).toMatchSnapshot();
  });

  it('경계 — 난이도 6 (허용 범위 바로 위) [BR-04]', async () => {
    expect(await search({ level: '6' })).toMatchSnapshot();
  });

  it('경계 — 난이도 0 (허용 범위 바로 아래) [BR-04]', async () => {
    expect(await search({ level: '0' })).toMatchSnapshot();
  });

  it('경계 — 난이도 "05" 는 정규식은 틀리지만 정수 5 로 조회된다 [BR-04]', async () => {
    expect(await search({ level: '05' })).toMatchSnapshot();
  });

  it('경계 — 기본 검색 2페이지: 전체 20건이라 빈 표 [BR-14 · BR-15 · BR-16]', async () => {
    expect(await search({ page: '2' })).toMatchSnapshot();
  });

  it('경계 — page=0 은 숫자라 경고 없이 1페이지 [BR-14]', async () => {
    expect(await search({ page: '0' })).toMatchSnapshot();
  });

  it('경계 — 키워드 정확히 100자는 자르지 않는다 [BR-05]', async () => {
    expect(await search({ q: '분'.repeat(100) })).toMatchSnapshot();
  });

  it('경계 — 단원 정렬 내림차순: 보조 정렬은 level DESC, id ASC [BR-12]', async () => {
    expect(await search({ sort: 'unit', dir: 'desc' })).toMatchSnapshot();
  });

  it('경계 — 난이도 정렬 방향 생략 시 내림차순 [BR-12]', async () => {
    expect(await search({ sort: 'level' })).toMatchSnapshot();
  });

  it('경계 — 등록일 정렬 오름차순, 같은 시각이면 id 순 [BR-12]', async () => {
    expect(await search({ sort: 'created', dir: 'asc' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 빈 값 · 누락
  it('빈값 — level= 은 파라미터 없음과 같은 level < 5 [BR-02]', async () => {
    expect(await search({ level: '' })).toMatchSnapshot();
  });

  it('빈값 — q · unit · tag 모두 빈 문자열 [BR-02 · BR-05 · BR-07 · BR-09]', async () => {
    expect(await search({ q: '', unit: '', tag: '' })).toMatchSnapshot();
  });

  it('빈값 — 공백만 있는 키워드는 trim 되어 조건 없음 [BR-05]', async () => {
    expect(await search({ q: '   ' })).toMatchSnapshot();
  });

  // ---------------------------------------------------------------- 이상한 값
  it('이상값 — 음수 난이도 -1 [BR-04]', async () => {
    expect(await search({ level: '-1' })).toMatchSnapshot();
  });

  it('이상값 — 숫자가 아닌 난이도 abc 는 0 으로 조회 [BR-04]', async () => {
    expect(await search({ level: 'abc' })).toMatchSnapshot();
  });

  it('이상값 — 없는 단원 코드 Z9-9 [BR-08]', async () => {
    expect(await search({ unit: 'Z9-9' })).toMatchSnapshot();
  });

  it('이상값 — 소문자 단원 코드 m5-1 (경고만, 입력값 그대로 조회) [BR-07]', async () => {
    expect(await search({ unit: 'm5-1' })).toMatchSnapshot();
  });

  it('이상값 — 없는 태그 [BR-09 · BR-10]', async () => {
    expect(await search({ tag: '없는태그' })).toMatchSnapshot();
  });

  it('경계 — 키워드 101자는 100자로 잘려 조회된다 [BR-05]', async () => {
    expect(await search({ q: '분'.repeat(101) })).toMatchSnapshot();
  });

  it('이상값 — 아주 긴 키워드 1000자 (URL 길이 한도에 걸리는지 포함) [BR-05]', async () => {
    expect(await search({ q: '가'.repeat(1000) })).toMatchSnapshot();
  });

  it('이상값 — 키워드 "%" 는 와일드카드로 전체와 같다 [BR-06]', async () => {
    expect(await search({ q: '%' })).toMatchSnapshot();
  });

  it('이상값 — 모르는 정렬 기준 · 방향은 기본 정렬 [BR-11 · BR-13]', async () => {
    expect(await search({ sort: 'price', dir: 'sideways' })).toMatchSnapshot();
  });

  it('이상값 — page=abc 는 1페이지, page=1000 은 999페이지(빈 표) [BR-14 · BR-16]', async () => {
    expect({
      abc: await search({ page: 'abc' }),
      over: await search({ page: '1000' }),
    }).toMatchSnapshot();
  });
});
