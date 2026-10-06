package com.example.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.grade.GradeReportRepository.SubmissionRow;
import com.example.grade.GradeReportRepository.UnitRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** usp_class_report 이관 계산 — 규칙 ID 는 docs/grade/BUSINESS-RULES.md. */
@ExtendWith(MockitoExtension.class)
class GradeReportServiceTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 9, 10, 23, 59, 0);
    private static final UnitRow NORMAL_UNIT = new UnitRow("M5-1", "분수의 덧셈과 뺄셈", new BigDecimal("0.30"));
    private static final UnitRow BONUS_UNIT = new UnitRow("M6-2", "비와 비율 (보너스)", new BigDecimal("0.00"));

    @Mock
    private GradeReportRepository gradeReportRepository;

    @InjectMocks
    private GradeReportService gradeReportService;

    private static SubmissionRow submission(int id, String student, String unit, String score, LocalDateTime at, String status) {
        return new SubmissionRow(id, student, unit, "A-" + unit, score == null ? null : new BigDecimal(score), at, status, DUE);
    }

    @Test
    @DisplayName("재적 0명(없는 학급)이면 빈 목록을 돌려주고 제출 · 단원을 읽지 않는다")
    void emptyClassReturnsNoRows() {
        when(gradeReportRepository.countEnrolled("C9")).thenReturn(0);

        GradeReportResponse result = gradeReportService.classReport("C9");

        assertThat(result.items()).isEmpty();
        assertThat(result.count()).isZero();
        assertThat(result.message()).isNull();
        verify(gradeReportRepository, never()).findClassSubmissions("C9");
    }

    @Test
    @DisplayName("class_id 가 없거나 공백이면 C1, 앞뒤 공백은 지운다")
    void blankClassIdFallsBackToDefault() {
        assertThat(GradeReportService.toProcedureArgument(null)).isEqualTo("C1");
        assertThat(GradeReportService.toProcedureArgument("   ")).isEqualTo("C1");
        assertThat(GradeReportService.toProcedureArgument(" C2 ")).isEqualTo("C2");
    }

    @Test
    @DisplayName("class_id 가 10자를 넘으면 프로시저 인자처럼 10자로 잘린다")
    void longClassIdIsTruncatedToTen() {
        assertThat(GradeReportService.toProcedureArgument("C1        X")).isEqualTo("C1        ");
        assertThat(GradeReportService.toProcedureArgument("C1XXXXXXXXX")).isEqualTo("C1XXXXXXXX");
    }

    @Test
    @DisplayName("상태 X 를 먼저 빼고 최신 1건을 고른다 — 최신이 X 면 그 전 제출이 인정되고 X 는 제외 수에 센다")
    void excludedStatusIsRemovedBeforePickingLatest() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M5-1", "70.0", DUE.minusDays(1), "S"),
            submission(2, "STU-1", "M5-1", "95.0", DUE.minusHours(1), "X")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.submitted()).isEqualTo(1);
        assertThat(row.excluded()).isEqualTo(1);
        assertThat(row.maxScore()).isEqualTo("70.0");
    }

    @Test
    @DisplayName("상태 비교도 DB 정렬 규칙을 따른다 — 소문자 x 와 뒤 공백이 붙은 'X ' 도 집계에서 빼고 제외 수에 센다")
    void excludedStatusFollowsCaseInsensitiveCollation() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M5-1", "70.0", DUE.minusDays(2), "S"),
            submission(2, "STU-1", "M5-1", "95.0", DUE.minusDays(1), "x"),
            submission(3, "STU-1", "M5-1", "99.0", DUE.minusHours(1), "X ")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.submitted()).isEqualTo(1);
        assertThat(row.excluded()).isEqualTo(2);
        assertThat(row.maxScore()).isEqualTo("70.0");
        assertThat(row.avgScore()).isEqualTo("70.00");
    }

    @Test
    @DisplayName("단원 코드 · 이름은 레거시처럼 앞뒤 공백을 지워 내보내고, 뒤 공백이 붙은 코드도 제출과 같은 단원으로 묶는다")
    void unitCodeAndNameAreTrimmedInResponse() {
        UnitRow paddedUnit = new UnitRow("M5-1  ", "  분수의 덧셈과 뺄셈 ", new BigDecimal("0.30"));
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M5-1", "80.0", DUE, "S")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(paddedUnit));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.unit()).isEqualTo("M5-1");
        assertThat(row.unitName()).isEqualTo("분수의 덧셈과 뺄셈");
        assertThat(row.submitted()).isEqualTo(1);
        assertThat(row.maxScore()).isEqualTo("80.0");
    }

    @Test
    @DisplayName("같은 제출 시각이면 id 가 큰 쪽을 인정한다")
    void sameSubmittedAtPicksLargerId() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(5, "STU-1", "M5-1", "60.0", DUE, "S"),
            submission(9, "STU-1", "M5-1", "80.0", DUE, "S")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        assertThat(gradeReportService.classReport("C1").items().get(0).maxScore()).isEqualTo("80.0");
    }

    @Test
    @DisplayName("마감 + 2일을 넘기면 지연 — 점수 × 0.9 를 소수 첫째 자리로 반올림, 정확히 2일은 지연 아님")
    void latePenaltyAppliesOnlyAfterGracePeriod() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(2);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M5-1", "85.5", DUE.plusDays(2).plusSeconds(1), "S"),
            submission(2, "STU-2", "M5-1", "90.0", DUE.plusDays(2), "S")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.late()).isEqualTo(1);
        assertThat(row.minScore()).isEqualTo("77.0"); // 85.5 × 0.9 = 76.95 → 77.0
        assertThat(row.maxScore()).isEqualTo("90.0");
    }

    @Test
    @DisplayName("일반 단원 — 미제출 0점을 넣어 재적 인원으로 나누고, 미제출이 있으면 최저는 0.0")
    void normalUnitCountsMissingAsZero() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(3);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M5-1", "80.0", DUE, "S"),
            submission(2, "STU-2", "M5-1", null, DUE, "S")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.submitted()).isEqualTo(2);
        assertThat(row.missing()).isEqualTo(1);
        assertThat(row.avgScore()).isEqualTo("26.67"); // (80.0 + 0.0) / 3
        assertThat(row.minScore()).isEqualTo("0.0");
    }

    @Test
    @DisplayName("평균은 T-SQL 처럼 나눗셈 결과를 소수 여섯째 자리에서 버린 뒤 둘째 자리로 반올림한다")
    void averageTruncatesIntermediateLikeTsql() {
        // 10000.0 / 2000001 = 0.0049999975… → 여섯째 자리 버림 0.004999 → 0.00 (반올림이었다면 0.005000 → 0.01)
        assertThat(GradeReportService.average(new BigDecimal("10000.0"), 2_000_001).toPlainString()).isEqualTo("0.00");
        assertThat(GradeReportService.average(new BigDecimal("642.8"), 10).toPlainString()).isEqualTo("64.28");
    }

    @Test
    @DisplayName("학생 · 과제 · 단원 코드는 DB 정렬 규칙처럼 대소문자와 뒤 공백을 무시하고 같은 값으로 묶는다")
    void keysFollowCaseInsensitiveCollation() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            new SubmissionRow(1, "STU-1", "m5-1 ", "A1", new BigDecimal("50.0"), DUE.minusDays(1), "S", DUE),
            new SubmissionRow(2, "stu-1", "M5-1", "a1", new BigDecimal("70.0"), DUE.minusHours(1), "S", DUE)));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT));

        UnitReportResponse row = gradeReportService.classReport("C1").items().get(0);

        assertThat(row.submitted()).isEqualTo(1);
        assertThat(row.maxScore()).isEqualTo("70.0");
    }

    @Test
    @DisplayName("단원 키는 과제의 단원이 아니라 제출 행의 unit_code 다")
    void unitKeyComesFromSubmissionRow() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(1);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            new SubmissionRow(1, "STU-1", "M6-2", "A-M5-1", new BigDecimal("80.0"), DUE, "S", DUE)));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT, BONUS_UNIT));

        List<UnitReportResponse> items = gradeReportService.classReport("C1").items();

        assertThat(items.get(0).submitted()).isZero();
        assertThat(items.get(1).submitted()).isEqualTo(1);
    }

    @Test
    @DisplayName("보너스 단원 — 제출자만 평균에 넣고, 아무도 안 냈으면 평균 · 최고 · 최저가 빈 값")
    void bonusUnitAveragesSubmittersOnly() {
        when(gradeReportRepository.countEnrolled("C1")).thenReturn(4);
        when(gradeReportRepository.findClassSubmissions("C1")).thenReturn(List.of(
            submission(1, "STU-1", "M6-2", "61.5", DUE, "S"),
            submission(2, "STU-2", "M6-2", "89.0", DUE, "S")));
        when(gradeReportRepository.findUnits()).thenReturn(List.of(NORMAL_UNIT, BONUS_UNIT));

        List<UnitReportResponse> items = gradeReportService.classReport("C1").items();
        UnitReportResponse normal = items.get(0);
        UnitReportResponse bonus = items.get(1);

        assertThat(bonus.avgScore()).isEqualTo("75.25");
        assertThat(bonus.minScore()).isEqualTo("61.5");
        assertThat(bonus.missing()).isEqualTo(2);
        assertThat(normal.submitted()).isZero();
        assertThat(normal.avgScore()).isEqualTo("0.00");
        assertThat(normal.maxScore()).isEmpty();
        assertThat(normal.minScore()).isEqualTo("0.0");
    }
}
