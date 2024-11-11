package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface IndexRepository extends JpaRepository<Index, Long> {
    Optional<List<Index>> findByLemmaId(int lemmaId);

    List<Index> findByPageId(int pageId);

    Optional<Index> findByLemmaIdAndPageId(int lemmaId, int pageId);
}
