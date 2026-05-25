package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.CurriculumFile;
import cn.edu.ruc.info.mapper.CurriculumFileMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CurriculumService {

    private final CurriculumFileMapper curriculumFileMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final JsonUtils jsonUtils;
    private volatile CurriculumDefinition cachedDefinition;

    public CurriculumService(CurriculumFileMapper curriculumFileMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            JsonUtils jsonUtils) {
        this.curriculumFileMapper = curriculumFileMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.jsonUtils = jsonUtils;
    }

    public UploadResult upload(MultipartFile file, Long operatorId) {
        String originalName = file.getOriginalFilename();
        String extension = getExtension(originalName);
        if (!List.of(".json", ".xlsx", ".xls").contains(extension)) {
            throw new RuntimeException("培养方案仅支持 JSON 或 Excel 文件");
        }

        FileStorageService.StoredFile storedFile = fileStorageService.saveMultipartFile(
                file,
                storagePathHelper.getCurriculumPath(),
                "curriculum");
        Path actualPath = storedFile.path();

        CurriculumDefinition definition = loadDefinition(actualPath, extension);
        cachedDefinition = definition;

        curriculumFileMapper.selectList(null).forEach(item -> {
            item.setActive(false);
            curriculumFileMapper.updateById(item);
        });

        CurriculumFile curriculumFile = new CurriculumFile();
        curriculumFile.setId("cur-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        curriculumFile.setFileName(storedFile.originalName());
        curriculumFile.setFileType(extension.replace(".", ""));
        curriculumFile.setFilePath(actualPath.toString());
        curriculumFile.setVersion(definition.getVersion());
        curriculumFile.setActive(true);
        curriculumFile.setUploadedBy(operatorId);
        curriculumFile.setUploadedAt(LocalDateTime.now());
        curriculumFileMapper.insert(curriculumFile);

        return UploadResult.builder()
                .id(curriculumFile.getId())
                .fileName(curriculumFile.getFileName())
                .version(curriculumFile.getVersion())
                .programName(definition.getProgramName())
                .requiredModules(definition.getRequiredModules().size())
                .requiredCourses(definition.getRequiredCourses().size())
                .uploadedAt(curriculumFile.getUploadedAt().toString())
                .build();
    }

    public UploadResult getLatestSummary() {
        CurriculumFile latest = curriculumFileMapper.selectOne(
                new LambdaQueryWrapper<CurriculumFile>().eq(CurriculumFile::getActive, true)
                        .orderByDesc(CurriculumFile::getUploadedAt).last("limit 1"));
        if (latest == null) {
            throw new RuntimeException("尚未上传培养方案");
        }
        CurriculumDefinition definition = getActiveDefinition();
        return UploadResult.builder()
                .id(latest.getId())
                .fileName(latest.getFileName())
                .version(latest.getVersion())
                .programName(definition.getProgramName())
                .requiredModules(definition.getRequiredModules().size())
                .requiredCourses(definition.getRequiredCourses().size())
                .uploadedAt(latest.getUploadedAt() != null ? latest.getUploadedAt().toString() : null)
                .build();
    }

    public CurriculumDefinition getActiveDefinition() {
        if (cachedDefinition != null) {
            return cachedDefinition;
        }
        CurriculumFile latest = curriculumFileMapper.selectOne(
                new LambdaQueryWrapper<CurriculumFile>().eq(CurriculumFile::getActive, true)
                        .orderByDesc(CurriculumFile::getUploadedAt).last("limit 1"));
        if (latest == null) {
            throw new RuntimeException("尚未上传培养方案");
        }
        cachedDefinition = loadDefinition(Path.of(latest.getFilePath()), "." + latest.getFileType().toLowerCase(Locale.ROOT));
        return cachedDefinition;
    }

    private CurriculumDefinition loadDefinition(Path path, String extension) {
        try {
            if (".json".equals(extension)) {
                CurriculumDefinition definition = jsonUtils.fromJson(
                        Files.readString(path),
                        CurriculumDefinition.class);
                validateDefinition(definition);
                return definition;
            }
            return parseExcelDefinition(path);
        } catch (IOException e) {
            throw new RuntimeException("读取培养方案文件失败");
        }
    }

    private CurriculumDefinition parseExcelDefinition(Path path) {
        try (InputStream inputStream = Files.newInputStream(path); Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new RuntimeException("培养方案文件为空");
            }
            Map<String, Integer> headerMap = buildHeaderMap(sheet.getRow(sheet.getFirstRowNum()), formatter);
            List<RequiredCourse> courses = new ArrayList<>();
            Map<String, Double> moduleCredits = new LinkedHashMap<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String courseName = getCell(row, headerMap, formatter, "课程名", "课程");
                if (!StringUtils.hasText(courseName)) {
                    continue;
                }
                String module = defaultIfBlank(getCell(row, headerMap, formatter, "模块", "类别"), "未分类");
                double credits = parseDouble(defaultIfBlank(getCell(row, headerMap, formatter, "学分"), "0"));
                boolean required = !"否".equals(defaultIfBlank(getCell(row, headerMap, formatter, "是否必修", "必修"), "是"));
                courses.add(RequiredCourse.builder()
                        .courseName(courseName.trim())
                        .module(module.trim())
                        .credits(credits)
                        .required(required)
                        .build());
                moduleCredits.put(module.trim(),
                        moduleCredits.getOrDefault(module.trim(), 0.0) + credits);
            }
            CurriculumDefinition definition = CurriculumDefinition.builder()
                    .programName(stripExtension(path.getFileName().toString()))
                    .version(LocalDateTime.now().toString())
                    .requiredModules(moduleCredits.entrySet().stream()
                            .map(entry -> RequiredModule.builder()
                                    .key(entry.getKey())
                                    .title(entry.getKey())
                                    .requiredCredits(entry.getValue())
                                    .build())
                            .collect(Collectors.toList()))
                    .requiredCourses(courses)
                    .build();
            validateDefinition(definition);
            return definition;
        } catch (IOException e) {
            throw new RuntimeException("解析培养方案 Excel 失败");
        }
    }

    private void validateDefinition(CurriculumDefinition definition) {
        if (definition == null || definition.getRequiredModules() == null || definition.getRequiredCourses() == null
                || definition.getRequiredModules().isEmpty() || definition.getRequiredCourses().isEmpty()) {
            throw new RuntimeException("培养方案内容不完整");
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

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new RuntimeException("文件缺少后缀名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        return fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
    }

    @Data
    @Builder
    public static class CurriculumDefinition {
        private String programName;
        private String version;
        private List<RequiredModule> requiredModules;
        private List<RequiredCourse> requiredCourses;
    }

    @Data
    @Builder
    public static class RequiredModule {
        private String key;
        private String title;
        private Double requiredCredits;
    }

    @Data
    @Builder
    public static class RequiredCourse {
        private String courseName;
        private String module;
        private Double credits;
        private Boolean required;
    }

    @Data
    @Builder
    public static class UploadResult {
        private String id;
        private String fileName;
        private String version;
        private String programName;
        private Integer requiredModules;
        private Integer requiredCourses;
        private String uploadedAt;
    }
}
