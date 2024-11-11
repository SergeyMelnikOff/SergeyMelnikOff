package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import searchengine.config.SiteList;
import searchengine.dto.Response;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.lemma.LemmaFinder;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final RelevanceCalculator relevanceCalculator;
    private final SnippetFormatter snippetFormatter;

    private final SiteList sites;
    private final LemmaFinder lemmaFinder = LemmaFinder.getInstance();

    /**
     * Метод осуществляет поиск страниц по переданному поисковому запросу (параметр query).
     * @param query  — поисковый запрос;
     * @param site   — сайт, по которому осуществлять поиск (если не задан, поиск должен происходить
     *               по всем проиндексированным сайтам); задаётся в формате адреса, например:
     *               http://www.site.com (без слэша в конце);
     * @param offset — сдвиг от 0 для постраничного вывода (параметр необязательный; если не
     *               установлен, то значение по умолчанию равно нулю);
     * @param limit  — количество результатов, которое необходимо вывести (параметр необязательный;
     *               если не установлен, то значение по умолчанию равно 20).
     * @return response
     */
    @Override
    public Response search(String query, String site, int offset, int limit) {

        List<Search> searchList = new ArrayList<>();
        /* 1. Вывод параметров поискового запроса */
        printInfoBySearch(query, site, offset, limit);

        /* 2. Подготовка данных */
        List<Integer> siteIdList = new ArrayList<>();
        List<String> lemmaListFromQuery = new ArrayList<>();
        List<Lemma> lemmaList = new ArrayList<>();
        Response response = prepareDataForSearch(query, site, siteIdList, lemmaListFromQuery,
                lemmaList);
        if (response != null) {
            return response;
        }

        /* 3. Заполнение и сортировка searchResultsList */
        fillSearchResultsList(siteIdList, lemmaList, offset, limit, searchList);
        if (searchList.isEmpty()) {
            return setResponseFalse("Не найдено");
        }
        sortSearchResultsList(offset, limit, searchList);

        /* 4. Довавление сниппетов */
        setSnippetForSearchResults(lemmaList, searchList);

        return setSearchData(searchList);
    }

    private void printInfoBySearch(String query, String site, int offset, int limit) {
        System.out.println();
        log.info("*****************************************");
        log.info("Поисковый запрос: {}", query);
        log.info("Сайт: {}", site);
        log.info("Сдвиг от 0: {}", offset);
        log.info("Количество результатов: {}", limit);
        log.info("*****************************************");
    }

    /**
     * Подготовка данных для поиска
     * @param query              — поисковый запрос;
     * @param site               — сайт (null если все)
     * @param siteIdList         - список Id сайтов, где есть искомые слова
     * @param lemmaListFromQuery - список слов(лемм) поисковых
     * @param lemmaList          - список найденных сущностей Lemma из БД
     * @return Response - null если всё в порядке
     */
    private Response prepareDataForSearch(
            String query, String site,
            List<Integer> siteIdList, List<String> lemmaListFromQuery, List<Lemma> lemmaList) {

        List<Integer> siteIdListTemp = getSiteIdList(site);
        if (siteIdListTemp.isEmpty()) {
            return setResponseFalse("search site " + site + " not found");
        }

        List<String> lemmaListFromQueryTemp = Objects.requireNonNull(lemmaFinder)
                .collectLemmas(query)
                .keySet()
                .stream().toList();

        List<Lemma> lemmaListTemp = getLemmaList(siteIdListTemp, lemmaListFromQueryTemp);
        if (lemmaListTemp.isEmpty()) {
            return setResponseFalse("search lemmas: not found in database");
        }

        removeIfLimitFrequencyIsBig(lemmaListTemp);
        if (lemmaListTemp.isEmpty()) {
            return setResponseFalse("not found lemmas");
        }

        siteIdListTemp = lemmaListTemp.stream().map(Lemma::getSiteId).distinct().toList();

        lemmaListTemp = lemmaListTemp.stream().sorted(Comparator.comparingInt(Lemma::getFrequency)).toList();

        siteIdList.addAll(siteIdListTemp);
        lemmaListFromQuery.addAll(lemmaListFromQueryTemp);
        lemmaList.addAll(lemmaListTemp);

        return null;
    }

    /**
     * Получает список Id сайтов из конфигурации которые есть в БД
     * @param site if null - all sites
     * @return list of siteIds
     */
    private List<Integer> getSiteIdList(String site) {
        List<Integer> siteIdList = new ArrayList<>();
        if (site == null) {
            List<searchengine.config.Site> siteList = sites.getSites();
            siteIdList = siteList.stream()
                    .map(searchengine.config.Site::getName)
                    .map(s -> {
                        if (siteRepository.existsByName(s)) {
                            return siteRepository.findSiteEByName(s).get(0).getSiteId();
                        } else {
                            return 0;
                        }
                    }).toList();
        } else {
            Optional<searchengine.config.Site> siteFromConfig = sites.getSites().stream()
                    .filter(site1 -> site1.getUrl().equals(site)).findFirst();
            if (siteFromConfig.isPresent()) {
                Site siteE = siteRepository.findByName(siteFromConfig.get().getName())
                        .orElse(null);
                if (siteE != null) {
                    siteIdList.add(siteE.getSiteId());
                }
            }
        }
        return siteIdList.stream().filter(integer -> integer != 0).toList();
    }

    /**
     * Возвращает список сущностей Lemma из DB если количество лемм совпадает
     * @param siteIdList         список siteId
     * @param lemmaListFromQuery список лемм из запроса
     * @return список найденных в БД лемм
     */
    private List<Lemma> getLemmaList(List<Integer> siteIdList, List<String> lemmaListFromQuery) {
        List<Lemma> lemmaList = new ArrayList<>();
        for (Integer siteId : siteIdList) {
            for (String lem : lemmaListFromQuery) {
                lemmaRepository.findBySiteIdAndLemma(siteId, lem).ifPresent(lemmaList::add);
            }
            long countOfWordsFound = lemmaList.stream()
                    .filter(lemma -> siteId.equals(lemma.getSiteId())).count();
            if (countOfWordsFound == 0) {
                continue;
            }
            if (countOfWordsFound != lemmaListFromQuery.size()) {
                lemmaList.removeIf(lemma -> lemma.getSiteId() == siteId);
            }
        }
        return lemmaList;
    }

    /**
     * Удаление из списка поиска лемм которые слишком часто встречаются
     * @param lemmaList список лемм
     */
    private void removeIfLimitFrequencyIsBig(List<Lemma> lemmaList) {
        int limitCount = 1000;
        Iterator<Lemma> iterator = lemmaList.iterator();
        while (iterator.hasNext()) {
            Lemma lemma = iterator.next();
            int countPages = pageRepository.countBySiteId(lemma.getSiteId());

            log.debug("siteId: {} countPages: {} Frequency: {}", lemma.getSiteId(), countPages,
                    lemma.getFrequency());
            if (lemma.getFrequency() >= countPages && countPages > limitCount) {
                log.warn("remove lemma:{}", lemma.getLemma());
                iterator.remove();
            }
        }
    }

    /**
     * Заполнение списка SearchResults
     * @param siteIdList        список siteId
     * @param lemmaList         список лемм
     * @param searchList список
     */
    private void fillSearchResultsList(List<Integer> siteIdList,
                                       List<Lemma> lemmaList, int offset, int limit, List<Search> searchList) {
        double[][] relevance;

        List<Search> searchListTemp = new ArrayList<>();
        for (Integer i : siteIdList) {
            relevance = relevanceCalculator.formationForOneSite(
                    lemmaList.stream().filter(lemma -> lemma.getSiteId() == i).toList(), offset, limit,
                    searchListTemp);

            // заполняем searchResultsList
            for (int j = 0; j < relevance.length; j++) {
                Search results;
                for (Search search : searchListTemp) {
                    results = search;
                    int ind = relevance[j].length - 1;
                    if (results.getNumber() == (j + 1) && results.getSiteId() == i) {
                        results.setRelevance(relevance[j][ind]);
                    }
                }
            }
        }
        searchList.addAll(searchListTemp);
    }

    /**
     * Сортировка списка SearchResults и после применение offset и limit
     */
    private void sortSearchResultsList(int offset, int limit,
                                       List<Search> searchList) {
        List<Search> searchListTemp;

        searchListTemp = searchList.stream()
                .filter(search -> search.getRelevance() != 0.0)
                .sorted(Comparator
                        .comparing(Search::getRelevance)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();

        searchList.clear();
        searchList.addAll(searchListTemp);
    }

    /**
     * Заполнение сниппетами списка SearchResults
     * @param lemmaList         список лемм
     * @param searchList список
     */
    private void setSnippetForSearchResults(List<Lemma> lemmaList,
                                            List<Search> searchList) {
        Iterator<Search> iteratorSR = searchList.iterator();
        Search results;
        while (iteratorSR.hasNext()) {
            results = iteratorSR.next();
            Page page = pageRepository.findByPageId(results.getPageId());
            results.setTitle(page.getTitle());
            results.setUrl(page.getPath());

            String snippet = snippetFormatter.getSnippet(page.getContent(), lemmaList);
            results.setSnippet(snippet);
        }
    }

    /**
     * SearchResults -> searchDataList
     * @return responseTrue
     */
    private Response setSearchData(List<Search> searchList) {
        List<SearchData> searchDataList = new ArrayList<>();
        SearchResponse responseTrue = new SearchResponse();
        responseTrue.setError("");
        responseTrue.setResult(true);
        responseTrue.setCount(searchList.size());
        for (Search search : searchList) {
            Site site = siteRepository.getSiteEBySiteId(search.getSiteId());
            String uri = search.getUrl().endsWith("/") ? search.getUrl()
                    .substring(0, search.getUrl().length() - 1) : search.getUrl();
            SearchData searchData = new SearchData(site.getUrl(),
                    site.getName(),
                    uri,
                    search.getTitle(),
                    search.getSnippet(),
                    search.getRelevance());
            searchDataList.add(searchData);
            log.info("сайт {} релевантность {}", site.getUrl() + uri, searchData.getRelevance());
        }
        System.out.println();
        responseTrue.setData(searchDataList);
        return responseTrue;
    }

    /**
     * response.setResult(true) true- тогда удаляются результаты предыдущего поиска, но не
     * отражается строка ошибки на стороне фронта если false- то оставляет результаты предыдущего поиска
     * @param errorMessage сообщение
     * @return response ответ
     */
    private Response setResponseFalse(String errorMessage) {
        log.warn(errorMessage);

        List<SearchData> searchDataList = new ArrayList<>();
        SearchResponse response = new SearchResponse();
        response.setError(errorMessage);
        response.setResult(true);
        response.setCount(0);
        SearchData searchData = new SearchData("", "", "", "", "", 0);
        searchDataList.add(searchData);
        response.setData(searchDataList);

        return response;
    }
}
