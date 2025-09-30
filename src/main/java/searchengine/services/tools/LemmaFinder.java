package searchengine.services.tools;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private final LuceneMorphology luceneMorphology;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    public static LemmaFinder getInstance() throws IOException {
        LuceneMorphology morphology = new RussianLuceneMorphology();
        return new LemmaFinder(morphology);
    }

    private LemmaFinder(LuceneMorphology luceneMorphology) {
        this.luceneMorphology = luceneMorphology;
    }

    private LemmaFinder() {
        throw new RuntimeException("Disallow construct");
    }

    public Map<String, Integer> collectLemmas(String text) {
        String[] words = splitToRussianWords(text);
        Map<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) continue;

            List<String> morphInfo = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(morphInfo)) continue;

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) continue;

            String normalForm = normalForms.get(0);

            lemmas.put(normalForm, lemmas.getOrDefault(normalForm, 0) + 1);
        }
        return lemmas;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream()
                .map(String::toUpperCase)
                .anyMatch(base -> Arrays.stream(particlesNames).anyMatch(base::contains));
    }

    private String[] splitToRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^а-я\\s]", " ")
                .trim()
                .split("\\s+");
    }

    public String clearHtml(String html) {
        return Jsoup.parse(html).text();
    }
}
