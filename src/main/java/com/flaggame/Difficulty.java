package com.flaggame;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

enum Difficulty {

    EASY("""
            US CA MX BR AR GB FR DE IT ES PT NL BE CH AT SE NO DK FI IE
            RU UA PL GR TR IL EG ZA IN CN JP KR AU NZ
            """),
    MEDIUM("""
            US CA MX BR AR GB FR DE IT ES PT NL BE CH AT SE NO DK FI IE
            RU UA PL GR TR IL EG ZA IN CN JP KR AU NZ
            CL CO PE VE UY PY BO EC CR PA CU DO JM HT
            IS CZ SK HU RO BG RS HR BA SI AL MK ME EE LV LT BY MD
            GE AM AZ SA AE QA KW BH OM JO LB SY IQ IR MA DZ TN LY
            SD ET KE TZ UG GH NG CM SN CI CD CG ZW ZM BW NA MZ MG
            PK BD LK NP TH VN MY SG ID PH KZ UZ MN KP TW PG FJ
            """),
    HARD("");

    private final Set<String> codes;

    Difficulty(String codes) {
        this.codes = Arrays.stream(codes.split("\\s+"))
                .filter(code -> !code.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    static Difficulty parse(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    List<Country> filter(List<Country> countries) {
        if (this == HARD) {
            return countries.stream()
                    .filter(country -> !MEDIUM.codes.contains(country.code()))
                    .toList();
        }
        return countries.stream()
                .filter(country -> codes.contains(country.code()))
                .toList();
    }

    String displayName() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
