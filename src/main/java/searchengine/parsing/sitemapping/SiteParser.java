package searchengine.parsing.sitemapping;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.config.Messages;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.parsing.ParseLemma;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Getter
@Setter
@RequiredArgsConstructor
public class SiteParser {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final ParseLemma parseLemma;

    private int siteId;
    private String domain;
    private String url;

    public void initSiteParser(int siteId, String domain, String url) {
        this.siteId = siteId;
        this.domain = domain;
        this.url = url;
    }

    private ParsePageTask parsePageTask;

    private static AtomicBoolean isCancel = new AtomicBoolean(false);
    public static void setCancel(boolean b) {
        isCancel.set(b);
    }
    public static boolean isCancel() {
        return isCancel.get();
    }

    public void forceStop() {
        setCancel(true);
        pool.shutdownNow();
    }

    private static final int PARALLELISM = 120;
    private ForkJoinPool pool = new ForkJoinPool(PARALLELISM);

    /**
     * Парсинг страниц
     */
    public void getLinks() {
        long delayInMilliseconds = 2;
        pool = new ForkJoinPool(PARALLELISM);
        parsePageTask = preparePage();
        pool.execute(parsePageTask);

        while (!parsePageTask.isDone() && !isCancel()) {
            try {
                Thread.sleep(delayInMilliseconds);
            } catch (InterruptedException ignored) {
            }
        }

        if (isCancel()) {
            pool.shutdownNow();
            forceStop();
            log.info("Отмена индексации... ");
        } else {
            pool.shutdown();
        }
        try {
            parsePageTask.join();
            saveSite();
        } catch (Exception e) {
            log.error("parsePage.join() {}", e.getMessage());
        }
        parsePageTask = null;
    }

    /**
     * Установка значений данных для парсинга страницы
     * @return обьект ParsePageTask
     */
    private ParsePageTask preparePage() {
        parsePageTask = new ParsePageTask(parseLemma, pageRepository);

        parsePageTask.setUrl(url);
        parsePageTask.setDomain(domain);
        parsePageTask.setParent(null);
        parsePageTask.setSiteId(siteId);
        return parsePageTask;
    }

    /**
     * Сохраняет сайт в БД
     */
    private void saveSite() {
        Site site = siteRepository.findById(siteId).orElse(null);
        if (site == null) {
            log.warn("Сайт с ID: {} не найден", site);
            return;
        }
        site.setStatus(isCancel() ? Status.FAILED : Status.INDEXING);
        site.setStatusTime(Utils.setNow());

        getLemmasForAllPages(site);

        site.setStatus(isCancel() ? Status.FAILED : Status.INDEXED);
        site.setLastError(isCancel() ? Messages.INDEXING_STOPPED_BY_USER : "");
        site.setStatusTime(Utils.setNow());
        siteRepository.save(site);
        log.info("***** site '{}' saved", site.getName());
    }

    /**
     * Проход по всем страницам сайта и сохранение лемм и индексов
     * @param site - сущность site
     */
    public void getLemmasForAllPages(Site site) {
        int statusCode = 200;
        List<Page> pageList = pageRepository.findBySiteIdAndCode(site.getSiteId(), statusCode);
        parseLemma.setBeginPos(pageList.get(0).getPageId());
        parseLemma.setEndPos(pageList.get(pageList.size() - 1).getPageId());

        pageList.stream().takeWhile(e -> !isCancel()).forEach(this::parseSinglePage);
    }

    /**
     * Для отдельной страницы парсятся леммы и происходит запись лемм и индексов
     * @param page - страница
     */
    public void parseSinglePage(Page page) {
        parseLemma.setCurrentPos(page.getPageId());
        if (!isCancel()) {
            parseLemma.parsing(page);
        }
    }

    /**
     * Сброс ссылок после выполнения работы
     */
    public void clearUniqueLinks() {
        ParsePageTask.clearUniqueLinks();
    }

    /**
     * Сохраняет страницу в БД
     * @param url - ссылка
     * @param site - сайт
     * @param domain - домен
     * @return - сохранённая строаница
     */
    public Page savePage(String url, Site site, String domain) {
        int statusCode = 200;
        preparePage();
        Document doc = parsePageTask.getDocumentByUrl(url, statusCode);
        parsePageTask.setSiteId(site.getSiteId());
        parsePageTask.setDomain(domain);
        parsePageTask.setUrl(url);
        return parsePageTask.savePage(doc, statusCode);
    }
}
