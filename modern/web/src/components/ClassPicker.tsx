export interface ClassOption {
  id: string;
  label: string;
}

interface ClassPickerProps {
  options: readonly ClassOption[];
  selectedId: string;
  onSelect: (id: string) => void;
}

/** 학급 선택(세그먼트 버튼). 목록은 부모가 넘긴다. */
export function ClassPicker({ options, selectedId, onSelect }: ClassPickerProps) {
  return (
    <div className="class-picker" role="radiogroup" aria-label="학급">
      {options.map((option) => {
        const checked = option.id === selectedId;
        return (
          <button
            key={option.id}
            type="button"
            role="radio"
            aria-checked={checked}
            className={`class-picker__option${checked ? ' class-picker__option--checked' : ''}`}
            onClick={() => onSelect(option.id)}
          >
            <span className="class-picker__id">{option.id}</span>
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
