package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.Template;
import cn.edu.ruc.info.mapper.TemplateMapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    @Autowired
    private TemplateMapper templateMapper;

    public List<TemplateVO> listTemplates() {
        List<Template> templates = templateMapper.selectList(null);
        return templates.stream().map(t -> TemplateVO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .scene(t.getScene())
                .fileType(t.getFileType())
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null)
                .url(t.getUrl())
                .build()).collect(Collectors.toList());
    }

    public TemplateVO getTemplateDetail(String id) {
        Template t = templateMapper.selectById(id);
        if (t == null)
            throw new RuntimeException("模板不存在");
        return TemplateVO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .scene(t.getScene())
                .fileType(t.getFileType())
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null)
                .url(t.getUrl())
                .build();
    }

    public TemplateVO generatePreviewLink(String id) {
        Template t = templateMapper.selectById(id);
        if (t == null)
            throw new RuntimeException("模板不存在");
        // 实际应生成签名URL，这里直接返回原始URL
        return TemplateVO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .scene(t.getScene())
                .fileType(t.getFileType())
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null)
                .url(t.getUrl())
                .build();
    }

    public TemplateVO generateDownloadLink(String id) {
        // 与预览逻辑相同（简化）
        return generatePreviewLink(id);
    }

    @Data
    @Builder
    public static class TemplateVO {
        private String id;
        private String title;
        private String scene;
        private String fileType;
        private String updatedAt;
        private String url;
    }
}