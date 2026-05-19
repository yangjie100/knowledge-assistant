package com.knowledge.assistant.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ka.rag.chunk")
public class ChunkConfig {

    private int defaultSize = 800;
    private int minSize = 200;
    private int overlap = 100;
}
