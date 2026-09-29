package com.example.grade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * 성적 집계 MS-SQL 조회. 계산은 하지 않고 원자료 행만 돌려준다(계산은 {@link GradeReportService}).
 *
 * <p>레거시 {@code usp_class_report} 가 읽는 테이블(student · submission · assignment · unit)을 같은 조건으로 읽는다.
 */
@Repository
public class GradeReportRepository {

    private static final String COUNT_ENROLLED_SQL =
        "SELECT COUNT(*) FROM dbo.student AS s WHERE s.class_id = :classId";

    private static final String CLASS_SUBMISSIONS_SQL =
        "SELECT sub.id, sub.student_id, sub.unit_code, sub.assignment_id, sub.score, sub.submitted_at, sub.status, a.due_at"
            + " FROM dbo.submission AS sub"
            + " JOIN dbo.student AS s ON s.id = sub.student_id"
            + " JOIN dbo.assignment AS a ON a.id = sub.assignment_id"
            + " WHERE s.class_id = :classId";

    // 단원 순서는 DB 정렬 규칙(collation)을 따르도록 DB 에서 정렬한다(레거시 ORDER BY r.unit_code).
    private static final String UNITS_SQL =
        "SELECT u.code, u.name, u.weight FROM dbo.unit AS u ORDER BY u.code";

    private final GradesJdbc gradesJdbc;

    public GradeReportRepository(GradesJdbc gradesJdbc) {
        this.gradesJdbc = gradesJdbc;
    }

    /** 학급 재적 인원. */
    public int countEnrolled(String classId) {
        Integer n = gradesJdbc.template().queryForObject(COUNT_ENROLLED_SQL, Map.of("classId", classId), Integer.class);
        return n == null ? 0 : n;
    }

    /** 학급 학생의 모든 제출 행(상태 X 포함)과 과제 마감 시각. */
    public List<SubmissionRow> findClassSubmissions(String classId) {
        return gradesJdbc.template().query(CLASS_SUBMISSIONS_SQL, Map.of("classId", classId), (rs, rowNum) ->
            new SubmissionRow(
                rs.getInt("id"),
                rs.getString("student_id"),
                rs.getString("unit_code"),
                rs.getString("assignment_id"),
                rs.getBigDecimal("score"),
                rs.getObject("submitted_at", LocalDateTime.class),
                rs.getString("status"),
                rs.getObject("due_at", LocalDateTime.class)));
    }

    /** 전 단원(학급과 무관), 단원 코드 순. */
    public List<UnitRow> findUnits() {
        return gradesJdbc.template().query(UNITS_SQL, Map.of(), (rs, rowNum) ->
            new UnitRow(rs.getString("code"), rs.getString("name"), rs.getBigDecimal("weight")));
    }

    /** 제출 한 행. {@code score} 는 NULL 일 수 있다. */
    public record SubmissionRow(
        int id,
        String studentId,
        String unitCode,
        String assignmentId,
        BigDecimal score,
        LocalDateTime submittedAt,
        String status,
        LocalDateTime dueAt) {
    }

    /** 단원 한 행. {@code weight} 가 0.00 이면 보너스 단원. */
    public record UnitRow(String code, String name, BigDecimal weight) {
    }
}
