package com.example.grade;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.common.DatabaseUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** MS-SQL 연결이 필요 없는 부분만 본다 — DB 오류를 502 용 예외로 감싸는지. */
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
}
