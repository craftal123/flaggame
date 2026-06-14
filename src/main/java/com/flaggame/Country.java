package com.flaggame;

import java.util.Set;

record Country(String code, String englishName, String hebrewName, Set<String> answers) {

    boolean matches(String answer) {
        return answers.contains(AnswerNormalizer.normalize(answer));
    }
}
