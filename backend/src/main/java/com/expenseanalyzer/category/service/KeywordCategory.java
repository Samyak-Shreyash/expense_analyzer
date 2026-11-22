package com.expenseanalyzer.category.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Category names suggested by whole-word keywords in a raw description, for rows with no merchant and
 * for new merchants that have no hint. Keywords live in {@code /category/keywords.json}. */
public final class KeywordCategory {

    // order matters (file order): the first keyword found wins
    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    static {
        try (InputStream in = KeywordCategory.class.getResourceAsStream("/category/keywords.json")) {
            Map<String, List<String>> byCategory = JsonMapper.builder().build()
                    .readValue(Objects.requireNonNull(in), new TypeReference<LinkedHashMap<String, List<String>>>() { });
            byCategory.forEach((category, keywords) ->
                    keywords.forEach(k -> KEYWORDS.putIfAbsent(k.toUpperCase(Locale.ROOT), category)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private KeywordCategory() {
    }

    public static Optional<String> of(String rawDescription) {
        if (rawDescription == null) {
            return Optional.empty();
        }
        Set<String> tokens = new HashSet<>(Arrays.asList(rawDescription.toUpperCase(Locale.ROOT).split("[^A-Z]+")));
        return KEYWORDS.entrySet().stream().filter(e -> tokens.contains(e.getKey())).map(Map.Entry::getValue).findFirst();
    }
}
