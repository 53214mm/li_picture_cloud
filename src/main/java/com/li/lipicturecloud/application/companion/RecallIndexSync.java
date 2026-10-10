package com.li.lipicturecloud.application.companion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 图片记忆和聊天经历共用的召回工具：向量索引对账、字面两字匹配。
 */
final class RecallIndexSync {

    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}]+");
    private static final Pattern LATIN = Pattern.compile("[a-z0-9][a-z0-9_-]{2,39}");
    /** 太常见、不足以说明在聊同一件事的两字组合。 */
    private static final Set<String> STOP_BIGRAMS = Set.of("图片", "照片", "这张", "那张", "一张", "之前", "记得",
            "还记", "什么", "哪些", "我们", "你的", "我的", "可能", "画面", "一个", "没有", "这个", "那个", "观察",
            "可见", "推测", "意义", "角色", "出处", "对方", "上传", "喂过", "给你", "你还", "现在", "今天", "绫页",
            "上次", "聊过", "说过", "时候");

    private RecallIndexSync() {
    }

    /**
     * 补齐向量库缺少或内容变过的条目，并清掉已不在 {@code live} 里的旧向量。
     *
     * @param live 当前可用条目 id → 用于向量化的文本
     */
    static Map<Long, String> reconcile(CompanionMemoryIndex index, long companionId, long subjectId,
                          Map<Long, String> live, int maxPerTurn) {
        Map<Long, String> indexed = index.fingerprints(companionId, subjectId);
        List<CompanionMemoryIndex.Entry> missing = new ArrayList<>();
        Map<Long, String> pending = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : live.entrySet()) {
            String fingerprint = fingerprint(entry.getValue());
            if (!fingerprint.equals(indexed.get(entry.getKey()))) {
                if (missing.size() < maxPerTurn) {
                    missing.add(new CompanionMemoryIndex.Entry(entry.getKey(), entry.getValue(), fingerprint));
                } else {
                    pending.put(entry.getKey(), entry.getValue());
                }
            }
        }
        index.upsert(companionId, subjectId, missing);
        index.remove(indexed.keySet().stream().filter(id -> !live.containsKey(id)).toList());
        return pending;
    }

    /** 字面兜底：这句话与条目文本共享的两字组合越多越靠前。 */
    static List<Long> lexical(String query, Map<Long, String> documents) {
        Set<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> scores = new LinkedHashMap<>();
        documents.forEach((id, text) -> {
            Set<String> documentTerms = terms(text);
            int score = (int) terms.stream().filter(documentTerms::contains).count();
            if (score > 0) {
                scores.put(id, score);
            }
        });
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }

    static Set<String> terms(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> terms = new LinkedHashSet<>();
        CJK.matcher(lower).results().map(match -> match.group()).forEach(run -> {
            for (int i = 0; i + 1 < run.length(); i++) {
                String bigram = run.substring(i, i + 2);
                if (!STOP_BIGRAMS.contains(bigram)) {
                    terms.add(bigram);
                }
            }
        });
        LATIN.matcher(lower).results().map(match -> match.group()).forEach(terms::add);
        return terms;
    }

    static String fingerprint(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
