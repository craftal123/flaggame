package com.flaggame;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

final class FlagDeck {

    private final Set<String> usedCountryCodes = new HashSet<>();

    void reset() {
        usedCountryCodes.clear();
    }

    Country draw(List<Country> countries, RandomGenerator random) {
        List<Country> unused = countries.stream()
                .filter(country -> !usedCountryCodes.contains(country.code()))
                .toList();
        if (unused.isEmpty()) {
            return null;
        }

        Country selected = unused.get(random.nextInt(unused.size()));
        usedCountryCodes.add(selected.code());
        return selected;
    }
}
