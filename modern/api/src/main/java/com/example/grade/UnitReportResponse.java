package com.example.grade;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 학급 단원별 현황 한 행. 필드 이름 · 표기는 레거시 {@code /report} 표({@code <table id="report">})와 같다.
 *
 * <p>점수는 레거시처럼 DECIMAL 자릿수를 그대로 살린 문자열(평균 소수 둘째 자리, 최고 · 최저 첫째 자리)이고,
 * 값이 없으면 빈 문자열이다(레거시 BR-25).
 */
public record UnitReportResponse(
    String unit,
    @JsonProperty("unit_name") String unitName,
    int enrolled,
    int submitted,
    int missing,
    int late,
    int excluded,
    @JsonProperty("avg_score") String avgScore,
    @JsonProperty("max_score") String maxScore,
    @JsonProperty("min_score") String minScore) {
}
