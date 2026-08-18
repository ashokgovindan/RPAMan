package rpa.rpaman;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drives Chrome through the Automation Anywhere Control Room to collect the
 * Historical activity list, then stores it in SQLite.
 * <p>
 * Selectors are deliberately structural rather than class-based: the table is
 * found by picking the largest one on the page and columns are mapped by their
 * header text. A360 is an Angular app whose generated class names change
 * between releases, so header-driven mapping survives upgrades that CSS
 * selectors would not.
 * <p>
 * Tunable via the Settings table:
 * <ul>
 *   <li>{@code aa.chromeUserDataDir} — Chrome profile root (defaults to the
 *       standard Windows location so existing SSO cookies are reused)</li>
 *   <li>{@code aa.chromeProfile} — profile directory name, default {@code Default}</li>
 *   <li>{@code aa.loginTimeoutSeconds} — how long to wait for SSO, default 180</li>
 *   <li>{@code aa.pageTimeoutSeconds} — per-page wait, default 60</li>
 *   <li>{@code aa.maxRows} — safety cap while lazy-scrolling, default 2000</li>
 * </ul>
 */
public class AaRunHistoryService {

    /** Callback so the dialog can show what the scrape is doing. */
    public interface Progress {
        void update(String message);
    }

    /** Lower-cases an element's text inside an XPath predicate. */
    private static final String LOWER_TEXT =
            "translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')";

    private static final Pattern STARTED_PATTERN =
            Pattern.compile("(\\d{1,2}:\\d{2}:\\d{2}).*?(\\d{4}-\\d{2}-\\d{2})", Pattern.DOTALL);

    private final DatabaseManager dbManager;

    public AaRunHistoryService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    // ------------------------------------------------------------------- run

    /**
     * Runs the full flow: open the Prod URL, sign in via SSO, open
     * Activity &gt; Historical, apply the Last 30 days filter, scrape and store.
     *
     * @return every row that was read from the Control Room
     */
    public List<AaActivity> fetchAndStore(Progress progress) throws Exception {
        String prodUrl = findProdUrl();
        report(progress, "Opening " + prodUrl);

        WebDriver driver = createDriver();
        try {
            int pageTimeout = settingInt("aa.pageTimeoutSeconds", 60);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(pageTimeout));

            driver.get(prodUrl);
            signIn(driver, progress);
            openHistorical(driver, prodUrl, wait, progress);
            applyLast30DaysFilter(driver, progress);

            List<AaActivity> activities = scrapeActivities(driver, progress);
            report(progress, "Saving " + activities.size() + " rows...");
            dbManager.upsertActivities(activities);
            return activities;
        } finally {
            try {
                driver.quit();
            } catch (RuntimeException ignored) {
                // browser already gone
            }
        }
    }

    /** The URL stored in Settings > URLs under the name "Prod". */
    public String findProdUrl() {
        for (String[] item : dbManager.getUrlItems()) {
            if (item.length > 2 && "prod".equalsIgnoreCase(item[1].trim())) {
                String url = item[2].trim();
                if (!url.isEmpty()) {
                    return url.matches("(?i)^[a-z][a-z0-9+.\\-]*://.*") ? url : "https://" + url;
                }
            }
        }
        throw new IllegalStateException(
                "No \"Prod\" URL is configured.\n"
                        + "Add one in View > Settings > URLs (URL Name must be \"Prod\").");
    }

    // ---------------------------------------------------------------- driver

    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();

        String userDataDir = dbManager.getSetting("aa.chromeUserDataDir", defaultChromeUserDataDir());
        if (userDataDir != null && !userDataDir.trim().isEmpty()) {
            // Reuses the everyday profile so existing SSO cookies apply.
            options.addArguments("--user-data-dir=" + userDataDir.trim());
            options.addArguments("--profile-directory="
                    + dbManager.getSetting("aa.chromeProfile", "Default"));
        }
        options.addArguments("--start-maximized");
        options.addArguments("--remote-allow-origins=*");
        options.setExperimentalOption("excludeSwitches", Arrays.asList("enable-automation"));

        try {
            return new ChromeDriver(options);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Could not start Chrome with your normal profile.\n\n"
                            + "Chrome refuses to share a profile that is already open, so close all "
                            + "Chrome windows and try again.\n\n"
                            + "Original error: " + ex.getMessage(), ex);
        }
    }

    private static String defaultChromeUserDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isEmpty()) return "";
        return localAppData + "\\Google\\Chrome\\User Data";
    }

    // ----------------------------------------------------------------- login

    /**
     * Clicks the SSO "Log in" button when the login page is showing, then waits
     * for the Control Room shell. If SSO needs MFA the user completes it in the
     * open browser window and the wait picks it up.
     */
    private void signIn(WebDriver driver, Progress progress) {
        List<WebElement> loginButtons = driver.findElements(By.xpath(
                "//button[" + LOWER_TEXT + "='log in' or " + LOWER_TEXT + "='login'"
                        + " or " + LOWER_TEXT + "='sign in' or " + LOWER_TEXT + "='signin']"));

        if (!loginButtons.isEmpty()) {
            report(progress, "Signing in via SSO...");
            try {
                loginButtons.get(0).click();
            } catch (RuntimeException ex) {
                report(progress, "Could not click the login button: " + ex.getMessage());
            }
        } else {
            report(progress, "Already signed in, continuing...");
        }

        int loginTimeout = settingInt("aa.loginTimeoutSeconds", 180);
        report(progress, "Waiting for the Control Room (up to " + loginTimeout + "s)...");

        WebDriverWait loginWait = new WebDriverWait(driver, Duration.ofSeconds(loginTimeout));
        try {
            loginWait.until((ExpectedCondition<Boolean>) d -> {
                String url = d.getCurrentUrl();
                return url != null && !url.toLowerCase().contains("/login");
            });
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Timed out waiting for sign-in to finish.\n"
                            + "Complete the SSO prompt in the browser window, then try again.", ex);
        }
    }

    // ------------------------------------------------------------ navigation

    /** Goes straight to the Historical route, falling back to menu clicks. */
    private void openHistorical(WebDriver driver, String prodUrl, WebDriverWait wait, Progress progress) {
        report(progress, "Opening Activity > Historical...");

        String base = prodUrl;
        int hash = base.indexOf("/#/");
        if (hash >= 0) base = base.substring(0, hash);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        try {
            driver.get(base + "/#/activity/historical");
            waitForTable(wait);
            return;
        } catch (RuntimeException ex) {
            report(progress, "Direct link failed, using the navigation menu instead...");
        }

        clickByText(driver, "activity");
        clickByText(driver, "historical");
        waitForTable(wait);
    }

    private void waitForTable(WebDriverWait wait) {
        wait.until((ExpectedCondition<Boolean>) d ->
                !d.findElements(By.cssSelector("table tbody tr")).isEmpty());
    }

    private void clickByText(WebDriver driver, String lowerCaseText) {
        List<WebElement> matches = driver.findElements(By.xpath(
                "//a[" + LOWER_TEXT + "='" + lowerCaseText + "']"
                        + "|//span[" + LOWER_TEXT + "='" + lowerCaseText + "']"
                        + "|//button[" + LOWER_TEXT + "='" + lowerCaseText + "']"
                        + "|//div[@role='button'][" + LOWER_TEXT + "='" + lowerCaseText + "']"));
        for (WebElement match : matches) {
            try {
                if (match.isDisplayed()) {
                    match.click();
                    sleep(1200);
                    return;
                }
            } catch (RuntimeException ignored) {
                // stale or covered, try the next candidate
            }
        }
    }

    /**
     * Best-effort selection of the "Last 30 days" time filter. The Control Room
     * already defaults to this, so a failure here is reported but not fatal.
     */
    private void applyLast30DaysFilter(WebDriver driver, Progress progress) {
        report(progress, "Setting the time filter to Last 30 days...");
        try {
            if (driver.findElements(By.xpath(
                    "//*[contains(" + LOWER_TEXT + ",'last 30 days')]")).isEmpty()) {

                clickByText(driver, "time filter");
                List<WebElement> triggers = driver.findElements(By.xpath(
                        "//*[contains(" + LOWER_TEXT + ",'time filter')]"));
                for (WebElement trigger : triggers) {
                    try {
                        if (trigger.isDisplayed()) {
                            trigger.click();
                            sleep(800);
                            break;
                        }
                    } catch (RuntimeException ignored) {
                        // keep looking
                    }
                }
            }

            List<WebElement> options = driver.findElements(By.xpath(
                    "//*[" + LOWER_TEXT + "='last 30 days']"));
            for (WebElement option : options) {
                try {
                    if (option.isDisplayed()) {
                        option.click();
                        sleep(1500);
                        return;
                    }
                } catch (RuntimeException ignored) {
                    // keep looking
                }
            }
            report(progress, "Time filter left at its current value.");
        } catch (RuntimeException ex) {
            report(progress, "Could not change the time filter (" + ex.getMessage() + "), continuing.");
        }
    }

    // --------------------------------------------------------------- scrape

    private List<AaActivity> scrapeActivities(WebDriver driver, Progress progress) {
        WebElement table = findLargestTable(driver);
        if (table == null) {
            throw new IllegalStateException("No activity table was found on the Historical page.");
        }

        loadAllRows(driver, progress);
        table = findLargestTable(driver);

        Map<String, Integer> columns = mapColumns(table);
        List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
        report(progress, "Reading " + rows.size() + " rows...");

        List<AaActivity> activities = new ArrayList<>();
        for (WebElement row : rows) {
            try {
                List<WebElement> cells = row.findElements(By.tagName("td"));
                if (cells.isEmpty()) continue;

                AaActivity activity = new AaActivity();
                activity.status = cell(cells, columns, "status");
                activity.automationType = cell(cells, columns, "automation type");
                activity.activityName = cell(cells, columns, "activity name");
                activity.runningTime = cell(cells, columns, "running time");
                activity.automationName = cell(cells, columns, "automation name");
                activity.deviceName = cell(cells, columns, "device");
                activity.runAsUser = cell(cells, columns, "run as user");
                activity.activityType = cell(cells, columns, "activity type");
                activity.endedOn = flatten(cell(cells, columns, "ended on"));

                activity.startedDisplay = flatten(cell(cells, columns, "started on"));
                activity.startedOn = toIso(activity.startedDisplay);
                activity.buildId();

                if (!activity.id.isEmpty() && !"||".equals(activity.id)) {
                    activities.add(activity);
                }
            } catch (RuntimeException ignored) {
                // a row that re-rendered mid-read is simply skipped
            }
        }
        return activities;
    }

    /** The Historical grid lazy-loads, so scroll until the row count settles. */
    private void loadAllRows(WebDriver driver, Progress progress) {
        int maxRows = settingInt("aa.maxRows", 2000);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        int previous = -1;
        int unchanged = 0;

        for (int attempt = 0; attempt < 80; attempt++) {
            List<WebElement> rows = driver.findElements(By.cssSelector("table tbody tr"));
            int count = rows.size();

            if (count >= maxRows) {
                report(progress, "Reached the " + maxRows + " row cap.");
                return;
            }
            if (count == previous) {
                if (++unchanged >= 3) return;
            } else {
                unchanged = 0;
                report(progress, "Loaded " + count + " rows...");
            }
            previous = count;

            if (rows.isEmpty()) return;
            try {
                js.executeScript("arguments[0].scrollIntoView({block:'end'});",
                        rows.get(rows.size() - 1));
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            } catch (RuntimeException ignored) {
                // element went stale between find and scroll
            }
            sleep(700);
        }
    }

    private WebElement findLargestTable(WebDriver driver) {
        WebElement best = null;
        int bestRows = 0;
        for (WebElement table : driver.findElements(By.tagName("table"))) {
            try {
                int rows = table.findElements(By.cssSelector("tbody tr")).size();
                if (rows > bestRows) {
                    bestRows = rows;
                    best = table;
                }
            } catch (RuntimeException ignored) {
                // skip detached tables
            }
        }
        return best;
    }

    /** Maps lower-cased header text to its column index. */
    private Map<String, Integer> mapColumns(WebElement table) {
        Map<String, Integer> columns = new HashMap<>();
        List<WebElement> headers = table.findElements(By.cssSelector("thead th"));
        for (int i = 0; i < headers.size(); i++) {
            String text = headers.get(i).getText();
            if (text == null) continue;
            text = text.replace('\n', ' ').trim().toLowerCase();
            // Header labels carry a sort arrow suffix; strip anything past the words
            text = text.replaceAll("[^a-z ]", "").trim().replaceAll("\\s+", " ");
            if (!text.isEmpty() && !columns.containsKey(text)) {
                columns.put(text, i);
            }
        }
        return columns;
    }

    private String cell(List<WebElement> cells, Map<String, Integer> columns, String header) {
        Integer index = columns.get(header);
        if (index == null || index >= cells.size()) return "";
        try {
            String text = cells.get(index).getText();
            return text == null ? "" : text.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String flatten(String value) {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Turns "05:53:09 CDT 2026-07-30" into "2026-07-30T05:53:09" for sorting. */
    static String toIso(String display) {
        if (display == null || display.isEmpty()) return "";
        Matcher matcher = STARTED_PATTERN.matcher(display);
        if (!matcher.find()) return "";
        String time = matcher.group(1);
        if (time.length() == 7) time = "0" + time;
        try {
            return LocalDateTime.parse(matcher.group(2) + "T" + time).toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    // ---------------------------------------------------------------- utils

    private int settingInt(String key, int fallback) {
        try {
            return Integer.parseInt(dbManager.getSetting(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void report(Progress progress, String message) {
        if (progress != null) progress.update(message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
