package com.knowledge.assistant.rag.splitter;

import com.knowledge.assistant.rag.config.ChunkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentSplitter {

    private final ChunkConfig chunkConfig;

    public List<Document> split(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkConfig.getDefaultSize())
                .withMinChunkSizeChars(chunkConfig.getMinSize())
                .build();
        return splitter.apply(documents);
    }
}
