package cn.edu.ruc.info.service;

import cn.edu.ruc.info.util.JsonUtils;
import lombok.Data;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CurriculumDefinitionParser {

    private static final Pattern CHOOSE_PATTERN = Pattern.compile("([一二三四五六七八九十0-9]+)选([一二三四五六七八九十0-9]+)");
    private static final Pattern AT_LEAST_PATTERN = Pattern.compile("至少选修([一二三四五六七八九十0-9]+)门");

    private final JsonUtils jsonUtils;

    public CurriculumDefinitionParser(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public CurriculumService.CurriculumDefinition loadOrParse(Path excelPath) {
        Path processedPath = getProcessedPath(excelPath);
        if (Files.exists(processedPath)) {
            try {
                String json = Files.readString(processedPath, StandardCharsets.UTF_8);
                CurriculumService.CurriculumDefinition definition = jsonUtils.fromJson(
                        json,
                        CurriculumService.CurriculumDefinition.class);
                normalizeDefinition(definition);
                validateDefinition(definition);
                return definition;
            } catch (RuntimeException | IOException ignored) {
            }
        }
        CurriculumService.CurriculumDefinition definition = parseExcelDefinition(excelPath);
        writeProcessedDefinition(excelPath, definition);
        return definition;
    }

    public CurriculumService.CurriculumDefinition parseExcelDefinition(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("培养方案文件为空");
            }
            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(sheet.getFirstRowNum()), formatter);
            String currentModule = "";
            String currentRuleText = "";
            GroupBuilder activeGroup = null;

            List<CurriculumService.RequiredCourse> courseCatalog = new ArrayList<>();
            List<CurriculumService.RequirementGroup> requirementGroups = new ArrayList<>();
            Map<String, ModuleAccumulator> modules = new LinkedHashMap<>();
            boolean preciseCredits = true;

            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String moduleCell = getCell(row, headerMap, formatter, "大类", "模块", "类别");
                String ruleCell = getCell(row, headerMap, formatter, "性质", "规则");
                String courseName = getCell(row, headerMap, formatter, "课程名", "课程", "课程名称");
                if (!StringUtils.hasText(courseName)) {
                    continue;
                }
                if (StringUtils.hasText(moduleCell)) {
                    currentModule = moduleCell.trim();
                }
                if (StringUtils.hasText(ruleCell)) {
                    currentRuleText = ruleCell.trim();
                }
                String module = StringUtils.hasText(currentModule) ? currentModule : "未分类";
                String ruleText = StringUtils.hasText(currentRuleText) ? currentRuleText : "必修";

                boolean startNewGroup = activeGroup == null
                        || StringUtils.hasText(moduleCell)
                        || StringUtils.hasText(ruleCell);
                if (startNewGroup) {
                    if (activeGroup != null) {
                        requirementGroups.add(activeGroup.build());
                    }
                    RuleDescriptor descriptor = parseRule(ruleText);
                    activeGroup = new GroupBuilder(module, ruleText, descriptor, requirementGroups.size() + 1);
                }

                String offeredTerm = getCell(row, headerMap, formatter, "开设时间", "开课时间", "建议学期");
                String creditCell = getCell(row, headerMap, formatter, "学分", "credits", "credit");
                Double explicitCredits = parseOptionalDouble(creditCell);
                boolean courseHasPreciseCredit = explicitCredits != null && explicitCredits > 0;
                if (!courseHasPreciseCredit) {
                    preciseCredits = false;
                }
                double unitCredits = courseHasPreciseCredit ? explicitCredits : 1.0;

                CurriculumService.RequiredCourse course = CurriculumService.RequiredCourse.builder()
                        .courseName(courseName.trim())
                        .module(module)
                        .moduleKey(toKey(module))
                        .groupKey(activeGroup.getGroupKey())
                        .credits(unitCredits)
                        .required("ALL_REQUIRED".equals(activeGroup.getRuleType()))
                        .ruleText(ruleText)
                        .ruleType(activeGroup.getRuleType())
                        .groupRequiredCount(activeGroup.getRequiredCount())
                        .offeredTerm(offeredTerm)
                        .offeredTermCodes(parseOfferedTermCodes(offeredTerm))
                        .normalizedName(normalizeCourseName(courseName))
                        .build();
                courseCatalog.add(course);
                activeGroup.addCourse(course);
            }
            if (activeGroup != null) {
                requirementGroups.add(activeGroup.build());
            }

            for (CurriculumService.RequirementGroup group : requirementGroups) {
                ModuleAccumulator accumulator = modules.computeIfAbsent(
                        group.getModuleKey(),
                        key -> new ModuleAccumulator(group.getModuleKey(), group.getModuleTitle()));
                accumulator.add(group);
            }

            CurriculumService.CurriculumDefinition definition = CurriculumService.CurriculumDefinition.builder()
                    .programName(stripExtension(path.getFileName().toString()))
                    .version(LocalDateTime.now().toString())
                    .preciseCredits(preciseCredits)
                    .metricLabel(preciseCredits ? "学分" : "课程要求单元")
                    .requiredModules(modules.values().stream()
                            .map(ModuleAccumulator::build)
                            .collect(Collectors.toList()))
                    .requirementGroups(requirementGroups)
                    .requiredCourses(courseCatalog)
                    .build();
            validateDefinition(definition);
            return definition;
        } catch (IOException e) {
            throw new RuntimeException("解析培养方案 Excel 失败");
        }
    }

    public void writeProcessedDefinition(Path excelPath, CurriculumService.CurriculumDefinition definition) {
        Path processedPath = getProcessedPath(excelPath);
        try {
            Files.writeString(processedPath, jsonUtils.toJson(definition), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入培养方案结构化文件失败");
        }
    }

    public Path getProcessedPath(Path excelPath) {
        String fileName = excelPath.getFileName() == null ? "curriculum" : excelPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return excelPath.resolveSibling(baseName + ".processed.json");
    }

    private void validateDefinition(CurriculumService.CurriculumDefinition definition) {
        if (definition == null
                || definition.getRequiredModules() == null
                || definition.getRequirementGroups() == null
                || definition.getRequiredCourses() == null
                || definition.getRequiredModules().isEmpty()
                || definition.getRequirementGroups().isEmpty()
                || definition.getRequiredCourses().isEmpty()) {
            throw new RuntimeException("培养方案内容不完整");
        }
    }

    private void normalizeDefinition(CurriculumService.CurriculumDefinition definition) {
        if (definition == null) {
            return;
        }
        if (definition.getRequiredCourses() != null) {
            for (CurriculumService.RequiredCourse course : definition.getRequiredCourses()) {
                if (course == null) {
                    continue;
                }
                if (!StringUtils.hasText(course.getNormalizedName())) {
                    course.setNormalizedName(normalizeCourseName(course.getCourseName()));
                }
                if (!StringUtils.hasText(course.getModuleKey())) {
                    course.setModuleKey(toKey(course.getModule()));
                }
                if (course.getOfferedTermCodes() == null) {
                    course.setOfferedTermCodes(parseOfferedTermCodes(course.getOfferedTerm()));
                }
            }
        }
        if (definition.getRequirementGroups() != null) {
            for (CurriculumService.RequirementGroup group : definition.getRequirementGroups()) {
                if (group == null || group.getCourses() == null) {
                    continue;
                }
                for (CurriculumService.RequiredCourse course : group.getCourses()) {
                    if (course == null) {
                        continue;
                    }
                    if (!StringUtils.hasText(course.getNormalizedName())) {
                        course.setNormalizedName(normalizeCourseName(course.getCourseName()));
                    }
                    if (!StringUtils.hasText(course.getModuleKey())) {
                        course.setModuleKey(toKey(course.getModule()));
                    }
                    if (course.getOfferedTermCodes() == null) {
                        course.setOfferedTermCodes(parseOfferedTermCodes(course.getOfferedTerm()));
                    }
                }
            }
        }
    }

    private Map<String, Integer> buildHeaderMap(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> headerMap = new HashMap<>();
        if (headerRow == null) {
            return headerMap;
        }
        for (int i = headerRow.getFirstCellNum(); i < headerRow.getLastCellNum(); i++) {
            headerMap.put(formatter.formatCellValue(headerRow.getCell(i)).trim(), i);
        }
        return headerMap;
    }

    private String getCell(Row row, Map<String, Integer> headerMap, DataFormatter formatter, String... keys) {
        for (String key : keys) {
            Integer index = headerMap.get(key);
            if (index != null && row.getCell(index) != null) {
                return formatter.formatCellValue(row.getCell(index)).trim();
            }
        }
        return "";
    }

    private RuleDescriptor parseRule(String ruleText) {
        String text = StringUtils.hasText(ruleText) ? ruleText.trim() : "必修";
        if ("必修".equals(text)) {
            return new RuleDescriptor("ALL_REQUIRED", 0);
        }
        if ("任选一门".equals(text)) {
            return new RuleDescriptor("CHOOSE_N", 1);
        }
        Matcher chooseMatcher = CHOOSE_PATTERN.matcher(text);
        if (chooseMatcher.find()) {
            return new RuleDescriptor("CHOOSE_N", parseChineseNumber(chooseMatcher.group(2), 1));
        }
        Matcher atLeastMatcher = AT_LEAST_PATTERN.matcher(text);
        if (atLeastMatcher.find()) {
            return new RuleDescriptor("AT_LEAST_N", parseChineseNumber(atLeastMatcher.group(1), 1));
        }
        return new RuleDescriptor("ALL_REQUIRED", 0);
    }

    private int parseChineseNumber(String text, int defaultValue) {
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        String trimmed = text.trim();
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
        }
        return switch (trimmed) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> defaultValue;
        };
    }

    private Double parseOptionalDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> parseOfferedTermCodes(String offeredTerm) {
        if (!StringUtils.hasText(offeredTerm)) {
            return List.of();
        }
        String raw = offeredTerm.replaceAll("\\s+", "");
        Set<String> codes = new LinkedHashSet<>();
        if (raw.contains("秋") || raw.contains("上")) {
            codes.add("AUTUMN");
        }
        if (raw.contains("春") || raw.contains("下")) {
            codes.add("SPRING");
        }
        if (raw.contains("夏")) {
            codes.add("SUMMER");
        }

        if (raw.contains("大一")) {
            codes.add("YEAR_1");
        }
        if (raw.contains("大二")) {
            codes.add("YEAR_2");
        }
        if (raw.contains("大三")) {
            codes.add("YEAR_3");
        }
        if (raw.contains("大四")) {
            codes.add("YEAR_4");
        }
        return new ArrayList<>(codes);
    }

    private String normalizeCourseName(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        return input.trim()
                .replace("Ⅰ", "I")
                .replace("Ⅱ", "II")
                .replace("Ⅲ", "III")
                .replace("Ⅳ", "IV")
                .replace("Ⅴ", "V")
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("[\\s\\-—_/（）()【】\\[\\]·、,，.:：;；'\"`]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String toKey(String text) {
        return normalizeCourseName(text);
    }

    private String stripExtension(String fileName) {
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    private record RuleDescriptor(String type, int requiredCount) {
    }

    @Data
    private static class GroupBuilder {
        private final String moduleTitle;
        private final String ruleText;
        private final String ruleType;
        private final int requiredCount;
        private final String groupKey;
        private final List<CurriculumService.RequiredCourse> courses = new ArrayList<>();

        private GroupBuilder(String moduleTitle, String ruleText, RuleDescriptor descriptor, int sequence) {
            this.moduleTitle = moduleTitle;
            this.ruleText = ruleText;
            this.ruleType = descriptor.type();
            this.requiredCount = descriptor.requiredCount();
            this.groupKey = toGroupKey(moduleTitle, sequence);
        }

        public void addCourse(CurriculumService.RequiredCourse course) {
            courses.add(course);
        }

        public CurriculumService.RequirementGroup build() {
            int effectiveRequiredCount = "ALL_REQUIRED".equals(ruleType) ? courses.size() : Math.max(1, requiredCount);
            List<Double> availableCredits = courses.stream()
                    .map(CurriculumService.RequiredCourse::getCredits)
                    .filter(value -> value != null && value > 0)
                    .toList();
            double unitCredits = availableCredits.isEmpty() ? 1.0 : availableCredits.get(0);
            double requiredCredits = "ALL_REQUIRED".equals(ruleType)
                    ? courses.stream()
                            .map(CurriculumService.RequiredCourse::getCredits)
                            .filter(value -> value != null && value > 0)
                            .mapToDouble(Double::doubleValue)
                            .sum()
                    : effectiveRequiredCount * unitCredits;
            if (requiredCredits <= 0) {
                requiredCredits = "ALL_REQUIRED".equals(ruleType) ? courses.size() : effectiveRequiredCount;
            }
            return CurriculumService.RequirementGroup.builder()
                    .key(groupKey)
                    .moduleKey(normalizeModuleKey(moduleTitle))
                    .moduleTitle(moduleTitle)
                    .ruleText(ruleText)
                    .ruleType(ruleType)
                    .requiredCount(effectiveRequiredCount)
                    .candidateCount(courses.size())
                    .requiredCredits(requiredCredits)
                    .courses(new ArrayList<>(courses))
                    .build();
        }

        private static String toGroupKey(String moduleTitle, int sequence) {
            return normalizeModuleKey(moduleTitle) + "-group-" + sequence;
        }

        private static String normalizeModuleKey(String moduleTitle) {
            return moduleTitle == null ? "" : moduleTitle.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        }
    }

    private static class ModuleAccumulator {
        private final String key;
        private final String title;
        private double requiredCredits;
        private int requirementGroupCount;
        private int courseEntryCount;

        private ModuleAccumulator(String key, String title) {
            this.key = key;
            this.title = title;
        }

        private void add(CurriculumService.RequirementGroup group) {
            requiredCredits += group.getRequiredCredits() == null ? 0.0 : group.getRequiredCredits();
            requirementGroupCount++;
            courseEntryCount += group.getCourses() == null ? 0 : group.getCourses().size();
        }

        private CurriculumService.RequiredModule build() {
            return CurriculumService.RequiredModule.builder()
                    .key(key)
                    .title(title)
                    .requiredCredits(requiredCredits)
                    .requirementGroupCount(requirementGroupCount)
                    .courseEntryCount(courseEntryCount)
                    .build();
        }
    }
}
