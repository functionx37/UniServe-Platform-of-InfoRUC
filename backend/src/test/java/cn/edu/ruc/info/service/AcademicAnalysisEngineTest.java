package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AcademicRecord;
import cn.edu.ruc.info.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcademicAnalysisEngineTest {

    private final TranscriptParsingService transcriptParsingService = new TranscriptParsingService();
    private final CurriculumDefinitionParser curriculumDefinitionParser =
            new CurriculumDefinitionParser(new JsonUtils(new ObjectMapper()));
    private final AcademicAnalysisEngine analysisEngine = new AcademicAnalysisEngine();

    @Test
    void shouldParseSampleTranscriptPdf() {
        Path pdfPath = Path.of("../file/成绩单示例/示例成绩单.pdf").normalize();

        TranscriptParsingService.ParseResult result =
                transcriptParsingService.parse(pdfPath, "示例成绩单.pdf", 1L);

        assertNotNull(result);
        assertFalse(result.getRecords().isEmpty());
        assertTrue(result.getRecords().stream().anyMatch(record -> "高等数学Ⅱ".equals(record.getCourseName())));
        assertTrue(result.getRecords().stream().anyMatch(record -> "2024-2025学年春季学期".equals(record.getSemester())));
    }

    @Test
    void shouldParseCurriculumAndProduceRecommendationsFromSamples() {
        Path curriculumPath = Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize();
        Path transcriptPath = Path.of("../file/成绩单示例/示例成绩单.pdf").normalize();

        CurriculumService.CurriculumDefinition definition = curriculumDefinitionParser.parseExcelDefinition(curriculumPath);
        List<AcademicRecord> records = transcriptParsingService.parse(transcriptPath, "示例成绩单.pdf", 1L).getRecords();
        analysisEngine.enrichRecords(records, definition);

        AcademicAnalysisEngine.AnalysisSnapshot snapshot =
                analysisEngine.analyze(records, definition, LocalDate.of(2026, 6, 7));

        assertNotNull(snapshot);
        assertEquals("课程要求单元", snapshot.getMetricLabel());
        assertFalse(snapshot.isPreciseCredits());
        assertFalse(snapshot.getModules().isEmpty());
        assertFalse(snapshot.getMissingRequiredCourses().isEmpty());
        assertFalse(snapshot.getRecommendedCourses().isEmpty());
        assertEquals("2025-2026学年春季学期", snapshot.getSemesterContext().getCurrentLabel());
        assertTrue(snapshot.getMissingRequiredCourses().stream()
                .anyMatch(item -> "毛泽东思想和中国特色社会主义理论体系概论".equals(item.getCourse())));
        assertTrue(snapshot.getRecommendedCourses().stream()
                .anyMatch(item -> item.getCourse().contains("毛泽东思想")));
    }

    @Test
    void shouldNormalizeTranscriptCourseNameWhenRawLineContainsTeacherAndScore() {
        Path curriculumPath = Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize();
        CurriculumService.CurriculumDefinition definition = curriculumDefinitionParser.parseExcelDefinition(curriculumPath);

        AcademicRecord record = new AcademicRecord();
        record.setUserId(1L);
        record.setCourseName("高等数学I 周春来 98 82");

        List<AcademicRecord> records = new ArrayList<>();
        records.add(record);

        analysisEngine.enrichRecords(records, definition);

        assertEquals("高等数学Ⅰ", records.get(0).getCourseName());
        AcademicAnalysisEngine.AnalysisSnapshot snapshot =
                analysisEngine.analyze(records, definition, LocalDate.of(2026, 6, 7));
        assertEquals(1, snapshot.getMatchedCourseCount());
        assertFalse(snapshot.getUnmatchedTranscriptCourses().contains("高等数学I 周春来 98 82"));
    }
}
