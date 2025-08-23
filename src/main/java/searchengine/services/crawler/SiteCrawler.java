package searchengine.services.crawler;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.IndexingConfig;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.ThreadLocalRandom;

public class SiteCrawler extends RecursiveAction {
    private static final Logger logger = LoggerFactory.getLogger(SiteCrawler.class);

    private final String url;
    private final Site site;
    private final IndexingConfig indexingConfig;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final Set<String> visited;
    private final TransactionTemplate transactionTemplate;

    public SiteCrawler(Site site, String url, IndexingConfig indexingConfig,
                       SiteRepository siteRepository, PageRepository pageRepository,
                       Set<String> visited, TransactionTemplate transactionTemplate) {
        this.site = site;
        this.url = url;
        this.indexingConfig = indexingConfig;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.visited = (visited != null) ? visited : ConcurrentHashMap.newKeySet();
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected void compute() {
        if (Thread.currentThread().isInterrupted()) {
            logger.debug("Поток прерван, пропускаем URL: {}", url);
            return;
        }

        String path = getPath(url, site.getUrl());
        if (path == null) {
            logger.debug("Некорректный путь, пропускаем URL: {}", url);
            return;
        }

        if (!visited.add(path)) {
            logger.debug("URL уже посещен: {}", url);
            return;
        }

        logger.info("Обрабатываем URL: {}", url);

        try {
            Thread.sleep(getRandomDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(indexingConfig.getUserAgent())
                    .referrer(indexingConfig.getReferrer())
                    .timeout(10000)
                    .ignoreHttpErrors(true)
                    .execute();

            int code = response.statusCode();
            String contentType = response.contentType();
            boolean isHtml = contentType != null && contentType.toLowerCase().startsWith("text/html");

            Document doc = null;
            String content;

            if (code < 400 && isHtml) {
                doc = response.parse();
                content = doc.outerHtml();
            } else {
                content = "";
            }

            transactionTemplate.execute(status -> {
                Page page = new Page();
                page.setSite(site);
                page.setPath(path);
                page.setCode(code);
                page.setContent(content);
                pageRepository.save(page);
                return null;
            });

            transactionTemplate.execute(status -> {
                Site currentSite = siteRepository.findById(site.getId()).orElse(null);
                if (currentSite != null) {
                    currentSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(currentSite);
                }
                return null;
            });

            if (doc != null) {
                Elements links = doc.select("a[href]");
                List<SiteCrawler> subtasks = new ArrayList<>();

                for (Element link : links) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String absUrl = link.absUrl("href");
                    if (absUrl == null || absUrl.isBlank()) continue;
                    if (!absUrl.startsWith(site.getUrl())) continue;

                    int hash = absUrl.indexOf('#');
                    if (hash >= 0) absUrl = absUrl.substring(0, hash);

                    String childPath = getPath(absUrl, site.getUrl());
                    if (childPath == null) continue;

                    if (!visited.contains(childPath)) {
                        subtasks.add(new SiteCrawler(site, absUrl, indexingConfig,
                                siteRepository, pageRepository, visited, transactionTemplate));
                    }
                }

                for (SiteCrawler subtask : subtasks) {
                    subtask.fork();
                }
                for (SiteCrawler subtask : subtasks) {
                    subtask.join();
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка при обработке URL: {}", url, e);
            transactionTemplate.execute(status -> {
                Site currentSite = siteRepository.findById(site.getId()).orElse(null);
                if (currentSite != null) {
                    currentSite.setLastError("Ошибка обхода: " + e.getMessage());
                    currentSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(currentSite);
                }
                return null;
            });
        }
    }

    private int getRandomDelay() {
        return ThreadLocalRandom.current().nextInt(indexingConfig.getMinDelayMillis(),
                indexingConfig.getMaxDelayMillis() + 1);
    }

    private String getPath(String url, String siteUrl) {
        if (!url.startsWith(siteUrl)) return null;
        String path = url.substring(siteUrl.length());
        if (path.isEmpty()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }
}