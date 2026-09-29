import { useState } from 'react';

import { API_BASE } from './api/client';
import { GradeReportView } from './components/GradeReportView';
import { ItemBrowser } from './components/ItemBrowser';
import { ModuleTabs } from './components/ModuleTabs';
import type { ModuleKey } from './components/ModuleTabs';

export function App() {
  const [active, setActive] = useState<ModuleKey>('items');

  return (
    <div className="app">
      <header className="app__header">
        <div>
          <p className="app__eyebrow">에듀테크 현행 콘솔</p>
          <h1>{active === 'items' ? '문항 은행' : '성적 현황'}</h1>
        </div>
        <p className="app__api">
          API: <code>{API_BASE}</code>
        </p>
      </header>
      <ModuleTabs active={active} onChange={setActive} />
      <main>{active === 'items' ? <ItemBrowser /> : <GradeReportView />}</main>
    </div>
  );
}
