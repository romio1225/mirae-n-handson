import { fireEvent, render, screen } from '@testing-library/react';

import { ClassPicker } from './ClassPicker';

const options = [
  { id: 'C1', label: '5학년 1반' },
  { id: 'C2', label: '5학년 2반' },
];

describe('ClassPicker', () => {
  it('학급마다 라디오 버튼을 그리고 선택된 학급만 checked 로 표시한다', () => {
    render(<ClassPicker options={options} selectedId="C1" onSelect={() => {}} />);

    expect(screen.getAllByRole('radio')).toHaveLength(2);
    expect(screen.getByRole('radio', { name: /5학년 1반/ }).getAttribute('aria-checked')).toBe('true');
    expect(screen.getByRole('radio', { name: /5학년 2반/ }).getAttribute('aria-checked')).toBe('false');
  });

  it('학급을 누르면 onSelect(학급 ID) 를 부른다', () => {
    const onSelect = vi.fn();
    render(<ClassPicker options={options} selectedId="C1" onSelect={onSelect} />);

    fireEvent.click(screen.getByRole('radio', { name: /5학년 2반/ }));

    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(onSelect).toHaveBeenCalledWith('C2');
  });
});
