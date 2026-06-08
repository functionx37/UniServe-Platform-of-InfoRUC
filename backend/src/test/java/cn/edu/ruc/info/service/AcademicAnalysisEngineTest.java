package cn.edu.ruc.info.service;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.entity.AcademicRecord;
import cn.edu.ruc.info.util.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
        record.setCourseName("高等数学I 周春来 部类基础 5 95 86");

        List<AcademicRecord> records = new ArrayList<>();
        records.add(record);

        analysisEngine.enrichRecords(records, definition);

        assertEquals("高等数学Ⅰ", records.get(0).getCourseName());
        AcademicAnalysisEngine.AnalysisSnapshot snapshot =
                analysisEngine.analyze(records, definition, LocalDate.of(2026, 6, 7));
        assertEquals(1, snapshot.getMatchedCourseCount());
        assertFalse(snapshot.getUnmatchedTranscriptCourses().contains("高等数学I 周春来 部类基础 5 95 86"));
    }

    @Test
    void shouldMatchRealTranscriptPdfWhenLocalFileExists() {
        Path curriculumPath = Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize();
        Path transcriptPath = findLocalRealTranscriptPdf();
        assumeTrue(transcriptPath != null, "local regression file not present");

        CurriculumService.CurriculumDefinition definition = curriculumDefinitionParser.parseExcelDefinition(curriculumPath);
        List<AcademicRecord> records = transcriptParsingService.parse(
                transcriptPath,
                transcriptPath.getFileName().toString(),
                1L,
                definition).getRecords();
        analysisEngine.enrichRecords(records, definition);
        AcademicAnalysisEngine.AnalysisSnapshot snapshot =
                analysisEngine.analyze(records, definition, LocalDate.of(2026, 6, 8));

        assertFalse(records.isEmpty(), "real transcript parsed no course records");
        assertTrue(snapshot.getMatchedCourseCount() > 0,
                "real transcript matched 0 courses, unmatched=" + snapshot.getUnmatchedTranscriptCourses());
        assertTrue(records.stream().anyMatch(record -> record.getCourseName().contains("高等数学")),
                "real transcript did not include expected 高等数学 course, records=" + records);
        assertTrue(snapshot.getMatchedCourseCount() >= 15,
                "real transcript matched too few courses, matched=" + snapshot.getMatchedCourseCount()
                        + ", records=" + records);
        Set<String> parsedCourses = new HashSet<>(records.stream().map(AcademicRecord::getCourseName).toList());
        assertTrue(parsedCourses.contains("高等数学Ⅰ"), "missing parsed course 高等数学Ⅰ: " + parsedCourses);
        assertTrue(parsedCourses.contains("高等代数Ⅰ"), "missing parsed course 高等代数Ⅰ: " + parsedCourses);
        assertTrue(parsedCourses.contains("思想道德与法治"), "missing parsed course 思想道德与法治: " + parsedCourses);
        assertTrue(parsedCourses.contains("程序设计荣誉课程"), "missing parsed course 程序设计荣誉课程: " + parsedCourses);
        assertTrue(parsedCourses.contains("中国近现代史纲要"), "missing parsed course 中国近现代史纲要: " + parsedCourses);
        assertTrue(parsedCourses.contains("英语演讲"), "missing parsed course 英语演讲: " + parsedCourses);
    }

    @Test
    void shouldExportRealAcademicAnalysisResponseToJsonWhenLocalFileExists() throws IOException {
        Path curriculumPath = Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize();
        Path transcriptPath = findLocalRealTranscriptPdf();
        assumeTrue(transcriptPath != null, "local regression file not present");

        CurriculumService.CurriculumDefinition definition = curriculumDefinitionParser.parseExcelDefinition(curriculumPath);
        List<AcademicRecord> records = transcriptParsingService.parse(
                transcriptPath,
                transcriptPath.getFileName().toString(),
                1L,
                definition).getRecords();
        analysisEngine.enrichRecords(records, definition);

        AcademicAnalysisEngine.AnalysisSnapshot snapshot =
                analysisEngine.analyze(records, definition, LocalDate.of(2026, 6, 8));
        assertTrue(snapshot.getMatchedCourseCount() > 0,
                "real transcript matched 0 courses, unmatched=" + snapshot.getUnmatchedTranscriptCourses());

        AcademicService.AnalysisVO responseData = AcademicService.AnalysisVO.builder()
                .transcript(buildTranscriptInfo(transcriptPath))
                .metricLabel(snapshot.getMetricLabel())
                .preciseCredits(snapshot.isPreciseCredits())
                .totalCredits(snapshot.getTotalCredits())
                .earnedCredits(snapshot.getEarnedCredits())
                .gapCredits(snapshot.getGapCredits())
                .modules(snapshot.getModules().stream()
                        .map(item -> AcademicService.ModuleProgress.builder()
                                .key(item.getKey())
                                .title(item.getTitle())
                                .requiredCredits(item.getRequiredCredits())
                                .earnedCredits(item.getEarnedCredits())
                                .percent(item.getPercent())
                                .gapCredits(item.getGapCredits())
                                .build())
                        .toList())
                .missingRequiredCourses(snapshot.getMissingRequiredCourses().stream()
                        .map(item -> AcademicService.MissingCourse.builder()
                                .course(item.getCourse())
                                .reason(item.getReason())
                                .module(item.getModule())
                                .offeredTerm(item.getOfferedTerm())
                                .availableThisTerm(item.isAvailableThisTerm())
                                .build())
                        .toList())
                .unfinishedGroups(snapshot.getUnfinishedGroups())
                .recommendedCourses(snapshot.getRecommendedCourses())
                .semesterContext(snapshot.getSemesterContext())
                .matchedCourseCount(snapshot.getMatchedCourseCount())
                .unmatchedTranscriptCourses(snapshot.getUnmatchedTranscriptCourses())
                .risks(snapshot.getRisks())
                .suggestions(snapshot.getSuggestions())
                .build();

        Result<AcademicService.AnalysisVO> result = Result.success(responseData);
        Path outputPath = Path.of("../file/学业分析真实后端输出.json").normalize();

        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(outputPath.toFile(), result);

        assertTrue(Files.exists(outputPath), "analysis export file not created");
        assertTrue(Files.size(outputPath) > 0, "analysis export file is empty");
    }

    @Test
    void shouldValidateSampleCurriculumHeaderAgainstItself() {
        Path curriculumPath = Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize();
        assertDoesNotThrow(() -> curriculumDefinitionParser.validateCompatibleWithSampleFormat(curriculumPath, curriculumPath));
    }

    @Test
    void shouldRejectCurriculumWhenHeaderFormatDiffersFromSample() throws Exception {
        Path tempFile = Files.createTempFile("bad-curriculum", ".xlsx");
        Files.writeString(tempFile, "not-real-excel");
        try {
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> curriculumDefinitionParser.validateCompatibleWithSampleFormat(
                            tempFile,
                            Path.of("../file/培养方案示例/培养方案示例.xlsx").normalize()));
            assertTrue(error.getMessage().contains("培养方案格式错误")
                    || error.getMessage().contains("读取培养方案表头失败"));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path findLocalRealTranscriptPdf() {
        Path fileDir = Path.of("../file").normalize();
        if (!Files.isDirectory(fileDir)) {
            return null;
        }
        try (Stream<Path> paths = Files.list(fileDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("\\d+\\.pdf"))
                    .sorted(Comparator.comparingLong(this::timestampFromFileName).reversed())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private long timestampFromFileName(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        try {
            return Long.parseLong(stem);
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private AcademicService.TranscriptInfo buildTranscriptInfo(Path transcriptPath) throws IOException {
        String fileName = transcriptPath.getFileName().toString();
        String stem = fileName.substring(0, fileName.lastIndexOf('.'));
        String uploadedAt = Files.getLastModifiedTime(transcriptPath)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .toString();
        return AcademicService.TranscriptInfo.builder()
                .fileId("local-regression-" + stem)
                .fileName(fileName)
                .uploadedAt(uploadedAt)
                .parsed(true)
                .build();
    }

}
