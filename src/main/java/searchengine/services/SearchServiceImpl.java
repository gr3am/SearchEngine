package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponseDto;
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
        Map<String, Integer> lemmasFromQuery = new HashMap<>();

        try {
            if (query != null && !query.isBlank()) {
                LemmaFinder lemmaFinder = LemmaFinder.getInstance();
                lemmasFromQuery = lemmaFinder.collectLemmas(query);
            }

            List<String> filteredLemmas = filterLemmas(lemmasFromQuery, siteUrl);



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
}
