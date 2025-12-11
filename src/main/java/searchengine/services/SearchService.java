package searchengine.services;

import searchengine.dto.search.SearchResponseDto;

public interface SearchService {
    SearchResponseDto search(String query, String siteUrl, int offset, int limit);
}
