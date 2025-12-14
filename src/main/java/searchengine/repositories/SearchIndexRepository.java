package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SearchIndex;

import java.util.List;

@Repository
public interface SearchIndexRepository extends JpaRepository<SearchIndex, Integer> {
    void deleteByPage(Page oldPage);

    @Query("""
        SELECT si.page.id
        FROM SearchIndex si
        WHERE si.lemma.lemma = :lemma
    """)
    List<Integer> findPageIdByLemma(@Param("lemma") String lemma);


    @Query("""
        SELECT si.rank
        FROM SearchIndex si
        WHERE si.page.id = :pageId
          AND si.lemma.lemma = :lemma
    """)
    Float findRankByPageIdAndLemma(
            @Param("pageId") int pageId,
            @Param("lemma") String lemma
    );
}
