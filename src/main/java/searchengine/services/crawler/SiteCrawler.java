package searchengine.services.crawler;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.IndexingConfig;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
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
    private final String url;
    private final Site site;
    private final IndexingConfig indexingConfig;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final Set<String> visited;
    private final TransactionTemplate transactionTemplate;
    private static volatile boolean stopped = false;

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
        if (stopped || Thread.currentThread().isInterrupted()) {
            return;
        }

        String path = getPath(url, site.getUrl());
        if (path == null) return;
        if (!visited.add(path)) return;

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

            if (!stopped) {
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
                    if (currentSite != null && currentSite.getStatus() == Status.INDEXING) {
                        currentSite.setStatusTime(LocalDateTime.now());
                        siteRepository.save(currentSite);
                    }
                    return null;
                });
            }

            if (doc != null) {
                Elements links = doc.select("a[href]");
                List<SiteCrawler> subtasks = new ArrayList<>();

                for (Element link : links) {
                    if (stopped || Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    String absUrl = link.absUrl("href");
                    if (!isValidLink(absUrl, site.getUrl(), visited)) continue;

                    subtasks.add(new SiteCrawler(site, absUrl, indexingConfig,
                            siteRepository, pageRepository, visited, transactionTemplate));
                }

                invokeAll(subtasks);
            }

        } catch (Exception e) {
            if (!stopped) {
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
    }

    private boolean isValidLink(String link, String rootUrl, Set<String> visited) {
        if (link == null || link.isBlank()) return false;
        if (!link.startsWith(rootUrl)) return false;

        int hash = link.indexOf('#');
        if (hash >= 0) link = link.substring(0, hash);

        if (link.matches(".*(\\.(jpg|jpeg|png|gif|bmp|ico|svg|pdf|doc|docx|xls|xlsx|zip|rar|mp4|avi|mov|wmv|css|js))$")) {
            return false;
        }

        String path = getPath(link, rootUrl);
        return path != null && !visited.contains(path); // только проверяем
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

    public static void stop() {
        stopped = true;
    }

    public static void reset() {
        stopped = false;
    }
}