package com.flaggame;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CountryCatalog {

    private static final Locale HEBREW = Locale.forLanguageTag("he-IL");
    private static final Pattern JSON_ENTRY = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Set<String> NON_COUNTRY_CODES = Set.of("EU", "UN");
    private static final Map<String, List<String>> EXTRA_ALIASES = createAliases();

    private volatile List<Country> countries = buildLocaleFallback();
    private volatile Set<String> knownAnswers = collectAnswers(countries);

    List<Country> countries() {
        return countries;
    }

    int refresh(URI source, Path cacheFile) throws IOException {
        IOException downloadFailure = null;
        try {
            String json = download(source);
            List<Country> downloaded = parse(json);
            saveCache(cacheFile, json);
            this.countries = downloaded;
            this.knownAnswers = collectAnswers(downloaded);
            return downloaded.size();
        } catch (IOException exception) {
            downloadFailure = exception;
        }

        if (Files.isRegularFile(cacheFile)) {
            try {
                List<Country> cached = parse(Files.readString(cacheFile, StandardCharsets.UTF_8));
                this.countries = cached;
                this.knownAnswers = collectAnswers(cached);
                return cached.size();
            } catch (IOException cacheFailure) {
                downloadFailure.addSuppressed(cacheFailure);
            }
        }

        throw downloadFailure;
    }

    List<Country> parse(String json) throws IOException {
        Map<String, String> entries = new TreeMap<>();
        Matcher matcher = JSON_ENTRY.matcher(json);
        while (matcher.find()) {
            String code = unescape(matcher.group(1)).toUpperCase(Locale.ROOT);
            String name = unescape(matcher.group(2));
            if (code.matches("[A-Z]{2}") && !NON_COUNTRY_CODES.contains(code)) {
                entries.put(code, name);
            }
        }

        if (entries.size() < 190) {
            throw new IOException("FlagCDN country list contained only " + entries.size() + " usable flags");
        }

        List<Country> loaded = new ArrayList<>(entries.size());
        entries.forEach((code, name) -> loaded.add(createCountry(code, name)));
        return List.copyOf(loaded);
    }

    boolean isKnownCountryAnswer(String answer) {
        return knownAnswers.contains(AnswerNormalizer.normalize(answer));
    }

    private static List<Country> buildLocaleFallback() {
        Map<String, String> entries = new TreeMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale locale = new Locale.Builder().setRegion(code).build();
            entries.put(code, locale.getDisplayCountry(Locale.ENGLISH));
        }
        entries.put("XK", "Kosovo");

        List<Country> fallback = new ArrayList<>(entries.size());
        entries.forEach((code, name) -> fallback.add(createCountry(code, name)));
        return List.copyOf(fallback);
    }

    private static Country createCountry(String code, String englishName) {
        Locale countryLocale = new Locale.Builder().setRegion(code).build();
        String hebrewName = countryLocale.getDisplayCountry(HEBREW);
        if (hebrewName.isBlank() || hebrewName.equalsIgnoreCase(code)) {
            hebrewName = englishName;
        }

        Set<String> answers = new HashSet<>();
        addAnswer(answers, englishName);
        addAnswer(answers, hebrewName);
        EXTRA_ALIASES.getOrDefault(code, List.of()).forEach(alias -> addAnswer(answers, alias));
        return new Country(code, englishName, hebrewName, Set.copyOf(answers));
    }

    private static Set<String> collectAnswers(List<Country> countries) {
        Set<String> answers = new HashSet<>();
        countries.forEach(country -> answers.addAll(country.answers()));
        return Set.copyOf(answers);
    }

    private static String download(URI source) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) source.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "FlagGame/1.0");

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("FlagCDN country list returned HTTP " + status);
        }

        try (InputStream input = connection.getInputStream()) {
            byte[] bytes = input.readNBytes(1_000_001);
            if (bytes.length > 1_000_000) {
                throw new IOException("FlagCDN country list was unexpectedly large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void saveCache(Path cacheFile, String json) throws IOException {
        Files.createDirectories(cacheFile.getParent());
        Path temporary = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, cacheFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String unescape(String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (++index >= value.length()) {
                throw new IOException("Invalid JSON escape");
            }

            char escaped = value.charAt(index);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        throw new IOException("Invalid JSON unicode escape");
                    }
                    try {
                        result.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    } catch (NumberFormatException exception) {
                        throw new IOException("Invalid JSON unicode escape", exception);
                    }
                    index += 4;
                }
                default -> throw new IOException("Unsupported JSON escape: \\" + escaped);
            }
        }
        return result.toString();
    }

    private static void addAnswer(Set<String> answers, String answer) {
        String normalized = AnswerNormalizer.normalize(answer);
        if (!normalized.isBlank()) {
            answers.add(normalized);
        }
    }

    private static Map<String, List<String>> createAliases() {
        Map<String, List<String>> aliases = new HashMap<>();
        alias(aliases, "BO", "Bolivia");
        alias(aliases, "BN", "Brunei");
        alias(aliases, "CD", "Democratic Republic of the Congo", "DR Congo", "Congo Kinshasa",
                "הרפובליקה הדמוקרטית של קונגו", "קונגו קינשאסה");
        alias(aliases, "CG", "Republic of the Congo", "Congo Brazzaville", "קונגו ברזוויל");
        alias(aliases, "CI", "Ivory Coast", "Cote d'Ivoire", "Cote d Ivoire", "חוף השנהב");
        alias(aliases, "CV", "Cape Verde", "כף ורדה");
        alias(aliases, "CZ", "Czech Republic", "Czechia", "צ'כיה", "צכיה");
        alias(aliases, "FM", "Micronesia");
        alias(aliases, "GB", "United Kingdom", "UK", "Britain", "Great Britain",
                "בריטניה", "הממלכה המאוחדת");
        alias(aliases, "IR", "Iran");
        alias(aliases, "KP", "North Korea", "DPRK", "צפון קוריאה");
        alias(aliases, "KR", "South Korea", "Republic of Korea", "דרום קוריאה");
        alias(aliases, "LA", "Laos");
        alias(aliases, "MD", "Moldova");
        alias(aliases, "MK", "North Macedonia", "Macedonia", "צפון מקדוניה", "מקדוניה");
        alias(aliases, "MM", "Myanmar", "Burma", "בורמה", "מיאנמר", "מיאנמאר");
        alias(aliases, "PS", "Palestine", "State of Palestine", "פלסטין");
        alias(aliases, "RU", "Russia");
        alias(aliases, "SZ", "Eswatini", "Swaziland", "סווזילנד");
        alias(aliases, "SY", "Syria");
        alias(aliases, "TL", "Timor-Leste", "East Timor", "מזרח טימור");
        alias(aliases, "TR", "Türkiye", "Turkiye", "Turkey", "טורקיה", "תורקיה");
        alias(aliases, "TZ", "Tanzania");
        alias(aliases, "US", "United States", "United States of America", "USA", "America",
                "ארצות הברית", "אמריקה");
        alias(aliases, "VA", "Vatican City", "Vatican", "קריית הוותיקן", "הוותיקן");
        alias(aliases, "VE", "Venezuela");
        alias(aliases, "VN", "Vietnam", "Viet Nam");
        alias(aliases, "AE", "United Arab Emirates", "UAE", "Emirates", "איחוד האמירויות");
        return aliases;
    }

    private static void alias(Map<String, List<String>> aliases, String code, String... values) {
        aliases.put(code, Arrays.asList(values));
    }
}
