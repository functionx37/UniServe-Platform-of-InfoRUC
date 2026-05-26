package cn.edu.ruc.info.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk_embeddings")
public class KnowledgeChunkEmbedding {
    @TableId
    private String chunkId;
    private String model;
    private Integer dim;
    private String embedding;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

