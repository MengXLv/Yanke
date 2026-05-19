package com.yangke.forum.module.content.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * DFA 敏感词过滤器。
 * 敏感词库可通过 init() 方法加载，当前为内置列表。
 * 返回命中的敏感词集合，业务层据此决定自动屏蔽 or 审核标记。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private final Map<Character, Object> dfaMap = new HashMap<>();

    private static final char END_FLAG = '\0';

    @PostConstruct
    public void init() {
        // 内置敏感词库，生产环境从数据库或文件加载
        List<String> words = Arrays.asList(
                "毒品", "枪支", "赌博", "色情", "裸聊",
                "法轮功", "六四", "台独", "藏独", "疆独",
                "代开发票", "办证", "高利贷", "嫖娼", "卖淫",
                "冰毒", "海洛因", "摇头丸", "k粉", "大麻",
                "暴力", "恐怖", "炸弹", "枪支弹药"
        );
        for (String word : words) {
            addWord(word);
        }
        log.info("Sensitive word filter loaded, {} words", words.size());
    }

    /**
     * 添加敏感词到 DFA 字典树
     */
    public void addWord(String word) {
        Map<Character, Object> current = dfaMap;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            @SuppressWarnings("unchecked")
            Map<Character, Object> child = (Map<Character, Object>) current.get(c);
            if (child == null) {
                child = new HashMap<>();
                current.put(c, child);
            }
            current = child;
        }
        current.put(END_FLAG, Boolean.TRUE);
    }

    /**
     * 检测文本中的敏感词，返回命中的所有敏感词
     */
    public Set<String> match(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) return result;

        text = text.toLowerCase();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            Map<Character, Object> current = dfaMap;
            int j = i;
            while (j < len) {
                char c = text.charAt(j);
                @SuppressWarnings("unchecked")
                Map<Character, Object> child = (Map<Character, Object>) current.get(c);
                if (child == null) break;

                current = child;
                j++;

                if (current.containsKey(END_FLAG)) {
                    result.add(text.substring(i, j));
                }
            }
        }
        return result;
    }

    /**
     * 是否包含敏感词
     */
    public boolean containsSensitive(String text) {
        return !match(text).isEmpty();
    }

    /**
     * 替换敏感词为 ***
     */
    public String replace(String text) {
        Set<String> words = match(text);
        String result = text;
        for (String word : words) {
            result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), "***");
        }
        return result;
    }
}
