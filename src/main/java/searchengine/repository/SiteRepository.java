package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;
import searchengine.model.Status;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface SiteRepository extends JpaRepository<Site, Integer> {
    Optional<Site> findByName(String name);

    List<Site> findSiteEByName(String name);

    int countByNameAndStatus(String name, Status indexing);

    boolean existsByName(String name);

    Site getSiteEBySiteId(int siteId);
}
