package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AcademicRecord;
import lombok.Builder;
import lombok.Data;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranscriptParsingService {

    private static final Pattern PDF_LINE_PATTERN = Pattern.compile(
            "^(.+?)\\s+([0-9]+(?:\\.[0-9]+)?)\\s+([0-9]{2,3}(?:\\.[0-9]+)?|合格|通过)?\\s*(通识课程|专业必修|专业选修|实践环节|未分类)?\\s*(20\\d{2}[-—]?[12])?$");

    public ParseResult parse(Path filePath, String originalFileName, Long userId) {
        String extension = getExtension(originalFileName);
        List<AcademicRecord> records;
        if (".xlsx".equals(extension) || ".xls".equals(extension)) {
            records = parseExcel(filePath, userId);
        } else if (".pdf".equals(extension)) {
            records = parsePdf(filePath, userId);
        } else {
            throw new RuntimeException("仅支持 PDF/Excel 成绩单");
        }

        if (records.isEmpty()) {
            throw new RuntimeException("成绩单解析成功，但未识别到课程数据");
        }
        return ParseResult.builder()
                .records(records)
                .message("解析成功，共识别 " + records.size() + " 门课程")
                .build();
    }

    private List<AcademicRecord> parseExcel(Path filePath, Long userId) {
        try (InputStream inputStream = Files.newInputStream(filePath); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = buildHeaderMap(sheet.getRow(sheet.getFirstRowNum()), formatter);
            List<AcademicRecord> records = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String courseName = getCell(headers, row, formatter, "课程名", "课程", "课程名称");
                if (!StringUtils.hasText(courseName)) {
                    continue;
                }
                records.add(buildRecord(
                        userId,
                        courseName,
                        getCell(headers, row, formatter, "学分"),
                        getCell(headers, row, formatter, "成绩", "分数"),
                        defaultIfBlank(getCell(headers, row, formatter, "类别", "模块"), "未分类"),
                        getCell(headers, row, formatter, "学期", "semester")));
            }
            return records;
        } catch (IOException e) {
            throw new RuntimeException("解析 Excel 成绩单失败");
        }
    }

    private List<AcademicRecord> parsePdf(Path filePath, Long userId) {
        String text;
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            text = new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new RuntimeException("解析 PDF 成绩单失败");
        }
        if (!StringUtils.hasText(text)) {
            throw new RuntimeException("解析失败：文件内容无法识别，请确认上传的是文本型 PDF 成绩单");
        }
        List<AcademicRecord> records = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            Matcher matcher = PDF_LINE_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            records.add(buildRecord(
                    userId,
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    defaultIfBlank(matcher.group(4), "未分类"),
                    matcher.group(5)));
        }
        if (records.isEmpty()) {
            throw new RuntimeException("解析失败：文件内容无法识别，请确认上传的是规范的文本型 PDF 成绩单");
        }
        return records;
    }

    private AcademicRecord buildRecord(Long userId,
            String courseName,
            String credits,
            String score,
            String category,
            String semester) {
        AcademicRecord record = new AcademicRecord();
        record.setUserId(userId);
        record.setCourseName(courseName.trim());
        record.setCredits(parseDecimal(credits));
        record.setScore(parseScore(score));
        record.setCategory(category == null ? "未分类" : category.trim());
        record.setSemester(StringUtils.hasText(semester) ? semester.trim() : null);
        return record;
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headers = new HashMap<>();
        if (headerRow == null) {
            return headers;
        }
        for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {
            headers.put(formatter.formatCellValue(headerRow.getCell(i)).trim(), i);
        }
        return headers;
    }

    private String getCell(Map<String, Integer> headers, Row row, DataFormatter formatter, String... names) {
        for (String name : names) {
            Integer index = headers.get(name);
            if (index != null && row.getCell(index) != null) {
                return formatter.formatCellValue(row.getCell(index)).trim();
            }
        }
        return "";
    }

    private BigDecimal parseDecimal(String input) {
        if (!StringUtils.hasText(input)) {
            return BigDecimal.ZERO;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(input.trim()));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseScore(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(input.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new RuntimeException("文件缺少后缀名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    @Data
    @Builder
    public static class ParseResult {
        private List<AcademicRecord> records;
        private String message;
    }
}
