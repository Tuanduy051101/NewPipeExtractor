/**/// DO NOT MODIFY THIS FILE MANUALLY
/**/// This class was automatically generated by "GeneratePatternClasses.java",
/**/// modify the "unique_patterns.json" and re-generate instead.

package org.schabi.newpipe.extractor.timeago.patterns;

import org.schabi.newpipe.extractor.timeago.PatternsHolder;

public class pa extends PatternsHolder {
    private static final String WORD_SEPARATOR = " ";
    private static final String[]
            SECONDS  /**/ = {"ਸਕਿੰਟ"},
            MINUTES  /**/ = {"ਮਿੰਟ"},
            HOURS    /**/ = {"ਘੰਟਾ", "ਘੰਟੇ"},
            DAYS     /**/ = {"ਦਿਨ"},
            WEEKS    /**/ = {"ਹਫ਼ਤਾ", "ਹਫ਼ਤੇ"},
            MONTHS   /**/ = {"ਮਹੀਨਾ", "ਮਹੀਨੇ"},
            YEARS    /**/ = {"ਸਾਲ"};

    private static final pa INSTANCE = new pa();

    public static pa getInstance() {
        return INSTANCE;
    }

    private pa() {
        super(WORD_SEPARATOR, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS);
    }
}