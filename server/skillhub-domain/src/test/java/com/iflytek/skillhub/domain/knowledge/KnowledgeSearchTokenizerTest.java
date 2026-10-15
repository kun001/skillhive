package com.iflytek.skillhub.domain.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeSearchTokenizerTest {

    @Test
    void splitsChineseIntoBigramsAndLatinIntoLowerCaseWords() {
        assertThat(KnowledgeSearchTokenizer.indexTokens("报销流程 Docker-Compose v2，年"))
                .isEqualTo("报销 销流 流程 docker compose v2 年");
    }

    @Test
    void indexTokensAreDistinctAndBounded() {
        assertThat(KnowledgeSearchTokenizer.indexTokens("流程流程 流程")).isEqualTo("流程 程流");
        String huge = KnowledgeSearchTokenizer.indexTokens("x" + "一二三四五六七八九十".repeat(200_000));
        assertThat(huge.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(KnowledgeSearchTokenizer.MAX_INDEX_BYTES + 16);
    }

    @Test
    void queriesRequireEveryTermAndTreatLoneHanAsPrefix() {
        assertThat(KnowledgeSearchTokenizer.toTsQuery("报销流程")).isEqualTo("(报销 & 销流 & 流程)");
        assertThat(KnowledgeSearchTokenizer.toTsQuery("报销  Docker")).isEqualTo("报销 & docker");
        assertThat(KnowledgeSearchTokenizer.toTsQuery("报")).isEqualTo("报:*");
    }

    @Test
    void queriesNeverCarryTsQuerySyntax() {
        assertThat(KnowledgeSearchTokenizer.toTsQuery("a & b | !c (d) 'e':*")).isEqualTo("a & b & c & d & e");
        assertThat(KnowledgeSearchTokenizer.toTsQuery("&&& !!! ")).isEmpty();
        assertThat(KnowledgeSearchTokenizer.toTsQuery(null)).isEmpty();
    }

    @Test
    void japaneseAndKoreanAreTreatedLikeChinese() {
        assertThat(KnowledgeSearchTokenizer.indexTokens("テスト 회의")).isEqualTo("テス スト 회의");
    }
}
