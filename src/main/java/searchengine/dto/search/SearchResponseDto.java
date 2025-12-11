package searchengine.dto.search;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponseDto {
    private boolean result;
    private String error;
    private int count;
    private List<SearchResultDto> data;

    public static SearchResponseDto ok(List<SearchResultDto> data, int count) {
        SearchResponseDto response = new SearchResponseDto();
        response.result = true;
        response.data = data;
        response.count = count;
        return response;
    }

    public static SearchResponseDto okEmpty() {
        return ok(List.of(), 0);
    }

    public static SearchResponseDto error(String message) {
        SearchResponseDto response = new SearchResponseDto();
        response.result = false;
        response.error = message;
        return response;
    }
}
