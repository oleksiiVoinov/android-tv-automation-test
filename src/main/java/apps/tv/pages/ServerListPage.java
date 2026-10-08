package apps.tv.pages;

import apps.tv.api.serverlist.ServerV7;
import driver.TestContext;
import io.appium.java_client.AppiumBy;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.testng.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server list screen (TvServerListActivity): title, search, sort, and the scrollable server list.
 * Locators verified on device. Interaction is D-pad driven; selecting a server connects and
 * redirects back to the main screen.
 *
 * <p>The list has three sections, top to bottom (verified on device):
 * <ol>
 *   <li>"Fastest server" — one row, labelled with the <b>country</b> (e.g. "Germany");</li>
 *   <li>"Recently used" — up to 3 leaf servers by alias ("Japan - 1"), most recent first, no
 *       duplicates; absent on a fresh install. Selecting one connects <b>immediately</b>
 *       (no cluster popup, no reconnect dialog);</li>
 *   <li>"ALL Servers" — country clusters; selecting one opens the popup with its servers.</li>
 * </ol>
 * The same text can therefore appear in several sections ("Germany" as fastest and as a cluster),
 * so {@link #selectServer} only accepts rows below the "ALL Servers" header, and the recent
 * section has its own {@link #selectRecentServer}.
 */
public class ServerListPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";
    private static final int MAX_LIST_STEPS = 150;   // scrolling the full server list (long)
    private static final int MAX_SEARCH_STEPS = 20;  // navigating the few filtered search results

    public static final String FASTEST_SERVER = "Fastest server";
    public static final String RECENTLY_USED = "Recently used";

    // Header
    public final By title = By.id(PKG + "tv_title");                    // "Select Server Location"
    public final By searchIcon = By.id(PKG + "searchIcon");
    public final By sortContainer = By.id(PKG + "sort_server_container");
    public final By sortOption = By.id(PKG + "tv_sort_option");         // current sort label
    // List
    public final By serverList = By.id(PKG + "lv_server_list");
    public final By allServersTitle = By.id(PKG + "tv_all_server_title");   // "ALL Servers" header
    public final By sectionTitle = By.id(PKG + "tv_section_title");         // "Fastest server" / "Recently used"
    private final By fastestServerTitle = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceId(\"" + PKG + "tv_section_title\").text(\"" + FASTEST_SERVER + "\")");
    // Something inside the list holds the focus (UP from the top row moves it out to the toolbar).
    private final By focusInList = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceId(\"" + PKG + "lv_server_list\").childSelector(new UiSelector().focused(true))");
    private final By recentlyUsedTitle = AppiumBy.androidUIAutomator(
            "new UiSelector().resourceId(\"" + PKG + "tv_section_title\").text(\"" + RECENTLY_USED + "\")");
    // Headers + row names of the whole list, in document (= on-screen) order — used to read the sections.
    private final By listRowsAndHeaders = By.xpath("//*[@resource-id='" + PKG + "tv_section_title' or @resource-id='"
            + PKG + "tv_name' or @resource-id='" + PKG + "tv_all_server_title']");
    public final By serverName = By.id(PKG + "tv_name");               // row name in the main list
    // Search
    public final By searchField = By.id(PKG + "et_server_search");
    public final By searchResults = By.id(PKG + "rv_server_search_results");
    public final By searchResultName = By.id(PKG + "tv_search_server_name");
    // Main screen (redirect target after selecting a server)
    private final By connectButton = By.id(PKG + "tvConnectButton");
    private final By search_result = By.id(PKG + "rv_server_search_results");

    /**
     * Sort options in the sort dialog.
     */
    public enum Sort {
        FASTEST("tv_sort_fastest", "Fastest"),
        A_TO_Z("tv_sort_a_to_z", "A - Z"),
        Z_TO_A("tv_sort_z_to_a", "Z - A");

        final By locator;
        public final String label;

        Sort(String id, String label) {
            this.locator = By.id(PKG + id);
            this.label = label;
        }
    }

    public ServerListPage(TestContext testContext) {
        super(testContext);
    }

    @Step("Verify server list screen is displayed")
    public ServerListPage verifyDisplayed() {
        Assert.assertEquals(textOf(title), "Select server location", "Wrong server list title");
        Assert.assertTrue(isDisplayed(searchIcon), "Search icon not displayed");
        Assert.assertTrue(isDisplayed(sortContainer), "Sort control not displayed");
        Assert.assertTrue(isDisplayed(allServersTitle), "'ALL Servers' section not displayed");
        Assert.assertFalse(appiumDriver.findElements(serverName).isEmpty(), "No server rows displayed");
        return this;
    }

    public String currentSortMode() {
        return textOf(sortOption);
    }

    // ---- Selecting a server from the main list ----

    /**
     * Scrolls the list with the D-pad until the row named {@code name} is focused, then activates it.
     * Selecting a server connects and redirects to the main screen.
     */
    /**
     * Selects the given API server. "ALL Servers" is grouped by country cluster, so we open the
     * cluster (by country name) and then pick the exact server inside it.
     */
    public MainScreenPage selectServer(ServerV7 server) {
        selectCluster(server.getCountryName());   // cluster, e.g. "Australia"
        dpad.center();                           // expand the cluster
        pause(Duration.ofSeconds(1));
        selectAliasName(server.getAliasName());      // server inside, e.g. "Australia - 2"
        dpad.center();                           // select → connects, redirect to main
        return waitForMainScreen();
    }

    /**
     * Selects a single row of "ALL Servers" by its visible text (flat list — no cluster to open).
     */
    @Step("Select row {name} from ALL Servers")
    public MainScreenPage selectServer(String name) {
        selectCluster(name);
        dpad.center();
        return waitForMainScreen();
    }

    /**
     * Scrolls the list downward with the D-pad until the focused row contains {@code text}.
     * Works for both cluster rows (tv_name) and expanded inner rows (plain TextView, no id).
     * <p>
     * End-of-list is detected by the focused row's <b>text</b> not changing — NOT by its bounds:
     * a RecyclerView keeps the highlight at a fixed screen position and scrolls content underneath,
     * so bounds stay constant while we're actually advancing.
     */
    @Step("Select cluster {text} in ALL Servers")
    private void selectCluster(String text) {
        System.out.println("🔎 scrollToRow: seeking '" + text + "'");
        skipToAllServers();   // only "ALL Servers" rows may match (see class doc)
        By exact = AppiumBy.androidUIAutomator("new UiSelector().text(\"" + text + "\")");
        Set<String> seenWindows = new HashSet<>();
        for (int step = 0; step < MAX_LIST_STEPS; step++) {
            String focused = focusedRowText();
            if (text.equals(focused)) {
                return;
            }
            boolean visibleSomewhere = !appiumDriver.findElements(exact).isEmpty();
            String signature = visibleWindow() + "@" + focusedBounds();
            //System.out.println("   step " + step + " focused='" + focused + "' targetVisible=" + visibleSomewhere);
            // Signature = visible rows + focus position. It changes while the focus moves within the
            // viewport (top of list) and while content scrolls; it repeats only when the list wraps
            // back to a state we've seen or a DPAD_DOWN does nothing (true dead end). If that happens
            // and the target isn't even on screen, we've been through everything — stop.
            if (!seenWindows.add(signature) && !visibleSomewhere) {
                //System.out.println("⛔ scrollToRow: list cycled without '" + text + "' — giving up");
                break;
            }
            dpad.down();
        }
        if (text.equals(focusedRowText())) {
            return;
        }
        throw new NoSuchElementException("Could not find server '" + text + "' in the 'ALL Servers' list");
    }

    @Step("Select server {server}")
    private void selectAliasName(String text) {
        pause(Duration.ofSeconds(1));
        List<WebElement> elementList = fluentVisibility(
                By.id("com.free.vpn.super.hotspot.open:id/server_popup_items_container"))
                .findElements(By.className("android.widget.TextView"));

        for (int i = 0; i < elementList.size(); i++) {
            if (elementList.get(0).getText().equals(text)) {
                return;
            } else {
                elementList = appiumDriver
                        .findElement(By.id("com.free.vpn.super.hotspot.open:id/server_popup_items_container"))
                        .findElements(By.className("android.widget.TextView"));
                String isFocused = elementList.get(i).getAttribute("focused");
                if (elementList.get(i).getText().equals(text) && isFocused.equals("true")) {
                    return;
                } else {
                    dpad.down();
                    pause(Duration.ofSeconds(1));
                }
            }
        }
        throw new NoSuchElementException("Could not find server '" + text + "' in the server list");
    }

    /**
     * Bounds of the currently focused element as a string (part of the anti-cycle signature).
     */
    private String focusedBounds() {
        var els = appiumDriver.findElements(AppiumBy.androidUIAutomator("new UiSelector().focused(true)"));
        if (els.isEmpty()) {
            return "";
        }
        Rectangle r = els.get(0).getRect();
        return r.getX() + "," + r.getY() + "," + r.getWidth() + "x" + r.getHeight();
    }

    /**
     * Signature of everything currently visible (all TextView texts) — used to detect list wrap/end.
     */
    private String visibleWindow() {
        return appiumDriver.findElements(AppiumBy.className("android.widget.TextView")).stream()
                .map(e -> {
                    try {
                        return e.getText();
                    } catch (Exception ignored) {
                        return "";
                    }
                })
                .collect(Collectors.joining("|"));
    }

    /**
     * Text of the first TextView inside the currently focused row (its name), or null.
     */
    private String focusedRowText() {
        By loc = AppiumBy.androidUIAutomator(
                "new UiSelector().focused(true).childSelector(new UiSelector().className(\"android.widget.TextView\"))");
        var elements = appiumDriver.findElements(loc);
        return elements.isEmpty() ? null : elements.get(0).getText().trim();
    }

    // ---- Sections: Recently used ----

    /**
     * Names in the "Recently used" section, top (most recent) first; empty on a fresh install.
     * Scrolls the list to the top first — the section titles must be on screen to tell the sections apart.
     */
    public List<String> recentServers() {
        focusTopOfList();   // section titles are only on screen while the list is scrolled to the top
        return readRecentOnScreen();
    }

    /** Parses the "Recently used" names from what is on screen — valid only with the list at the top. */
    private List<String> readRecentOnScreen() {
        List<String> recent = new ArrayList<>();
        boolean inRecent = false;
        for (WebElement e : appiumDriver.findElements(listRowsAndHeaders)) {
            String id = e.getAttribute("resource-id");
            String text = e.getText().trim();
            if (id.endsWith("tv_all_server_title")) {
                break;
            }
            if (id.endsWith("tv_section_title")) {
                inRecent = RECENTLY_USED.equals(text);
            } else if (inRecent) {
                recent.add(text);
            }
        }
        return recent;
    }

    /** Asserts the "Recently used" section starts with {@code expectedTop}, in this order. */
    @Step("Verify Recently used starts with {expectedTop}")
    public ServerListPage verifyRecentlyUsed(String... expectedTop) {
        List<String> recent = recentServers();
        attachScreenToReport("Recently used");
        Assert.assertTrue(recent.size() >= expectedTop.length,
                "Recently used has " + recent + ", expected it to start with " + List.of(expectedTop));
        Assert.assertEquals(recent.subList(0, expectedTop.length), List.of(expectedTop),
                "Wrong Recently used order (most recent must be first)");
        return this;
    }

    /** Selects a server from "Recently used" — connects right away and returns to main. */
    public MainScreenPage selectRecentServer(ServerV7 server) {
        return selectRecentServer(server.getAliasName());
    }

    /**
     * Moves the focus down through the top sections until the "Recently used" row named {@code name}
     * is focused, then activates it. Never touches "Fastest server" or "ALL Servers" rows with the
     * same label. Selecting connects immediately and redirects to the main screen.
     */
    @Step("Select {name} from Recently used")
    public MainScreenPage selectRecentServer(String name) {
        List<String> recent = recentServers();   // also leaves the focus on the "Fastest server" row
        int index = recent.indexOf(name);
        if (index < 0) {
            throw new NoSuchElementException("'" + name + "' is not in Recently used: " + recent);
        }
        // Count rows instead of looking at section titles: one DOWN already scrolls the focused row
        // to the top of the viewport, and the titles go off screen (verified on device).
        for (int i = 0; i <= index; i++) {
            dpad.down();   // Fastest server → recent[0] → recent[1] ...
        }
        String focused = focusedRowText();
        Assert.assertEquals(focused, name, "Focus did not land on the Recently used row " + name);
        dpad.center();
        return waitForMainScreen();
    }

    /**
     * Moves the focus onto the first "ALL Servers" cluster: from the top, skip the single
     * "Fastest server" row and every "Recently used" row.
     */
    private void skipToAllServers() {
        int topRows = 1 + recentServers().size();
        for (int i = 0; i < topRows; i++) {
            dpad.down();
        }
    }

    /**
     * Brings the focus to the first row of the list ("Fastest server"), so the section titles are on
     * screen. Needed because the list opens with the focus on the currently selected server — after a
     * disconnect that is its cluster deep inside ALL Servers, with no section title visible.
     * UP from the top row leaves the list (toolbar) — then one DOWN brings it back onto that row.
     */
    @Step("Focus the top of the server list")
    public ServerListPage focusTopOfList() {
        for (int step = 0; step < MAX_LIST_STEPS; step++) {
            if (!isPresent(focusInList)) {
                dpad.down();   // overshot into the toolbar — back onto the first row, then re-check
                continue;
            }
            if (isFocusedOnFastestServer()) {
                return this;
            }
            dpad.up();
        }
        throw new IllegalStateException("Could not reach the top of the server list");
    }

    /** True when the focused row sits right under the "Fastest server" title (before the next section). */
    private boolean isFocusedOnFastestServer() {
        Integer focusY = focusedY();
        List<WebElement> title = appiumDriver.findElements(fastestServerTitle);
        if (focusY == null || title.isEmpty() || title.get(0).getRect().getY() > focusY) {
            return false;
        }
        List<WebElement> next = appiumDriver.findElements(recentlyUsedTitle);
        if (next.isEmpty()) {
            next = appiumDriver.findElements(allServersTitle);
        }
        return next.isEmpty() || next.get(0).getRect().getY() > focusY;
    }

    @Step("Back to the main screen")
    public MainScreenPage backToMainScreen() {
        dpad.back();
        return waitForMainScreen();
    }

    private Integer focusedY() {
        var els = appiumDriver.findElements(AppiumBy.androidUIAutomator("new UiSelector().focused(true)"));
        return els.isEmpty() ? null : els.get(0).getRect().getY();
    }

    // ---- Search ----

    @Step("Search servers for '{query}'")
    public ServerListPage search(String query) {
        dpad.focusOnAndSelect(searchIcon);
        fluentVisibility(searchField, Duration.ofSeconds(10));
        testContext.getAndroidDriver().findElement(searchField).sendKeys(query);
        fluentVisibility(searchResults, Duration.ofSeconds(10));
        fluentVisibility(searchResultName, Duration.ofSeconds(10));
        return this;
    }

    /**
     * Selects a search result by name (moves focus into the results list first).
     */
    @Step("Select search result {name}")
    public MainScreenPage selectSearchResult(String name) {
        String previous = null;
        for (int step = 0; step < MAX_SEARCH_STEPS; step++) {
            dpad.down(); // from the field into the results, then row by row
            String current = focusedName(PKG + "tv_search_server_name");
            if (name.equals(current)) {
                dpad.center();
                return waitForMainScreen();
            }
            if (current != null && current.equals(previous)) {
                break;
            }
            previous = current;
        }
        throw new NoSuchElementException("Search result '" + name + "' not found");
    }

    @Step("Select search result {name}")
    public MainScreenPage selectSearchResult() throws InterruptedException {
        tap(search_result);
        pause(Duration.ofSeconds(1));
        tap(search_result);
        return waitForMainScreen();
    }

    // ---- Sort ----

    @Step("Sort servers by {mode}")
    public ServerListPage sortBy(Sort mode) {
        dpad.focusOnAndSelect(sortContainer);
        fluentVisibility(mode.locator, Duration.ofSeconds(10));
        dpad.focusOnAndSelect(mode.locator);
        // dialog closes; the toolbar label reflects the chosen mode
        waitForText(sortOption, mode.label, Duration.ofSeconds(10));
        return this;
    }

    // ---- helpers ----

    /**
     * Text of {@code childId} inside the currently focused row, or null if none.
     */
    private String focusedName(String childId) {
        By locator = AppiumBy.androidUIAutomator(
                "new UiSelector().focused(true).childSelector(new UiSelector().resourceId(\"" + childId + "\"))");
        var elements = appiumDriver.findElements(locator);
        return elements.isEmpty() ? null : elements.get(0).getText().trim();
    }

    private MainScreenPage waitForMainScreen() {
        MainScreenPage main = new MainScreenPage(testContext);
        fluentVisibility(connectButton, Duration.ofSeconds(30));
        return main;
    }
}
