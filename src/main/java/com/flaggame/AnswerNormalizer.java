package com.flaggame;

import java.text.Normalizer;
import java.util.Locale;

final class AnswerNormalizer {

    private AnswerNormalizer() {
    }

    static String normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('ך', 'כ')
                .replace('ם', 'מ')
                .replace('ן', 'נ')
                .replace('ף', 'פ')
                .replace('ץ', 'צ');

        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK)
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }
}
