package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        List<Site> sites = siteRepository.findAll();

        int totalSites = sites.size();
        long totalPages = pageRepository.count();
        long totalLemmas = lemmaRepository.count();
        boolean indexing = sites.stream().anyMatch(s -> s.getStatus() == Status.INDEXING);

        TotalStatistics total = new TotalStatistics();
        total.setSites(totalSites);
        total.setPages((int) totalPages);
        total.setLemmas((int) totalLemmas);
        total.setIndexing(indexing);

        List<DetailedStatisticsItem> detailed = sites.stream().map(site -> {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setStatus(site.getStatus().name());
            item.setStatusTime(site.getStatusTime());
            item.setError(site.getLastError());

            int pagesCount = pageRepository.countBySiteId(site.getId());
            int lemmasCount = lemmaRepository.countBySiteId(site.getId());
            item.setPages(pagesCount);
            item.setLemmas(lemmasCount);

            return item;
        }).toList();

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);

        return response;
    }
}
