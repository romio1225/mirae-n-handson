package com.example.grade;

import java.util.List;

/**
 * 학급 단원별 현황 응답. 모양은 동작 보존 테스트의 정규화 결과({@code items → rows}, {@code count}, {@code message})와 같다.
 *
 * @param items   단원별 행(단원 코드 순)
 * @param count   행 수(레거시 "단원 N건")
 * @param message 안내 문구. 레거시 화면에 없으므로 항상 null
 */
public record GradeReportResponse(List<UnitReportResponse> items, int count, String message) {

    public static GradeReportResponse of(List<UnitReportResponse> items) {
        return new GradeReportResponse(items, items.size(), null);
    }
}
