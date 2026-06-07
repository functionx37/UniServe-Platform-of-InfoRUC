package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.GeneratedProofMapper;
import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
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
            "/usr/share/fonts/opentype/noto/NotoSansCJKsc-Regular.otf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJKsc-Regular.otf",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf");

    private final GeneratedProofMapper generatedProofMapper;
    private final StoragePathHelper storagePathHelper;
    private final EncryptUtil encryptUtil;

    public ProofGenerationService(GeneratedProofMapper generatedProofMapper, StoragePathHelper storagePathHelper, EncryptUtil encryptUtil) {
        this.generatedProofMapper = generatedProofMapper;
        this.storagePathHelper = storagePathHelper;
        this.encryptUtil = encryptUtil;
    }

    public GeneratedProof generate(Long applicationId, String typeKey, User user, Map<String, Object> form) {
        String proofType = switch (typeKey) {
            case "leave" -> "请假证明";
            case "enrollment_cert" -> "在读证明";
            case "political_cert" -> "政治面貌证明";
            case "id_cert" -> "身份证明";
            default -> throw new RuntimeException("当前申请类型不支持生成证明");
        };
        Path fontPath = resolveFontPathOrNull();
        String fileId = "proof-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String fileName = proofType + "-" + user.getStudentNo() + ".pdf";
        Path output = storagePathHelper.getProofPath().resolve(fileId + ".pdf").normalize();

        try (PDDocument document = new PDDocument()) {
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                throw new RuntimeException("证明文件目录不可写");
            }
            renderProof(document, fontPath, proofType, user, form);
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

    public byte[] generatePreview(String typeKey, User user, Map<String, Object> form) {
        String proofType = switch (typeKey) {
            case "leave" -> "请假证明";
            case "enrollment_cert" -> "在读证明";
            case "political_cert" -> "政治面貌证明";
            case "id_cert" -> "身份证明";
            default -> throw new RuntimeException("当前申请类型不支持生成证明");
        };
        Path fontPath = resolveFontPathOrNull();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            renderProof(document, fontPath, proofType, user, form);
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("生成 PDF 预览失败");
        }
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

    private void renderProof(PDDocument document, Path fontPath, String proofType, User user, Map<String, Object> form) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        boolean hasUnicodeFont = false;
        PDFont font;
        try {
            if (fontPath != null) {
                font = PDType0Font.load(document, fontPath.toFile());
                hasUnicodeFont = true;
            } else {
                font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            }
        } catch (IOException e) {
            font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            hasUnicodeFont = false;
        }
        String title = hasUnicodeFont ? proofType : "Proof";
        List<String> lines = hasUnicodeFont ? buildContentLines(proofType, user, form) : buildAsciiFallbackLines(proofType, user, form);

        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(font, 20);
            stream.newLineAtOffset(180, 760);
            stream.showText(title);
            stream.endText();

            stream.beginText();
            stream.setFont(font, 12);
            stream.setLeading(24);
            stream.newLineAtOffset(80, 680);
            for (String line : lines) {
                stream.showText(line);
                stream.newLine();
            }
            stream.endText();
        }
    }

    private List<String> buildContentLines(String proofType, User user, Map<String, Object> form) {
        String receiver = String.valueOf(form.getOrDefault("receiver", "相关单位"));
        String purpose = String.valueOf(form.getOrDefault("purpose", "相关事务办理"));
        String idCard = valueOrDash(decryptValue(user == null ? null : user.getIdCard()));
        String politicalIdentity = valueOrDash(user == null ? null : user.getIdentity());
        String partyJoinDate = valueOrDash(normalizeDate(String.valueOf(form.getOrDefault("partyJoinDate", ""))));
        String leaveStart = valueOrDash(normalizeDate(String.valueOf(form.getOrDefault("leaveStart", ""))));
        String leaveEnd = valueOrDash(normalizeDate(String.valueOf(form.getOrDefault("leaveEnd", ""))));
        String leaveReason = valueOrDash(String.valueOf(form.getOrDefault("reason", "")).trim());
        String name = user == null ? "—" : valueOrDash(user.getRealName());
        String studentNo = user == null ? "—" : valueOrDash(user.getStudentNo());
        String major = user == null ? "未登记" : valueOrDash(user.getMajor());
        String grade = user == null ? "未登记" : valueOrDash(user.getGrade());
        if ("身份证明".equals(proofType)) {
            return List.of(
                    "兹证明 " + name + "，学号 " + studentNo + "，",
                    "身份证号：" + idCard + "。",
                    "系中国人民大学信息学院学生，专业为 " + major + "，年级为 " + grade + "。",
                    "本证明用于：" + purpose + "。",
                    "接收单位：" + receiver + "。",
                    "",
                    "特此证明。",
                    "",
                    "信息学院学生综合服务平台",
                    LocalDate.now().format(DATE_FORMATTER));
        }
        if ("请假证明".equals(proofType)) {
            return List.of(
                    "兹证明 " + name + "，学号 " + studentNo + "，",
                    "身份证号：" + idCard + "。",
                    "请假起止：" + leaveStart + " 至 " + leaveEnd + "。",
                    "请假事由：" + leaveReason + "。",
                    "",
                    "本证明由系统根据申请信息自动生成，仅用于演示/办理相关事项。",
                    "",
                    "信息学院学生综合服务平台",
                    LocalDate.now().format(DATE_FORMATTER));
        }
        if ("在读证明".equals(proofType)) {
            return List.of(
                    "兹证明 " + user.getRealName() + "，学号 " + user.getStudentNo() + "，",
                    "身份证号：" + idCard + "。",
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
                "身份证号：" + idCard + "。",
                "政治面貌：" + politicalIdentity + "。",
                "入党时间：" + partyJoinDate + "。",
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

    private String decryptValue(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank() || encryptUtil == null) {
            return "";
        }
        try {
            return encryptUtil.decrypt(encryptedText);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeDate(String dateText) {
        String raw = String.valueOf(dateText == null ? "" : dateText).trim();
        if (raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(raw);
            return date.format(DATE_FORMATTER);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private List<String> buildAsciiFallbackLines(String proofType, User user, Map<String, Object> form) {
        String studentNo = user == null ? "" : String.valueOf(user.getStudentNo() == null ? "" : user.getStudentNo());
        String leaveStart = String.valueOf(form.getOrDefault("leaveStart", ""));
        String leaveEnd = String.valueOf(form.getOrDefault("leaveEnd", ""));
        String reason = String.valueOf(form.getOrDefault("reason", ""));
        String receiver = String.valueOf(form.getOrDefault("receiver", ""));
        String purpose = String.valueOf(form.getOrDefault("purpose", ""));

        if ("请假证明".equals(proofType)) {
            return List.of(
                    "Leave Proof (fallback)",
                    "StudentNo: " + stripNonAscii(studentNo),
                    "Leave: " + stripNonAscii(leaveStart) + " to " + stripNonAscii(leaveEnd),
                    "Reason: " + stripNonAscii(reason),
                    "GeneratedAt: " + LocalDate.now());
        }
        if ("身份证明".equals(proofType)) {
            return List.of(
                    "ID Proof (fallback)",
                    "StudentNo: " + stripNonAscii(studentNo),
                    "Purpose: " + stripNonAscii(purpose),
                    "Receiver: " + stripNonAscii(receiver),
                    "GeneratedAt: " + LocalDate.now());
        }
        return List.of(
                "Proof (fallback)",
                "StudentNo: " + stripNonAscii(studentNo),
                "Purpose: " + stripNonAscii(purpose),
                "Receiver: " + stripNonAscii(receiver),
                "GeneratedAt: " + LocalDate.now());
    }

    private String stripNonAscii(String text) {
        String s = String.valueOf(text == null ? "" : text);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c >= 32 && c <= 126 ? c : '?');
        }
        return out.toString();
    }

    private Path resolveFontPathOrNull() {
        for (String candidate : FONT_CANDIDATES) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        Path resolved = scanFontDirectory(Path.of("/usr/share/fonts"));
        if (resolved != null) {
            return resolved;
        }
        return null;
    }

    private Path scanFontDirectory(Path root) {
        if (root == null || !Files.exists(root)) {
            return null;
        }
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return (name.endsWith(".ttf") || name.endsWith(".ttc") || name.endsWith(".otf"))
                                && (name.contains("notosanscjk") || name.contains("sourcehansans") || name.contains("wqy")
                                        || name.contains("droidsansfallback") || name.contains("uming"));
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }
}
