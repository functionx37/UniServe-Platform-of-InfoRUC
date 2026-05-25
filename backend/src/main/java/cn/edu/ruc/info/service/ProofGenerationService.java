package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.GeneratedProofMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProofGenerationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final List<String> FONT_CANDIDATES = List.of(
            "C:\\Windows\\Fonts\\msyh.ttc",
            "C:\\Windows\\Fonts\\msyh.ttf",
            "C:\\Windows\\Fonts\\simsun.ttc",
            "C:\\Windows\\Fonts\\simhei.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf");

    private final GeneratedProofMapper generatedProofMapper;
    private final StoragePathHelper storagePathHelper;

    public ProofGenerationService(GeneratedProofMapper generatedProofMapper, StoragePathHelper storagePathHelper) {
        this.generatedProofMapper = generatedProofMapper;
        this.storagePathHelper = storagePathHelper;
    }

    public GeneratedProof generate(Long applicationId, String typeKey, User user, Map<String, Object> form) {
        String proofType = switch (typeKey) {
            case "enrollment_cert" -> "在读证明";
            case "political_cert" -> "政治面貌证明";
            default -> throw new RuntimeException("当前申请类型不支持生成证明");
        };
        Path fontPath = resolveFontPath();
        String fileId = "proof-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fileName = proofType + "-" + user.getStudentNo() + ".pdf";
        Path output = storagePathHelper.getProofPath().resolve(fileId + ".pdf").normalize();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont font = PDType0Font.load(document, fontPath.toFile());

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 20);
                stream.newLineAtOffset(180, 760);
                stream.showText(proofType);
                stream.endText();

                stream.beginText();
                stream.setFont(font, 12);
                stream.setLeading(24);
                stream.newLineAtOffset(80, 680);
                for (String line : buildContentLines(proofType, user, form)) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }
            document.save(output.toFile());
        } catch (IOException e) {
            throw new RuntimeException("生成 PDF 证明失败");
        }

        GeneratedProof proof = new GeneratedProof();
        proof.setId(fileId);
        proof.setApplicationId(applicationId);
        proof.setUserId(user.getId());
        proof.setProofType(typeKey);
        proof.setTitle(fileName);
        proof.setFileName(fileName);
        proof.setFilePath(output.toString());
        proof.setCreatedAt(LocalDateTime.now());
        generatedProofMapper.insert(proof);
        return proof;
    }

    public GeneratedProof findByApplicationId(Long applicationId) {
        List<GeneratedProof> proofs = generatedProofMapper.selectList(new LambdaQueryWrapper<GeneratedProof>()
                .eq(GeneratedProof::getApplicationId, applicationId)
                .orderByDesc(GeneratedProof::getCreatedAt)
                .last("limit 1"));
        return proofs.stream().findFirst().orElse(null);
    }

    public GeneratedProof findById(String proofId) {
        GeneratedProof proof = generatedProofMapper.selectById(proofId);
        if (proof == null) {
            throw new RuntimeException("证明文件不存在");
        }
        return proof;
    }

    private List<String> buildContentLines(String proofType, User user, Map<String, Object> form) {
        String receiver = String.valueOf(form.getOrDefault("receiver", "相关单位"));
        String purpose = String.valueOf(form.getOrDefault("purpose", "相关事务办理"));
        if ("在读证明".equals(proofType)) {
            return List.of(
                    "兹证明 " + user.getRealName() + "，学号 " + user.getStudentNo() + "，",
                    "系中国人民大学信息学院在读学生。",
                    "该生专业为 " + valueOrDash(user.getMajor()) + "，年级为 " + valueOrDash(user.getGrade()) + "。",
                    "本证明用于：" + purpose + "。",
                    "接收单位：" + receiver + "。",
                    "",
                    "特此证明。",
                    "",
                    "信息学院学生综合服务平台",
                    LocalDate.now().format(DATE_FORMATTER));
        }
        return List.of(
                "兹证明 " + user.getRealName() + "，学号 " + user.getStudentNo() + "，",
                "现政治面貌信息以学院登记记录为准，当前出具本证明用于：" + purpose + "。",
                "接收单位：" + receiver + "。",
                "",
                "如需进一步核验，请联系学院管理老师。",
                "",
                "信息学院学生综合服务平台",
                LocalDate.now().format(DATE_FORMATTER));
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "未登记" : value;
    }

    private Path resolveFontPath() {
        for (String candidate : FONT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new RuntimeException("系统缺少可用中文字体，无法生成 PDF 证明");
    }
}
