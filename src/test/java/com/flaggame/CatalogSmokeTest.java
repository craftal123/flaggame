package com.flaggame;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;

final class CatalogSmokeTest {

    private CatalogSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        CountryCatalog catalog = new CountryCatalog();
        check(catalog.countries().size() >= 190, "Expected at least 190 countries");
        verifyDifficulties(catalog.countries());

        Path downloadedCodes = Path.of(System.getProperty("flagcdn.codes", ""));
        if (!downloadedCodes.toString().isBlank() && Files.isRegularFile(downloadedCodes)) {
            var liveCountries = catalog.parse(Files.readString(downloadedCodes, StandardCharsets.UTF_8));
            int count = liveCountries.size();
            check(count >= 240, "Expected the live FlagCDN list to contain at least 240 country flags");
            verifyDifficulties(liveCountries);
            System.out.println("Parsed " + count + " country flags from FlagCDN.");
        }

        Country turkey = find(catalog, "TR");
        check(turkey.matches("TURKEY"), "Turkey alias should match");
        check(turkey.matches("Türkiye"), "Türkiye should match");
        check(turkey.matches("טורקיה"), "Hebrew Turkey should match");
        check(turkey.matches("תורקיה"), "Alternate Hebrew Turkey spelling should match");

        Country ivoryCoast = find(catalog, "CI");
        check(ivoryCoast.matches("Cote dIvoire"), "Unaccented Ivory Coast name should match");

        Country myanmar = find(catalog, "MM");
        check(myanmar.matches("בורמה"), "Hebrew Burma should match");
        check(myanmar.matches("מיאנמאר"), "Hebrew Myanmar should match");

        Country unitedStates = find(catalog, "US");
        check(unitedStates.matches("אַרְצוֹת הַבְּרִית"), "Hebrew niqqud should be ignored");

        check(catalog.isKnownCountryAnswer("Japan"), "Known English country should be recognized");
        check(catalog.isKnownCountryAnswer("יפן"), "Known Hebrew country should be recognized");
        check(!catalog.isKnownCountryAnswer("hello everyone"), "Normal chat should not be a country");

        System.out.println("Catalog smoke test passed with "
                + catalog.countries().size() + " countries.");
    }

    private static Country find(CountryCatalog catalog, String code) {
        return catalog.countries().stream()
                .filter(country -> country.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static void verifyDifficulties(java.util.List<Country> countries) {
        var easy = Difficulty.EASY.filter(countries);
        var medium = Difficulty.MEDIUM.filter(countries);
        var hard = Difficulty.HARD.filter(countries);

        check(easy.size() == 34, "Expected 34 easy flags, got " + easy.size());
        check(medium.size() == 119, "Expected 119 medium flags, got " + medium.size());
        check(hard.size() == countries.size() - medium.size(),
                "Hard should contain only flags outside medium");
        check(medium.containsAll(easy), "Medium should include every easy flag");
        check(hard.stream().noneMatch(medium::contains), "Hard should not include easy or medium flags");

        FlagDeck deck = new FlagDeck();
        var drawnCodes = new HashSet<String>();
        var random = new Random(12345);
        for (int index = 0; index < easy.size(); index++) {
            Country drawn = deck.draw(easy, random);
            check(drawn != null, "Deck ended before every easy flag was drawn");
            check(drawnCodes.add(drawn.code()), "Flag repeated in the same deck: " + drawn.code());
        }
        check(deck.draw(easy, random) == null, "Exhausted deck should not repeat flags");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
