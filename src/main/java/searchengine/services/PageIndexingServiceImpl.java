package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.IndexingConfig;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.tools.LemmaFinder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class PageIndexingServiceImpl implements PageIndexingService {
    private final LemmaRepository lemmaRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexingConfig indexingConfig;

    @Transactional
    @Override
    public boolean indexPage(String url) {
        String rootUrl = getRootUrl(url);

        if (!siteRepository.existsByUrl(rootUrl)) {
            return false;
        }

        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(indexingConfig.getUserAgent())
                    .referrer(indexingConfig.getReferrer())
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .execute();

            String html = response.body();
            String text = lemmaFinder.clearHtml(html);

            var site = siteRepository.findByUrl(rootUrl).orElseThrow();

            String path = url.replace(rootUrl, "/");

            pageRepository.findByPathAndSite(path, site).ifPresent(oldPage -> {
                searchIndexRepository.deleteByPage(oldPage);
                lemmaRepository.deleteBySite(site);
                pageRepository.delete(oldPage);
            });

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(response.statusCode());

            if (response.statusCode() < 400) {
                page.setContent(html);
            } else {
                page.setContent("");
            }

            pageRepository.save(page);

            Map<String, Integer> lemmas = lemmaFinder.collectLemmas(text);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaText = entry.getKey();
                int count = entry.getValue();

                Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                        .orElseGet(() -> {
                            Lemma l = new Lemma();
                            l.setSite(site);
                            l.setLemma(lemmaText);
                            l.setFrequency(0);
                            return l;
                        });

                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmaRepository.save(lemma);

                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(count);
                searchIndexRepository.save(index);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }


    private String getRootUrl(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Incorrect URL: " + url);
        }
    }
}
