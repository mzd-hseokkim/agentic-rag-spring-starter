package kr.co.mz.agenticai.core.retrieval.bm25;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;

/**
 * Factories for Lucene analyzers tuned for Korean text.
 *
 * <p>Korean text is agglutinative — particles ("은/는/이/가") and verb
 * endings ("다/요/습니다") attach directly to stems, so whitespace
 * tokenization leaves "서울에서" as a single token and breaks BM25
 * matching on "서울". The Nori analyzer performs morpheme-level
 * tokenization using MeCab-ko-dic.
 */
public final class KoreanAnalyzers {

    private KoreanAnalyzers() {}

    /** Default {@link KoreanAnalyzer} with built-in stopword and POS filters. */
    public static Analyzer standard() {
        return new KoreanAnalyzer();
    }
}
