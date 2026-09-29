package com.example.grade;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 컨트롤러 슬라이스 — 서비스는 목. JSON 필드 이름이 동작 보존 테스트의 정규화 결과와 같은지 본다. */
@WebMvcTest(GradeReportController.class)
@ActiveProfiles("test")
class GradeReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GradeReportService gradeReportService;

    @Test
    @DisplayName("GET /api/grades/report?class_id=C1 → 200, items · count · message 와 레거시 열 이름")
    void classReportReturnsLegacyShapedJson() throws Exception {
        when(gradeReportService.classReport("C1")).thenReturn(GradeReportResponse.of(List.of(
            new UnitReportResponse("M5-1", "분수의 덧셈과 뺄셈", 10, 9, 1, 1, 1, "64.28", "97.5", "0.0"))));

        mockMvc.perform(get("/api/grades/report").param("class_id", "C1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.items[0].unit").value("M5-1"))
            .andExpect(jsonPath("$.items[0].unit_name").value("분수의 덧셈과 뺄셈"))
            .andExpect(jsonPath("$.items[0].enrolled").value(10))
            .andExpect(jsonPath("$.items[0].avg_score").value("64.28"))
            .andExpect(jsonPath("$.items[0].min_score").value("0.0"));
    }

    @Test
    @DisplayName("class_id 없이 호출하면 서비스에 null 을 넘긴다(기본 학급 처리는 서비스 몫)")
    void classReportWithoutClassIdPassesNull() throws Exception {
        when(gradeReportService.classReport(null)).thenReturn(GradeReportResponse.of(List.of()));

        mockMvc.perform(get("/api/grades/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.items").isEmpty());
    }
}
