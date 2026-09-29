import { fireEvent, render, screen } from '@testing-library/react';

import { ModuleTabs } from './ModuleTabs';

describe('ModuleTabs', () => {
  it('탭 두 개를 그리고 현재 탭만 선택 상태로 표시한다', () => {
    render(<ModuleTabs active="items" onChange={() => {}} />);

    expect(screen.getAllByRole('tab')).toHaveLength(2);
    expect(screen.getByRole('tab', { name: /문항 은행/ }).getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tab', { name: /성적 현황/ }).getAttribute('aria-selected')).toBe('false');
  });

  it('다른 탭을 누르면 onChange 를 그 키로 한 번 부르고, 현재 탭을 누르면 부르지 않는다', () => {
    const onChange = vi.fn();
    render(<ModuleTabs active="items" onChange={onChange} />);

    fireEvent.click(screen.getByRole('tab', { name: /문항 은행/ }));
    fireEvent.click(screen.getByRole('tab', { name: /성적 현황/ }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('grades');
  });
});
