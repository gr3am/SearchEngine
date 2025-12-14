package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findByLemmaAndSite(String lemmaText, Site site);
    void deleteBySite(Site site);
    long count();
    int countBySiteId(int siteId);

    @Query("""
        SELECT SUM(l.frequency)
        FROM Lemma l
        WHERE l.lemma = :lemma
    """)
    Long findTotalFrequencyByLemma(@Param("lemma") String lemma);
}
