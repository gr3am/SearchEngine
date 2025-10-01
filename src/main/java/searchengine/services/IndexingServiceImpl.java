package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import searchengine.config.IndexingConfig;
import searchengine.config.SiteConfig;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SearchIndexRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.tools.SiteCrawler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Service
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SearchIndexRepository searchIndexRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingConfig indexingConfig;
    private final TransactionTemplate transactionTemplate;
    private final Map<Integer, ForkJoinPool> runningPools = new ConcurrentHashMap<>();
    private final PageIndexingService pageIndexingService;

    @Override
    public boolean startIndexing() {
        if (isIndexingInProgress()) {
            return false;
        }

        SiteCrawler.reset();

        for (SiteConfig siteConfig : indexingConfig.getSites()) {
            Site site = transactionTemplate.execute(status -> {
                siteRepository.findByUrl(siteConfig.getUrl())
                        .ifPresent(oldSite -> {
                            searchIndexRepository.deleteAll();
                            lemmaRepository.deleteAll();
                            pageRepository.deleteBySite(oldSite);
                            siteRepository.delete(oldSite);
                        });

                Site newSite = new Site();
                newSite.setStatus(Status.INDEXING);
                newSite.setStatusTime(LocalDateTime.now());
                newSite.setUrl(siteConfig.getUrl());
                newSite.setName(siteConfig.getName());
                return siteRepository.save(newSite);
            });

            ForkJoinPool forkJoinPool = new ForkJoinPool();
            SiteCrawler siteCrawler = new SiteCrawler(site, site.getUrl(), indexingConfig,
                    siteRepository, pageRepository, null, transactionTemplate, pageIndexingService);

            runningPools.put(site.getId(), forkJoinPool);

            CompletableFuture.runAsync(() -> {
                try {
                    forkJoinPool.execute(siteCrawler);
                    forkJoinPool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.DAYS);

                    updateSite(site.getId(), s -> {
                        if (s.getStatus() == Status.INDEXING) {
                            s.setStatus(Status.INDEXED);
                            s.setStatusTime(LocalDateTime.now());
                        }
                    });
                } catch (Exception e) {
                    updateSite(site.getId(), s -> {
                        s.setStatus(Status.FAILED);
                        s.setLastError("Ошибка индексации: " + e.getMessage());
                        s.setStatusTime(LocalDateTime.now());
                    });
                } finally {
                    forkJoinPool.shutdown();
                    runningPools.remove(site.getId());
                }
            });
        }
        return true;
    }

    @Override
    public boolean stopIndexing() {
        if (!isIndexingInProgress()) {
            return false;
        }

        SiteCrawler.stop();

        runningPools.values().forEach(ForkJoinPool::shutdownNow);

        transactionTemplate.execute(status -> {
            siteRepository.findAll().forEach(site -> {
                if (site.getStatus() == Status.INDEXING) {
                    site.setStatus(Status.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                }
            });
            return null;
        });

        runningPools.clear();
        return true;
    }

    public boolean isIndexingInProgress() {
        return runningPools.values().stream().anyMatch(pool -> !pool.isTerminated());
    }

    private void updateSite(Integer siteId, Consumer<Site> updater) {
        transactionTemplate.execute(status -> {
            siteRepository.findById(siteId).ifPresent(site -> {
                updater.accept(site);
                siteRepository.save(site);
            });
            return null;
        });
    }
}