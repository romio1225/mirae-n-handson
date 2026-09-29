package com.example.grade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 성적 집계 API. 레거시 {@code GET /report?class_id=} 의 이관.
 * 경로 짝은 {@code characterization/lib/target.mjs} 의 PATH_ALIASES({@code /report → /api/grades/report}).
 */
@RestController
@RequestMapping("/api/grades")
public class GradeReportController {

    private final GradeReportService gradeReportService;

    public GradeReportController(GradeReportService gradeReportService) {
        this.gradeReportService = gradeReportService;
    }

    /** {@code GET /api/grades/report?class_id=} — 학급 단원별 현황. 파라미터 이름은 레거시와 같다. */
    @GetMapping("/report")
    public GradeReportResponse classReport(@RequestParam(name = "class_id", required = false) String classId) {
        return gradeReportService.classReport(classId);
    }
}
