package com.yangke.forum.module.content.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        filter.init();
    }

    @Test
    void matchSensitiveWord() {
        assertFalse(filter.match("毒品").isEmpty());
        assertFalse(filter.match("买卖枪支").isEmpty());
    }

    @Test
    void cleanText() {
        assertTrue(filter.match("正常内容").isEmpty());
        assertTrue(filter.match("今天天气真好").isEmpty());
    }

    @Test
    void containsSensitive() {
        assertTrue(filter.containsSensitive("这里有毒品交易"));
        assertFalse(filter.containsSensitive("正常讨论帖"));
    }

    @Test
    void replaceSensitive() {
        String result = filter.replace("有人贩卖毒品和枪支");
        assertTrue(result.contains("***"));
        assertFalse(result.contains("毒品"));
    }

    @Test
    void emptyAndNull() {
        assertTrue(filter.match(null).isEmpty());
        assertTrue(filter.match("").isEmpty());
        assertFalse(filter.containsSensitive(null));
        assertFalse(filter.containsSensitive(""));
    }

    @Test
    void regexSpecialCharSafe() {
        // replace中词需匹配大小写（DFA已toLowerCase），验证正则特字符不崩
        filter.addWord("a+b");
        String result = filter.replace("test a+b text");
        assertTrue(result.contains("***"));
    }
}
