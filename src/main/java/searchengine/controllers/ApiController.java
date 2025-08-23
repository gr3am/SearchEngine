package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingServiceImpl;
import searchengine.services.StatisticsService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;

    public ApiController(StatisticsService statisticsService, IndexingServiceImpl indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
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
}
