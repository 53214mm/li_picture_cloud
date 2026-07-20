package com.li.lipicturecloud.AI.advisor;

import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 敏感词过滤顾问，从配置文件加载敏感词列表并拦截包含敏感词的请求
 */
public class ProhibitedWordsAdvisor implements CallAdvisor, StreamAdvisor {

    private static final String RESOURCE = "/prohibited-words.txt";
    private final Set<String> prohibitedWords;

    public ProhibitedWordsAdvisor() {
        this.prohibitedWords = loadProhibitedWords();
    }

    private Set<String> loadProhibitedWords() {
        InputStream is = ProhibitedWordsAdvisor.class.getResourceAsStream(RESOURCE);
        if (is == null) {
            return Collections.emptySet();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .collect(Collectors.toCollection(() -> new HashSet<>(64)));
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        checkProhibitedWords(chatClientRequest);

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        checkProhibitedWords(chatClientRequest);

        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        return chatClientResponses;
    }

    private void checkProhibitedWords(ChatClientRequest request) {
        if (request == null || request.prompt() == null || request.prompt().getUserMessage() == null) {
            return;
        }
        String userText = request.prompt().getUserMessage().getText();
        if (userText == null || userText.isEmpty() || prohibitedWords.isEmpty()) {
            return;
        }

        String lowered = userText.toLowerCase();
        for (String word : prohibitedWords) {
            if (word == null || word.isEmpty()) continue;
            if (lowered.contains(word.toLowerCase())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求中包含敏感词: " + word);
            }
        }
    }

    @Override
    public String getName() {
        return "ProhibitedWordsAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
