package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AcademicRecord;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AcademicAnalysisEngine {

    public AnalysisSnapshot analyze(List<AcademicRecord> records,
            CurriculumService.CurriculumDefinition definition,
            LocalDate currentDate) {
        if (definition == null) {
            throw new RuntimeException("尚未上传培养方案");
        }
        SemesterContext semesterContext = resolveSemesterContext(currentDate);
        Map<String, CurriculumService.RequiredCourse> matchedCourses = matchRecords(records, definition.getRequiredCourses());

        List<String> unmatchedTranscriptCourses = records.stream()
                .map(AcademicRecord::getCourseName)
                .filter(StringUtils::hasText)
                .filter(name -> !matchedCourses.containsKey(normalize(name)))
                .distinct()
                .collect(Collectors.toList());

        Map<String, GroupEvaluation> groupEvaluations = new LinkedHashMap<>();
        List<MissingCourseItem> missingRequiredCourses = new ArrayList<>();
        List<UnfinishedGroupItem> unfinishedGroups = new ArrayList<>();
        Map<String, RecommendationItem> recommendationMap = new LinkedHashMap<>();

        for (CurriculumService.RequirementGroup group : definition.getRequirementGroups()) {
            List<CurriculumService.RequiredCourse> completedCourses = group.getCourses().stream()
                    .filter(course -> matchedCourses.containsValue(course))
                    .collect(Collectors.toList());
            double earnedCredits = calculateEarnedCredits(group, completedCourses, matchedCourses, definition);
            int completedCount = completedCourses.size();
            int requiredCount = group.getRequiredCount() == null ? 0 : group.getRequiredCount();
            int gapCount = Math.max(0, requiredCount - completedCount);
            boolean completed = gapCount == 0;

            if ("ALL_REQUIRED".equals(group.getRuleType())) {
                for (CurriculumService.RequiredCourse course : group.getCourses()) {
                    if (!isMatched(course, matchedCourses)) {
                        missingRequiredCourses.add(MissingCourseItem.builder()
                                .course(course.getCourseName())
                                .module(course.getModule())
                                .offeredTerm(course.getOfferedTerm())
                                .availableThisTerm(isCourseOfferedIn(course, semesterContext.getCurrentTermCode()))
                                .reason(course.getModule() + " 必修未完成")
                                .build());
                    }
                }
            }

            if (!completed) {
                List<CurriculumService.RequiredCourse> candidateCourses = group.getCourses().stream()
                        .filter(course -> !isMatched(course, matchedCourses))
                        .sorted(courseRecommendationComparator(semesterContext))
                        .collect(Collectors.toList());
                unfinishedGroups.add(UnfinishedGroupItem.builder()
                        .groupKey(group.getKey())
                        .moduleTitle(group.getModuleTitle())
                        .ruleText(group.getRuleText())
                        .requiredCount(requiredCount)
                        .completedCount(Math.min(completedCount, requiredCount))
                        .gapCount(gapCount)
                        .availableThisTermCount((int) candidateCourses.stream()
                                .filter(course -> isCourseOfferedIn(course, semesterContext.getCurrentTermCode()))
                                .count())
                        .candidateCourses(candidateCourses.stream()
                                .map(CurriculumService.RequiredCourse::getCourseName)
                                .collect(Collectors.toList()))
                        .build());

                candidateCourses.stream()
                        .limit(Math.max(3, gapCount))
                        .forEach(course -> recommendationMap.putIfAbsent(
                                course.getNormalizedName(),
                                buildRecommendation(course, group, semesterContext)));
            }

            groupEvaluations.put(group.getKey(), GroupEvaluation.builder()
                    .groupKey(group.getKey())
                    .moduleKey(group.getModuleKey())
                    .earnedCredits(earnedCredits)
                    .requiredCredits(safe(group.getRequiredCredits()))
                    .completedCount(Math.min(completedCount, requiredCount))
                    .requiredCount(requiredCount)
                    .completed(completed)
                    .build());
        }

        List<ModuleProgressItem> modules = definition.getRequiredModules().stream()
                .map(module -> {
                    List<GroupEvaluation> evaluations = groupEvaluations.values().stream()
                            .filter(item -> module.getKey().equals(item.getModuleKey()))
                            .collect(Collectors.toList());
                    double requiredCredits = safe(module.getRequiredCredits());
                    double earnedCredits = evaluations.stream()
                            .mapToDouble(GroupEvaluation::getEarnedCredits)
                            .sum();
                    double gapCredits = Math.max(0, requiredCredits - earnedCredits);
                    int percent = requiredCredits <= 0 ? 100
                            : (int) Math.min(100, Math.round(earnedCredits * 100 / requiredCredits));
                    return ModuleProgressItem.builder()
                            .key(module.getKey())
                            .title(module.getTitle())
                            .requiredCredits(round(requiredCredits))
                            .earnedCredits(round(earnedCredits))
                            .gapCredits(round(gapCredits))
                            .percent(percent)
                            .requirementGroupCount(module.getRequirementGroupCount() == null ? 0 : module.getRequirementGroupCount())
                            .build();
                })
                .sorted(Comparator.comparingDouble(ModuleProgressItem::getGapCredits).reversed()
                        .thenComparing(ModuleProgressItem::getTitle))
                .collect(Collectors.toList());

        double totalCredits = modules.stream().mapToDouble(ModuleProgressItem::getRequiredCredits).sum();
        double earnedCredits = modules.stream().mapToDouble(ModuleProgressItem::getEarnedCredits).sum();
        double gapCredits = Math.max(0, totalCredits - earnedCredits);

        List<RecommendationItem> recommendedCourses = recommendationMap.values().stream()
                .sorted(Comparator.comparingInt(RecommendationItem::getPriority)
                        .thenComparing(RecommendationItem::getModule)
                        .thenComparing(RecommendationItem::getCourse))
                .limit(10)
                .collect(Collectors.toList());

        List<String> risks = buildRisks(definition, modules, missingRequiredCourses, unfinishedGroups, gapCredits);
        List<String> suggestions = buildSuggestions(definition,
                semesterContext,
                missingRequiredCourses,
                unfinishedGroups,
                recommendedCourses,
                modules);

        return AnalysisSnapshot.builder()
                .metricLabel(defaultMetricLabel(definition))
                .preciseCredits(Boolean.TRUE.equals(definition.getPreciseCredits()))
                .totalCredits(round(totalCredits))
                .earnedCredits(round(earnedCredits))
                .gapCredits(round(gapCredits))
                .modules(modules)
                .missingRequiredCourses(missingRequiredCourses)
                .unfinishedGroups(unfinishedGroups)
                .recommendedCourses(recommendedCourses)
                .risks(risks)
                .suggestions(suggestions)
                .semesterContext(semesterContext)
                .matchedCourseCount(matchedCourses.size())
                .unmatchedTranscriptCourses(unmatchedTranscriptCourses)
                .build();
    }

    public List<AcademicRecord> enrichRecords(List<AcademicRecord> records, CurriculumService.CurriculumDefinition definition) {
        if (definition == null || records == null || records.isEmpty()) {
            return records == null ? List.of() : records;
        }
        Map<String, CurriculumService.RequiredCourse> matchedCourses = matchRecords(records, definition.getRequiredCourses());
        for (AcademicRecord record : records) {
            CurriculumService.RequiredCourse course = matchedCourses.get(normalize(record.getCourseName()));
            if (course == null) {
                continue;
            }
            // Persist the curriculum-standard course name so later comparison does not depend on raw transcript layout.
            record.setCourseName(course.getCourseName());
            if (!StringUtils.hasText(record.getCategory()) || "未分类".equals(record.getCategory())) {
                record.setCategory(course.getModule());
            }
            if ((record.getCredits() == null || BigDecimal.ZERO.compareTo(record.getCredits()) == 0)
                    && course.getCredits() != null
                    && course.getCredits() > 0) {
                record.setCredits(BigDecimal.valueOf(course.getCredits()));
            }
        }
        return records;
    }

    private List<String> buildRisks(CurriculumService.CurriculumDefinition definition,
            List<ModuleProgressItem> modules,
            List<MissingCourseItem> missingRequiredCourses,
            List<UnfinishedGroupItem> unfinishedGroups,
            double gapCredits) {
        List<String> risks = new ArrayList<>();
        String metricLabel = defaultMetricLabel(definition);
        if (gapCredits > 0) {
            risks.add("仍有 " + trimTrailingZero(gapCredits) + " " + metricLabel + " 未完成");
        }
        if (!missingRequiredCourses.isEmpty()) {
            risks.add("仍有 " + missingRequiredCourses.size() + " 门必修课未修");
        }
        long unfinishedModuleCount = modules.stream().filter(module -> module.getGapCredits() > 0).count();
        if (unfinishedModuleCount > 0) {
            risks.add("仍有 " + unfinishedModuleCount + " 个培养模块未达标");
        }
        if (!unfinishedGroups.isEmpty()) {
            risks.add("仍有 " + unfinishedGroups.size() + " 个范围选课规则未满足");
        }
        if (risks.isEmpty()) {
            risks.add("当前培养方案匹配情况良好");
        }
        return risks;
    }

    private List<String> buildSuggestions(CurriculumService.CurriculumDefinition definition,
            SemesterContext semesterContext,
            List<MissingCourseItem> missingRequiredCourses,
            List<UnfinishedGroupItem> unfinishedGroups,
            List<RecommendationItem> recommendedCourses,
            List<ModuleProgressItem> modules) {
        List<String> suggestions = new ArrayList<>();
        if (!missingRequiredCourses.isEmpty()) {
            String highPriority = missingRequiredCourses.stream()
                    .limit(4)
                    .map(MissingCourseItem::getCourse)
                    .collect(Collectors.joining("、"));
            suggestions.add("优先补齐必修课：" + highPriority);
        }
        List<RecommendationItem> currentTerm = recommendedCourses.stream()
                .filter(RecommendationItem::isAvailableThisTerm)
                .limit(4)
                .collect(Collectors.toList());
        if (!currentTerm.isEmpty()) {
            suggestions.add("按当前时间 " + semesterContext.getCurrentLabel()
                    + "，本学期可优先考虑：" + currentTerm.stream()
                            .map(RecommendationItem::getCourse)
                            .collect(Collectors.joining("、")));
        }
        List<RecommendationItem> nextTerm = recommendedCourses.stream()
                .filter(item -> semesterContext.getNextLabel().equals(item.getRecommendedFor()))
                .limit(4)
                .collect(Collectors.toList());
        if (currentTerm.isEmpty() && !nextTerm.isEmpty()) {
            suggestions.add("若本学期无法补修，可提前规划 " + semesterContext.getNextLabel()
                    + "："
                    + nextTerm.stream().map(RecommendationItem::getCourse).collect(Collectors.joining("、")));
        }
        modules.stream()
                .filter(module -> module.getGapCredits() > 0)
                .findFirst()
                .ifPresent(module -> suggestions.add("缺口最大的模块是“" + module.getTitle() + "”，建议优先覆盖该模块课程"));
        if (suggestions.isEmpty()) {
            suggestions.add("当前培养方案匹配情况良好，继续按既定计划选课即可");
        }
        if (!Boolean.TRUE.equals(definition.getPreciseCredits())) {
            suggestions.add("当前样例培养方案未提供逐门课程学分，分析结果按课程规则单元估算，正式上线建议补充学分列");
        }
        return suggestions;
    }

    private RecommendationItem buildRecommendation(CurriculumService.RequiredCourse course,
            CurriculumService.RequirementGroup group,
            SemesterContext semesterContext) {
        boolean availableThisTerm = isCourseOfferedIn(course, semesterContext.getCurrentTermCode());
        boolean availableNextTerm = isCourseOfferedIn(course, semesterContext.getNextTermCode());
        int priority = availableThisTerm ? 0 : availableNextTerm ? 1 : 2;
        String recommendedFor = availableThisTerm
                ? semesterContext.getCurrentLabel()
                : availableNextTerm ? semesterContext.getNextLabel() : fallbackRecommendationLabel(course);
        String reason;
        if ("ALL_REQUIRED".equals(group.getRuleType())) {
            reason = group.getModuleTitle() + " 必修未完成";
        } else {
            reason = group.getModuleTitle() + " " + group.getRuleText() + "，还需补 " + Math.max(1, group.getRequiredCount() == null ? 1 : group.getRequiredCount()) + " 门";
        }
        return RecommendationItem.builder()
                .course(course.getCourseName())
                .module(course.getModule())
                .ruleText(group.getRuleText())
                .offeredTerm(course.getOfferedTerm())
                .recommendedFor(recommendedFor)
                .availableThisTerm(availableThisTerm)
                .reason(reason)
                .priority(priority)
                .build();
    }

    private String fallbackRecommendationLabel(CurriculumService.RequiredCourse course) {
        return StringUtils.hasText(course.getOfferedTerm()) ? course.getOfferedTerm() : "开课时间待确认";
    }

    private double calculateEarnedCredits(CurriculumService.RequirementGroup group,
            List<CurriculumService.RequiredCourse> completedCourses,
            Map<String, CurriculumService.RequiredCourse> matchedCourses,
            CurriculumService.CurriculumDefinition definition) {
        List<Double> completedCreditValues = completedCourses.stream()
                .map(course -> courseCreditOf(course, definition))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        if ("ALL_REQUIRED".equals(group.getRuleType())) {
            return round(Math.min(
                    safe(group.getRequiredCredits()),
                    completedCreditValues.stream().mapToDouble(Double::doubleValue).sum()));
        }
        int requiredCount = group.getRequiredCount() == null ? 0 : group.getRequiredCount();
        double total = 0.0;
        for (int i = 0; i < completedCreditValues.size() && i < requiredCount; i++) {
            total += completedCreditValues.get(i);
        }
        return round(Math.min(safe(group.getRequiredCredits()), total));
    }

    private double courseCreditOf(CurriculumService.RequiredCourse course, CurriculumService.CurriculumDefinition definition) {
        if (course == null) {
            return 0.0;
        }
        if (course.getCredits() != null && course.getCredits() > 0) {
            return course.getCredits();
        }
        return Boolean.TRUE.equals(definition.getPreciseCredits()) ? 0.0 : 1.0;
    }

    private Map<String, CurriculumService.RequiredCourse> matchRecords(List<AcademicRecord> records,
            List<CurriculumService.RequiredCourse> requiredCourses) {
        Map<String, CurriculumService.RequiredCourse> matches = new LinkedHashMap<>();
        Set<String> claimedRequirementKeys = new LinkedHashSet<>();
        for (AcademicRecord record : records) {
            String normalizedRecordName = normalize(record.getCourseName());
            if (!StringUtils.hasText(normalizedRecordName)) {
                continue;
            }
            CurriculumService.RequiredCourse matchedCourse = findMatchingCourse(normalizedRecordName, requiredCourses, claimedRequirementKeys);
            if (matchedCourse != null) {
                matches.put(normalizedRecordName, matchedCourse);
                claimedRequirementKeys.add(matchedCourse.getNormalizedName());
            }
        }
        return matches;
    }

    private CurriculumService.RequiredCourse findMatchingCourse(String normalizedRecordName,
            List<CurriculumService.RequiredCourse> requiredCourses,
            Set<String> claimedRequirementKeys) {
        Optional<CurriculumService.RequiredCourse> exactMatch = requiredCourses.stream()
                .filter(course -> !claimedRequirementKeys.contains(course.getNormalizedName()))
                .filter(course -> normalizedRecordName.equals(course.getNormalizedName()))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        CurriculumService.RequiredCourse containedMatch = findBestContainedMatch(
                normalizedRecordName,
                requiredCourses,
                claimedRequirementKeys);
        if (containedMatch != null) {
            return containedMatch;
        }

        List<CurriculumService.RequiredCourse> prefixMatches = requiredCourses.stream()
                .filter(course -> !claimedRequirementKeys.contains(course.getNormalizedName()))
                .filter(course -> isRelaxedMatch(normalizedRecordName, course.getNormalizedName()))
                .sorted(Comparator.comparingInt(course -> relaxedDistance(normalizedRecordName, course.getNormalizedName())))
                .collect(Collectors.toList());
        if (prefixMatches.size() == 1) {
            return prefixMatches.get(0);
        }
        if (!prefixMatches.isEmpty()) {
            int bestDistance = relaxedDistance(normalizedRecordName, prefixMatches.get(0).getNormalizedName());
            List<CurriculumService.RequiredCourse> bestMatches = prefixMatches.stream()
                    .filter(course -> relaxedDistance(normalizedRecordName, course.getNormalizedName()) == bestDistance)
                    .collect(Collectors.toList());
            if (bestMatches.size() == 1) {
                return bestMatches.get(0);
            }
        }
        return null;
    }

    private CurriculumService.RequiredCourse findBestContainedMatch(String normalizedRecordName,
            List<CurriculumService.RequiredCourse> requiredCourses,
            Set<String> claimedRequirementKeys) {
        List<CurriculumService.RequiredCourse> containedMatches = requiredCourses.stream()
                .filter(course -> !claimedRequirementKeys.contains(course.getNormalizedName()))
                .filter(course -> isContainedMatch(normalizedRecordName, course.getNormalizedName()))
                .sorted(Comparator.comparingInt((CurriculumService.RequiredCourse course) -> course.getNormalizedName().length())
                        .reversed())
                .collect(Collectors.toList());
        if (containedMatches.isEmpty()) {
            return null;
        }
        if (containedMatches.size() == 1) {
            return containedMatches.get(0);
        }
        int firstLength = containedMatches.get(0).getNormalizedName().length();
        int secondLength = containedMatches.get(1).getNormalizedName().length();
        if (firstLength > secondLength) {
            return containedMatches.get(0);
        }
        return null;
    }

    private boolean isContainedMatch(String normalizedRecordName, String normalizedCourseName) {
        if (!StringUtils.hasText(normalizedRecordName) || !StringUtils.hasText(normalizedCourseName)) {
            return false;
        }
        if (normalizedRecordName.equals(normalizedCourseName)) {
            return true;
        }
        return normalizedRecordName.contains(normalizedCourseName)
                || normalizedCourseName.contains(normalizedRecordName);
    }

    private boolean isRelaxedMatch(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        if (left.startsWith(right) || right.startsWith(left)) {
            return Math.abs(left.length() - right.length()) <= 2;
        }
        return left.replaceAll("[ab]$", "").equals(right)
                || right.replaceAll("[ab]$", "").equals(left);
    }

    private int relaxedDistance(String left, String right) {
        return Math.abs(left.length() - right.length());
    }

    private boolean isMatched(CurriculumService.RequiredCourse course,
            Map<String, CurriculumService.RequiredCourse> matchedCourses) {
        return matchedCourses.values().stream()
                .anyMatch(matched -> matched.getNormalizedName().equals(course.getNormalizedName()));
    }

    private Comparator<CurriculumService.RequiredCourse> courseRecommendationComparator(SemesterContext semesterContext) {
        return Comparator
                .comparingInt((CurriculumService.RequiredCourse course) -> recommendationPriority(course, semesterContext))
                .thenComparing(course -> StringUtils.hasText(course.getOfferedTerm()) ? course.getOfferedTerm() : "zzz")
                .thenComparing(CurriculumService.RequiredCourse::getCourseName);
    }

    private int recommendationPriority(CurriculumService.RequiredCourse course, SemesterContext semesterContext) {
        if (isCourseOfferedIn(course, semesterContext.getCurrentTermCode())) {
            return 0;
        }
        if (isCourseOfferedIn(course, semesterContext.getNextTermCode())) {
            return 1;
        }
        return 2;
    }

    private boolean isCourseOfferedIn(CurriculumService.RequiredCourse course, String termCode) {
        return course != null
                && course.getOfferedTermCodes() != null
                && course.getOfferedTermCodes().contains(termCode);
    }

    private SemesterContext resolveSemesterContext(LocalDate date) {
        int month = date.getMonthValue();
        boolean spring = month >= 2 && month <= 7;
        if (spring) {
            int startYear = date.getYear() - 1;
            int endYear = date.getYear();
            return SemesterContext.builder()
                    .currentDate(date.toString())
                    .currentTermCode("SPRING")
                    .currentLabel(startYear + "-" + endYear + "学年春季学期")
                    .nextTermCode("AUTUMN")
                    .nextLabel(date.getYear() + "-" + (date.getYear() + 1) + "学年秋季学期")
                    .build();
        }
        int startYear = month >= 8 ? date.getYear() : date.getYear() - 1;
        int endYear = startYear + 1;
        int nextStartYear = endYear - 1;
        int nextEndYear = endYear;
        return SemesterContext.builder()
                .currentDate(date.toString())
                .currentTermCode("AUTUMN")
                .currentLabel(startYear + "-" + endYear + "学年秋季学期")
                .nextTermCode("SPRING")
                .nextLabel(nextStartYear + "-" + nextEndYear + "学年春季学期")
                .build();
    }

    private String defaultMetricLabel(CurriculumService.CurriculumDefinition definition) {
        return StringUtils.hasText(definition.getMetricLabel()) ? definition.getMetricLabel() : "学分";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double safe(Double value) {
        return value == null ? 0.0 : value;
    }

    private String trimTrailingZero(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.valueOf(round(value));
    }

    private String normalize(String input) {
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

    @Data
    @Builder
    public static class AnalysisSnapshot {
        private String metricLabel;
        private boolean preciseCredits;
        private double totalCredits;
        private double earnedCredits;
        private double gapCredits;
        private List<ModuleProgressItem> modules;
        private List<MissingCourseItem> missingRequiredCourses;
        private List<UnfinishedGroupItem> unfinishedGroups;
        private List<RecommendationItem> recommendedCourses;
        private List<String> risks;
        private List<String> suggestions;
        private SemesterContext semesterContext;
        private int matchedCourseCount;
        private List<String> unmatchedTranscriptCourses;
    }

    @Data
    @Builder
    public static class ModuleProgressItem {
        private String key;
        private String title;
        private double requiredCredits;
        private double earnedCredits;
        private double gapCredits;
        private int percent;
        private int requirementGroupCount;
    }

    @Data
    @Builder
    public static class MissingCourseItem {
        private String course;
        private String module;
        private String offeredTerm;
        private boolean availableThisTerm;
        private String reason;
    }

    @Data
    @Builder
    public static class UnfinishedGroupItem {
        private String groupKey;
        private String moduleTitle;
        private String ruleText;
        private int requiredCount;
        private int completedCount;
        private int gapCount;
        private int availableThisTermCount;
        private List<String> candidateCourses;
    }

    @Data
    @Builder
    public static class RecommendationItem {
        private String course;
        private String module;
        private String ruleText;
        private String offeredTerm;
        private String recommendedFor;
        private boolean availableThisTerm;
        private String reason;
        private int priority;
    }

    @Data
    @Builder
    public static class SemesterContext {
        private String currentDate;
        private String currentTermCode;
        private String currentLabel;
        private String nextTermCode;
        private String nextLabel;
    }

    @Data
    @Builder
    private static class GroupEvaluation {
        private String groupKey;
        private String moduleKey;
        private double earnedCredits;
        private double requiredCredits;
        private int completedCount;
        private int requiredCount;
        private boolean completed;
    }
}
