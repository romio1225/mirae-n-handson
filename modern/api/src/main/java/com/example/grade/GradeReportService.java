package com.example.grade;

import com.example.grade.GradeReportRepository.SubmissionRow;
import com.example.grade.GradeReportRepository.UnitRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 학급 단원별 현황 — 레거시 저장 프로시저 {@code dbo.usp_class_report}
 * ({@code legacy/grade-mssql/sql/usp_class_report.sql})의 계산을 Java 로 옮긴 것.
 *
 * <p>규칙 ID 는 {@code docs/grade/BUSINESS-RULES.md} 의 것이다. 레거시 동작은 이상해 보여도 그대로 옮긴다.
 *
 * <p>{@code @Transactional(readOnly = true)} 를 붙이지 않는다. 이 서비스는 MS-SQL({@link GradesJdbc})만 읽는데,
 * 스프링 트랜잭션 관리자는 MariaDB(itembank-pool, 최대 5개)를 관리한다. 붙이면 쓰지도 않는 MariaDB 연결을 호출마다 붙잡는다.
 *
 * <p>옮기지 않은 것: 보고 시각 기록(BR-23, REP:161-177). 읽기 전용 계정으로 조회하므로 쓰지 않는다.
 * DB 오류는 리포지토리가 502 로 바꾼다(BR-26 상태 코드는 같고, DB 원문 메시지는 싣지 않는다).
 */
@Service
public class GradeReportService {

    private static final Logger log = LoggerFactory.getLogger(GradeReportService.class);

    /** class_id 가 없거나 공백이면 쓰는 학급(BR-24, GradeController.java:23). */
    static final String DEFAULT_CLASS_ID = "C1";
    /** 프로시저 인자 {@code @class_id VARCHAR(10)} — 넘는 값은 조용히 잘린다(BR-28, REP:24). */
    static final int MAX_CLASS_ID_LENGTH = 10;
    /** 집계 제외 제출 상태(BR-01, REP:89 · REP:152). */
    private static final String EXCLUDED_STATUS = "X";
    /** 지연 판정 유예 일수(BR-06, REP:70). */
    private static final int LATE_GRACE_DAYS = 2;
    /** 지연 제출 반영 비율 — 10% 감점(BR-06, REP:71). */
    private static final BigDecimal LATE_PENALTY_RATE = new BigDecimal("0.9");
    /** 점수 · 최고 · 최저 자릿수 DECIMAL(5,1)(BR-27). */
    private static final int SCORE_SCALE = 1;
    /** 평균 자릿수 DECIMAL(5,2)(BR-27, REP:124 · REP:126). */
    private static final int AVG_SCALE = 2;
    /** T-SQL DECIMAL(38,1) ÷ INT 의 결과 자릿수 6 — 이 자리에서 반올림하지 않고 버린다(SELECT 로 확인: 2.0/3 = .666666). */
    private static final int DIVISION_SCALE = 6;
    /** 점수 없음 · 미제출의 점수 0.0 — 자릿수 1 을 유지해야 "0.0" 으로 나간다(REP:71-72, REP:131). */
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.0");

    private final GradeReportRepository gradeReportRepository;

    public GradeReportService(GradeReportRepository gradeReportRepository) {
        this.gradeReportRepository = gradeReportRepository;
    }

    /** 학급 단원별 현황. 없는 학급 · 재적 0명이면 빈 목록(REP:37-51). */
    public GradeReportResponse classReport(String classIdParam) {
        String classId = toProcedureArgument(classIdParam);

        int enrolled = gradeReportRepository.countEnrolled(classId);
        if (enrolled == 0) {
            return GradeReportResponse.of(List.of());
        }

        List<SubmissionRow> submissions = gradeReportRepository.findClassSubmissions(classId);
        Map<String, UnitAggregate> byUnit = aggregateEffectiveSubmissions(submissions);
        Map<String, Integer> excludedByUnit = countExcluded(submissions);

        List<UnitReportResponse> items = new ArrayList<>();
        for (UnitRow unit : gradeReportRepository.findUnits()) {
            String key = sqlKey(unit.code());
            items.add(toUnitReport(unit, enrolled, byUnit.get(key), excludedByUnit.getOrDefault(key, 0)));
        }
        log.debug("class report built: units={}", items.size());
        return GradeReportResponse.of(items);
    }

    /** 레거시 호출부의 class_id 처리(BR-24) 뒤 프로시저 인자 VARCHAR(10) 잘림(BR-28)까지 재현한다. */
    static String toProcedureArgument(String classIdParam) {
        String classId = (classIdParam == null || classIdParam.isBlank()) ? DEFAULT_CLASS_ID : classIdParam.trim();
        return classId.length() > MAX_CLASS_ID_LENGTH ? classId.substring(0, MAX_CLASS_ID_LENGTH) : classId;
    }

    /**
     * 유효 제출(REP:65-93) → 단원별 합계(REP:136-143).
     * 상태 X 를 먼저 빼고(BR-01) 그다음 학생 · 과제별 최신 1건을 고른다(BR-02). 단원 키는 제출의 unit_code 다(BR-21).
     */
    private static Map<String, UnitAggregate> aggregateEffectiveSubmissions(List<SubmissionRow> submissions) {
        Map<StudentAssignment, SubmissionRow> latest = new LinkedHashMap<>();
        for (SubmissionRow row : submissions) {
            if (isExcluded(row)) {
                continue;
            }
            latest.merge(new StudentAssignment(sqlKey(row.studentId()), sqlKey(row.assignmentId())), row,
                (current, candidate) -> isLater(candidate, current) ? candidate : current);
        }

        Map<String, UnitAggregate> byUnit = new HashMap<>();
        for (SubmissionRow row : latest.values()) {
            boolean late = row.submittedAt().isAfter(row.dueAt().plusDays(LATE_GRACE_DAYS));
            byUnit.computeIfAbsent(sqlKey(row.unitCode()), k -> new UnitAggregate()).add(effectiveScore(row, late), late);
        }
        return byUnit;
    }

    /** status = 'X' 를 DB 정렬 규칙(대소문자 · 뒤 공백 무시)대로 비교한다(BR-01, REP:89 · REP:152). */
    private static boolean isExcluded(SubmissionRow row) {
        return EXCLUDED_STATUS.equals(sqlKey(row.status()));
    }

    /** ORDER BY submitted_at DESC, id DESC 의 첫 행(BR-02, REP:83). */
    private static boolean isLater(SubmissionRow candidate, SubmissionRow current) {
        int bySubmittedAt = candidate.submittedAt().compareTo(current.submittedAt());
        return bySubmittedAt > 0 || (bySubmittedAt == 0 && candidate.id() > current.id());
    }

    /** NULL 점수는 0.0(BR-05), 지연이면 ROUND(점수 × 0.9, 1)(BR-06). */
    private static BigDecimal effectiveScore(SubmissionRow row, boolean late) {
        BigDecimal score = row.score() == null ? ZERO_SCORE : row.score();
        return late ? score.multiply(LATE_PENALTY_RATE).setScale(SCORE_SCALE, RoundingMode.HALF_UP) : score;
    }

    /** 단원별 제외 수 — 상태 X 인 제출 행 전부(최신 여부와 무관, BR-22, REP:146-155). */
    private static Map<String, Integer> countExcluded(List<SubmissionRow> submissions) {
        Map<String, Integer> excluded = new HashMap<>();
        for (SubmissionRow row : submissions) {
            if (isExcluded(row)) {
                excluded.merge(sqlKey(row.unitCode()), 1, Integer::sum);
            }
        }
        return excluded;
    }

    /** 단원 한 행(REP:112-155). */
    private static UnitReportResponse toUnitReport(UnitRow unit, int enrolled, UnitAggregate agg, int excluded) {
        boolean bonus = unit.weight().signum() == 0;
        int submitted = agg == null ? 0 : agg.count;
        int late = agg == null ? 0 : agg.late;

        BigDecimal avg;
        if (bonus) {
            // 보너스 단원: 제출자 평균, 아무도 안 냈으면 NULL(BR-19, REP:122-124)
            avg = submitted == 0 ? null : average(agg.sum, submitted);
        } else {
            // 일반 단원: 미제출 0점 포함, 재적 인원으로 나눈다(BR-18, REP:126)
            avg = average(agg == null ? ZERO_SCORE : agg.sum, enrolled);
        }

        BigDecimal max = agg == null ? null : agg.max;
        BigDecimal min;
        if (bonus) {
            min = agg == null ? null : agg.min;
        } else if (submitted < enrolled) {
            min = ZERO_SCORE; // 미제출이 있으면 최저는 0(BR-20, REP:131)
        } else {
            min = agg.min;
        }

        return new UnitReportResponse(
            unit.code().trim(), // 레거시는 DECIMAL 외 값을 trim 해서 내보낸다(GradeRepository.java:52)
            unit.name().trim(),
            enrolled,
            submitted,
            enrolled - submitted, // 레거시 그대로 — 단원에 과제가 여럿이면 음수가 될 수 있다(REP:117)
            late,
            excluded,
            format(avg),
            format(max),
            format(min));
    }

    /** ROUND(합 / n, 2) — T-SQL 처럼 나눗셈 결과를 자릿수 6 에서 버린 뒤 둘째 자리로 반올림한다. */
    static BigDecimal average(BigDecimal sum, int n) {
        return sum.divide(BigDecimal.valueOf(n), DIVISION_SCALE, RoundingMode.DOWN)
            .setScale(AVG_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * SQL 비교 키. DB 정렬 규칙 SQL_Latin1_General_CP1_CI_AS 는 대소문자를 무시하고 뒤 공백을 무시한다.
     * 레거시의 PARTITION BY · GROUP BY · JOIN(REP:82, :143, :145, :155)이 같은 값으로 보는 코드를 Java 에서도 한 키로 묶는다.
     */
    static String sqlKey(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end).toUpperCase(Locale.ROOT);
    }

    /** DECIMAL 은 자릿수를 살린 문자열, NULL 은 빈 문자열(BR-25, GradeRepository.java:45-53). */
    private static String format(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private record StudentAssignment(String studentId, String assignmentId) {
    }

    /** 단원별 COUNT · SUM · MAX · MIN · 지연 수. */
    private static final class UnitAggregate {
        private int count;
        private int late;
        private BigDecimal sum = ZERO_SCORE;
        private BigDecimal max;
        private BigDecimal min;

        void add(BigDecimal score, boolean isLate) {
            count++;
            if (isLate) {
                late++;
            }
            sum = sum.add(score);
            max = (max == null || score.compareTo(max) > 0) ? score : max;
            min = (min == null || score.compareTo(min) < 0) ? score : min;
        }
    }
}
