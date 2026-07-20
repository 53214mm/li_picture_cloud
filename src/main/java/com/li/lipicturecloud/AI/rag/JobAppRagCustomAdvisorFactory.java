package com.li.lipicturecloud.AI.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * RAG 增强顾问工厂，基于 pgvector 向量库检索相关文档增强 AI 回答
 */
public class JobAppRagCustomAdvisorFactory {

    /**
     * 创建基于 pgvector 的 RAG 增强顾问
     * 检索与用户问题最相似的前 3 条文档，增强后喂给 AI
     *
     * @param vectorStore pgvector 向量存储
     */
    public static Advisor createJobRagAdvisor(VectorStore vectorStore) {
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.5)
                .topK(3)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(JobAppContextualQueryAugmenterFactory.createInstance())
                .build();
    }
}
