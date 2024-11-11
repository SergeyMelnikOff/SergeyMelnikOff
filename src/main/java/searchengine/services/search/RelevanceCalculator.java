package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Search;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelevanceCalculator {
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    /**
     * Формирует таблицу relevance. Определяет релевантность
     * @param lemmaList  - список лемм для поиска в Index и Page
     * @param searchList - список результатов поиска
     */
    public double[][] formationForOneSite(List<Lemma> lemmaList, int offset, int limit,
                                           List<Search> searchList) {
        List<Search> searchListTemp = new ArrayList<>();
        double[][] relevance;
        double maxRelevance = 0;

        // По первой, самой редкой лемме из списка, находить все страницы, на которых она встречается
        List<Index> indexList = getIndexEForFirstLemma(lemmaList, offset, limit);

        List<Page> pageList = getPageList(indexList);

        relevance = getRelevanceArray(indexList, lemmaList, pageList, searchListTemp);

        maxRelevance = setAbsoluteRelevance(pageList, lemmaList, relevance);
        setRelativeRelevance(pageList, lemmaList, relevance, maxRelevance);

        searchList.addAll(searchListTemp);
        return relevance;
    }

    private List<Page> getPageList(List<Index> indexList) {
        List<Page> pageList = new ArrayList<>();
        for (Index index : indexList) {
            Page page = pageRepository.findByPageId(index.getPageId());
            if (page != null) {
                pageList.add(page);
            }
        }
        return pageList;
    }

    private List<Index> getIndexEForFirstLemma(List<Lemma> lemmaList, int offset, int limit) {
        List<Index> indexList = new ArrayList<>(Objects
                .requireNonNull(indexRepository.findByLemmaId(lemmaList.get(0).getLemmaId())
                        .orElse(null)).stream().skip(offset).limit(limit).toList());

        // Удаление из списка элементов, которых нет по другим леммам
        Iterator<Index> iter = indexList.iterator();
        while (iter.hasNext()) {
            Index index = iter.next();
            int pageId = index.getPageId();
            int i = 1;
            while (i < lemmaList.size()) {
                Index index1 = indexRepository.findByLemmaIdAndPageId(lemmaList.get(i).getLemmaId(), pageId)
                        .orElse(null);
                if (index1 == null) {
                    iter.remove();
                    break;
                }
                i++;
            }
        }
        return indexList;
    }

    private void setRelativeRelevance(List<Page> pageList, List<Lemma> lemmaList,
                                      double[][] relevance, double maxRelevance) {
        for (int j = 0; j < pageList.size(); j++) {
            int ind = lemmaList.size() + 2;
            relevance[j][ind] = relevance[j][ind - 1] / maxRelevance;
        }
    }

    private double setAbsoluteRelevance(List<Page> pageList, List<Lemma> lemmaList,
                                        double[][] relevance) {
        double maxRelevance = 0;
        for (int j = 0; j < pageList.size(); j++) {
            for (int k = 0; k < lemmaList.size() + 3; k++) {
                if (k == lemmaList.size() + 1) {
                    int sumAR = 0;
                    for (int l = 0; l < lemmaList.size(); l++) {
                        sumAR += relevance[j][l + 1];
                    }
                    relevance[j][k] = sumAR;
                    maxRelevance = Double.max(maxRelevance, sumAR);
                }
            }
        }
        return maxRelevance;
    }

    private double[][] getRelevanceArray(List<Index> indexList, List<Lemma> lemmaList,
                                         List<Page> pageList, List<Search> searchListTemp) {
        //          lem1 lem2
        //      0   1    2    3    4        K -кол-во лемм
        //  0   [1] [r1] [r2] [ar] [or]
        //  1   [2] []   []   [ar] [or]
        //
        //  J -кол-во страниц (индексов)
        // Заполняем № и колонку первой леммы
        double[][] relevance = new double[indexList.size()][lemmaList.size() + 3];
        for (int j = 0; j < indexList.size(); j++) {
            for (int k = 0; k < 2; k++) {    //
                if (k == 0) {
                    relevance[j][k] = j + 1.0;
                    Search search = new Search();
                    search.setNumber(j + 1);
                    search.setSiteId(lemmaList.get(0).getSiteId());
                    search.setPageId(indexList.get(j).getPageId());
                    searchListTemp.add(search);
                    continue;
                }
                relevance[j][k] = indexList.get(j).getRank();
            }
        }

        // Поиск соответствия леммы из списка страниц
        // и заполнение колонок следующих лемм
        findingLemmaMatchFromListOfPages(lemmaList, pageList, relevance);

        return relevance;
    }

    private void findingLemmaMatchFromListOfPages(List<Lemma> lemmaList, List<Page> pageList, double[][] relevance) {
        int i = 1;
        while (i < lemmaList.size()) {
            int lemmaId = lemmaList.get(i).getLemmaId();
            List<Index> indexList2 = new ArrayList<>(
                    Objects.requireNonNull(indexRepository.findByLemmaId(lemmaId).orElse(null)));

            if (!pageList.removeIf(page -> indexList2.stream()
                    .noneMatch(indexE -> indexE.getPageId() == page.getPageId()))) {
                // удаляем лишние индексы
                indexList2.removeIf(indexE -> pageList.stream()
                        .noneMatch(page -> page.getPageId() == indexE.getPageId()));
                for (int j = 0; j < indexList2.size(); j++) {
                    relevance[j][i + 1] = indexList2.get(j).getRank();
                }
            }
            i++;
        }
    }
}
