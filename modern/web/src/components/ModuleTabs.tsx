export type ModuleKey = 'items' | 'grades';

interface ModuleTab {
  key: ModuleKey;
  label: string;
  description: string;
}

const TABS: readonly ModuleTab[] = [
  { key: 'items', label: '문항 은행', description: '단원 · 문항 탐색' },
  { key: 'grades', label: '성적 현황', description: '학급 단원별 집계' },
];

interface ModuleTabsProps {
  active: ModuleKey;
  onChange: (key: ModuleKey) => void;
}

/** 화면 전환 탭. 선택 상태는 부모가 가진다. */
export function ModuleTabs({ active, onChange }: ModuleTabsProps) {
  const handleSelect = (key: ModuleKey) => {
    if (key !== active) {
      onChange(key);
    }
  };

  return (
    <nav className="module-tabs" role="tablist" aria-label="화면 선택">
      {TABS.map((tab) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          aria-selected={tab.key === active}
          className={`module-tabs__tab${tab.key === active ? ' module-tabs__tab--active' : ''}`}
          onClick={() => handleSelect(tab.key)}
        >
          <span className="module-tabs__label">{tab.label}</span>
          <span className="module-tabs__desc">{tab.description}</span>
        </button>
      ))}
    </nav>
  );
}
