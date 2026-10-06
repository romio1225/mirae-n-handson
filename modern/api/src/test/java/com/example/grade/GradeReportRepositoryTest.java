package com.example.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.DatabaseUnavailableException;
import com.example.grade.GradeReportRepository.SubmissionRow;
import com.example.grade.GradeReportRepository.UnitRow;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** MS-SQL 연결이 필요 없는 부분만 본다 — DB 오류를 502 용 예외로 감싸는지, 행 매핑 · 파라미터. */
@ExtendWith(MockitoExtension.class)
class GradeReportRepositoryTest {

    @Mock
    private GradesJdbc gradesJdbc;

    @Mock
    private NamedParameterJdbcTemplate template;

    @InjectMocks
    private GradeReportRepository gradeReportRepository;

    @Test
    @DisplayName("DB 오류는 원문 메시지를 숨긴 DatabaseUnavailableException 으로 감싸 던진다")
    void dataAccessErrorIsWrapped() {
        when(gradesJdbc.template()).thenReturn(template);
        when(template.queryForObject(anyString(), anyMap(), eq(Integer.class)))
            .thenThrow(new DataAccessResourceFailureException("Login failed for user 'readonly'"));

        assertThatThrownBy(() -> gradeReportRepository.countEnrolled("C1"))
            .isInstanceOf(DatabaseUnavailableException.class)
            .hasMessage(GradeReportRepository.UNAVAILABLE_MESSAGE)
            .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("재적 인원 조회는 classId 를 파라미터로 넘기고, 결과가 null 이면 0 을 돌려준다")
    void countEnrolledPassesClassIdAndMapsNullToZero() {
        when(gradesJdbc.template()).thenReturn(template);
        when(template.queryForObject(anyString(), eq(Map.of("classId", "C1")), eq(Integer.class))).thenReturn(null);

        assertThat(gradeReportRepository.countEnrolled("C1")).isZero();
        verify(template).queryForObject(anyString(), eq(Map.of("classId", "C1")), eq(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("제출 행 조회는 classId 를 넘기고 열을 SubmissionRow 로 매핑한다(NULL 점수 유지)")
    void findClassSubmissionsMapsColumns() throws SQLException {
        when(gradesJdbc.template()).thenReturn(template);
        ArgumentCaptor<RowMapper<SubmissionRow>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(template.query(anyString(), eq(Map.of("classId", "C4")), mapper.capture())).thenReturn(List.of());

        gradeReportRepository.findClassSubmissions("C4");

        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 12, 10, 0);
        LocalDateTime dueAt = LocalDateTime.of(2026, 9, 10, 23, 59);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(901);
        when(rs.getString("student_id")).thenReturn("STU-1");
        when(rs.getString("unit_code")).thenReturn("M5-1");
        when(rs.getString("assignment_id")).thenReturn("A1");
        when(rs.getBigDecimal("score")).thenReturn(null);
        when(rs.getObject("submitted_at", LocalDateTime.class)).thenReturn(submittedAt);
        when(rs.getString("status")).thenReturn("S");
        when(rs.getObject("due_at", LocalDateTime.class)).thenReturn(dueAt);

        SubmissionRow row = mapper.getValue().mapRow(rs, 0);

        assertThat(row).isEqualTo(new SubmissionRow(901, "STU-1", "M5-1", "A1", null, submittedAt, "S", dueAt));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("단원 조회는 code · name · weight 를 UnitRow 로 매핑한다")
    void findUnitsMapsColumns() throws SQLException {
        when(gradesJdbc.template()).thenReturn(template);
        ArgumentCaptor<RowMapper<UnitRow>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(template.query(anyString(), eq(Map.of()), mapper.capture())).thenReturn(List.of());

        gradeReportRepository.findUnits();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("code")).thenReturn("M6-2");
        when(rs.getString("name")).thenReturn("비와 비율 (보너스)");
        when(rs.getBigDecimal("weight")).thenReturn(new BigDecimal("0.00"));

        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new UnitRow("M6-2", "비와 비율 (보너스)", new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("제출 행 조회의 DB 오류도 DatabaseUnavailableException 으로 감싼다")
    void findClassSubmissionsErrorIsWrapped() {
        when(gradesJdbc.template()).thenReturn(template);
        when(template.query(anyString(), anyMap(), any(RowMapper.class)))
            .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(() -> gradeReportRepository.findClassSubmissions("C1"))
            .isInstanceOf(DatabaseUnavailableException.class)
            .hasMessage(GradeReportRepository.UNAVAILABLE_MESSAGE);
    }

    @Test
    @DisplayName("단원 조회의 DB 오류도 DatabaseUnavailableException 으로 감싼다")
    void findUnitsErrorIsWrapped() {
        when(gradesJdbc.template()).thenReturn(template);
        when(template.query(anyString(), anyMap(), any(RowMapper.class)))
            .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(() -> gradeReportRepository.findUnits())
            .isInstanceOf(DatabaseUnavailableException.class)
            .hasMessage(GradeReportRepository.UNAVAILABLE_MESSAGE);
    }
}
