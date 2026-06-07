package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofGenerationServiceTest {

    @Test
    void generatePreviewShouldContainChineseTextWhenProjectFontExists() throws IOException {
        ProofGenerationService service = new ProofGenerationService(null, null, null);
        User user = new User();
        user.setRealName("张三");
        user.setStudentNo("20260001");
        user.setMajor("计算机科学与技术");
        user.setGrade("2026级");
        user.setIdentity("中共党员");

        byte[] pdf = service.generatePreview("enrollment_cert", user, Map.of(
                "purpose", "奖学金申请",
                "receiver", "教务处"
        ));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("在读证明"));
            assertTrue(text.contains("张三"));
            assertTrue(text.contains("计算机科学与技术"));
            assertTrue(text.contains("奖学金申请"));
        }
    }
}
