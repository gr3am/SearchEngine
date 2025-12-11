package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.PageIndexingServiceImpl;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;
    private final PageIndexingServiceImpl pageIndexingService;
    private final SearchService searchService;

    public ApiController(StatisticsService statisticsService, IndexingServiceImpl indexingService, PageIndexingServiceImpl pageIndexingService, SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.pageIndexingService = pageIndexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        boolean started = indexingService.startIndexing();
        if (!started) {
            return ResponseEntity.ok(Map.of(
                    "result", false,
                    "error", "Индексация уже запущена"
            ));
        }
        return ResponseEntity.ok(Map.of("result", true));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        boolean stopped = indexingService.stopIndexing();
        if (!stopped) {
            return ResponseEntity.ok(Map.of(
                    "result", false,
                    "error", "Индексация не запущена"
            ));
        }
        return ResponseEntity.ok(Map.of("result", true));
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        boolean result = pageIndexingService.indexPage(url);

        if (!result) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "result", false,
                            "error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"
                    )
            );
        }

        return ResponseEntity.ok(Map.of("result", true));
    }

//    @GetMapping("/search")
//    public ResponseEntity<SearchResponseDto> search(@RequestParam(value = "query", required = false) String query,
//                                                      @RequestParam(value = "site", required = false) String site,
//                                                      @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
//                                                      @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
//
//        return;
//    }
}
