// 문항 은행 MCP 서버 골격 (TypeScript SDK v2 · stdio)
//
// stdio 서버에서는 stdout 이 곧 프로토콜 채널이다.
// 로그는 console.error 만 쓴다(stdout 으로 찍는 로그 함수는 절대 쓰지 않는다).
import { McpServer } from '@modelcontextprotocol/server';
import { StdioServerTransport } from '@modelcontextprotocol/server/stdio';

const server = new McpServer({ name: 'item-bank', version: '1.0.0' });

// 예시 도구: 서버가 살아 있는지 확인한다. 입력 없음.
server.registerTool(
  'ping',
  {
    description: '서버 연결 확인용 예시 도구. "pong" 을 돌려준다.'
  },
  async () => ({
    content: [{ type: 'text', text: 'pong' }]
  })
);

// ================================================================
// 여기에 도구를 등록합니다
//
//   server.registerTool('도구_이름', { description, inputSchema }, handler)
//
// - inputSchema 는 z.object({...}) 전체 스키마로 넘긴다 (import * as z from 'zod/v4')
// - 감쌀 API 는 ./itemApi.js 에 있다 (ESM 이라 확장자를 .js 로 적는다)
// - import 문도 이 자리에 함께 붙여 넣어도 된다
// ================================================================
import * as z from 'zod/v4';
import { searchItems, updateItemTags } from './itemApi.js';

/** 한 번에 돌려줄 수 있는 최대 건수 */
const MAX_LIMIT = 20;
/** limit 을 주지 않았을 때의 건수 */
const DEFAULT_LIMIT = 5;

server.registerTool(
  'search_items',
  {
    description:
      '문항 은행(사내 문항 조회 API)에서 키워드 · 단원 · 난이도로 문항을 검색한다. ' +
      '문항 본문이나 태그, 단원별 문항 구성을 확인해야 할 때 사용한다. 읽기 전용이며 문항을 바꾸지 않는다. ' +
      '난이도는 이 API 표기인 하 · 중 · 상 이다(itembank DB 의 level 1~5 와 다르다).',
    inputSchema: z.object({
      keyword: z.string().describe('문항 본문 또는 태그에서 찾을 키워드 (예: 분수, 문장제)'),
      unit: z.string().optional().describe('단원 코드(예: M5-1) 또는 단원 이름 일부(예: 분수의 곱셈)'),
      difficulty: z.enum(['하', '중', '상']).optional().describe('난이도: 하 · 중 · 상 중 하나'),
      limit: z
        .number()
        .int()
        .min(1)
        .max(MAX_LIMIT)
        .default(DEFAULT_LIMIT)
        .describe(`돌려줄 최대 건수 (1~${MAX_LIMIT}, 기본 ${DEFAULT_LIMIT})`)
    })
  },
  async ({ keyword, unit, difficulty, limit }) => {
    const items = await searchItems({ keyword, unit, difficulty, limit });
    console.error(`search_items: ${items.length}건`);
    const text =
      items.length > 0
        ? JSON.stringify(items, null, 2)
        : `검색 결과 없음: ${JSON.stringify({ keyword, unit, difficulty, limit })}`;
    return { content: [{ type: 'text', text }] };
  }
);

/** 한 문항에 붙일 수 있는 최대 태그 수 */
const MAX_TAGS = 10;
/** 태그 하나의 최대 길이 */
const MAX_TAG_LENGTH = 30;

// 쓰기 도구(심화 2). 더미 API 의 메모리 배열만 바꾼다 — 서버를 다시 켜면 원래대로 돌아온다.
// 쓰기를 여는 순간 권한 규칙도 같이 둔다: .claude/settings.json 의 permissions.ask 에 이 도구가 있다.
server.registerTool(
  'update_item_tags',
  {
    description:
      '문항 하나의 태그 목록을 통째로 바꾼다(쓰기). 사용자가 특정 문항의 태그 변경을 명시적으로 요청했을 때만 사용한다. ' +
      '조회만 필요하면 search_items 를 쓴다. 이 실습 서버에서는 메모리만 바뀌고 서버를 다시 켜면 원래대로 돌아온다.',
    inputSchema: z.object({
      id: z.number().int().positive().describe('태그를 바꿀 문항 id (search_items 결과의 id)'),
      tags: z
        .array(z.string().trim().min(1).max(MAX_TAG_LENGTH))
        .min(1)
        .max(MAX_TAGS)
        .describe(`새 태그 목록(기존 태그를 대체한다, 1~${MAX_TAGS}개, 공백만 있는 태그는 거부, 중복은 하나로 합친다)`)
    })
  },
  async ({ id, tags }) => {
    const updated = await updateItemTags(id, tags);
    console.error(`update_item_tags: id=${id} ${updated ? '변경' : '없는 id'}`);
    const text = updated
      ? JSON.stringify(updated, null, 2)
      : `문항 없음: ${JSON.stringify({ id })}`;
    return { content: [{ type: 'text', text }], isError: updated === null };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('item-bank MCP server running on stdio');
}

main().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
