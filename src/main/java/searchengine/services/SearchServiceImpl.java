package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponseDto;
import searchengine.dto.search.SearchResultDto;
import searchengine.model.Page;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.tools.LemmaFinder;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Value("${search.max-lemma-share}")
    private double maxLemmaShare;

    @Value("${search.snippet-length}")
    private int maxSnippetLength;

    @Override
    public SearchResponseDto search(String query, String siteUrl, int offset, int limit) {
        try {
            Map<String, Integer> lemmasFromQuery = new HashMap<>();

            if (query != null && !query.isBlank()) {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                lemmasFromQuery = lemmaFinder.collectLemmas(query);
            }

            List<String> filteredLemmas = filterLemmas(lemmasFromQuery, siteUrl);

            Set<Integer> pageIds = null;

            for (String lemma : filteredLemmas) {
                List<Integer> pages = searchIndexRepository.findPageIdByLemma(lemma);

                if (pageIds == null) {
                    pageIds = new HashSet<>(pages);
                } else {
                    pageIds.retainAll(pages);
                }
            }

            if (pageIds == null || pageIds.isEmpty()) return SearchResponseDto.okEmpty();

            Map<Integer, Float> absRelevance = calculateAbsRelevance(pageIds, filteredLemmas);
            Map<Integer, Float> relRelevance = new HashMap<>();

            float maxAbsRelevance = Collections.max(absRelevance.values());

            absRelevance.forEach((i, r) -> {
                float rel = r / maxAbsRelevance;
                relRelevance.put(i, rel);
            });

            List<SearchResultDto> data = new ArrayList<>();

            for (int id : pageIds) {
                Optional<Page> page = pageRepository.findById(id);
                float relevance = relRelevance.get(id);
                String title = Jsoup.parse(page.get().getContent()).title();
                String snippet = buildSnippet(page.get().getContent(), filteredLemmas, maxSnippetLength);

                data.add(new SearchResultDto(
                        page.get().getSite().getUrl(),
                        page.get().getSite().getName(),
                        page.get().getPath(),
                        title,
                        snippet,
                        relevance
                ));
            }

            data.sort((a, b) -> Float.compare((float) b.getRelevance(), (float) a.getRelevance()));

            int end = Math.min(offset + limit, data.size());
            List<SearchResultDto> paged = data.subList(offset, end);

            return SearchResponseDto.ok(paged, paged.size());
        } catch (IOException e) {
            e.getMessage();
        }

        return SearchResponseDto.okEmpty();
    }

    private List<String> filterLemmas(Map<String, Integer> lemmasFromQuery, String siteUrl) {
        long totalPages = (siteUrl != null)
                ? pageRepository.countBySiteUrl(siteUrl)
                : pageRepository.count();

        long threshold = Math.max(1, Math.round(totalPages * maxLemmaShare));

        Map<String, Long> lemmaFreq = new HashMap<>();

        for (String lemma : lemmasFromQuery.keySet()) {
            Long freq = lemmaRepository.findTotalFrequencyByLemma(lemma);

            if (freq != null && freq > 0 && freq <= threshold) {
                lemmaFreq.put(lemma, freq);
            }
        }

        return lemmaFreq.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<Integer, Float> calculateAbsRelevance(Set<Integer> pageIds, List<String> filteredLemmas) {
        Map<Integer, Float> absRelevance = new HashMap<>();

        for (Integer id : pageIds) {
            float sum = 0;

            for (String lemma : filteredLemmas) {
                Float rank = searchIndexRepository.findRankByPageIdAndLemma(id, lemma);

                if (rank != null) sum += rank;
            }

            absRelevance.put(id, sum);
        }

        return absRelevance;
    }

    private String buildSnippet(String html, List<String> words, int maxSnippetLength) {
        String text = Jsoup.parse(html).text();
        int firstIndex = -1;
        String firstWord = null;

        for (String word : words) {
            int idx = text.toLowerCase().indexOf(word.toLowerCase());

            if (idx != -1 && (firstIndex == -1 || idx < firstIndex)) {
                firstIndex = idx;
                firstWord = word;
            }
        }

        if (firstIndex == -1) {
            String snippet = text.substring(0, Math.min(maxSnippetLength, text.length()));

            for (String word : words) {
                snippet = snippet.replaceAll("(?i)" + word, "<b>" + word + "</b>");
            }
            return snippet;
        }

        int radius = maxSnippetLength / 2;
        int start = Math.max(0, firstIndex - radius);
        int end = Math.min(text.length(), firstIndex + radius);

        String snippet = text.substring(start, end);

        for (String word : words) {
            snippet = snippet.replaceAll("(?i)" + word, "<b>" + word + "</b>");
        }

        return snippet + "...";
    }
}
