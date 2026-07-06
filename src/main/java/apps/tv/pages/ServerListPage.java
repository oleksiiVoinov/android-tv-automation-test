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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server list screen (TvServerListActivity): title, search, sort, and the scrollable server list.
 * Locators verified on device. Interaction is D-pad driven; selecting a server connects and
 * redirects back to the main screen.
 */
public class ServerListPage extends BasePage {

    private static final String PKG = "com.free.vpn.super.hotspot.open:id/";
    private static final int MAX_LIST_STEPS = 150;   // scrolling the full server list (long)
    private static final int MAX_SEARCH_STEPS = 20;  // navigating the few filtered search results

    // Header
    public final By title = By.id(PKG + "tv_title");                    // "Select Server Location"
    public final By searchIcon = By.id(PKG + "searchIcon");
    public final By sortContainer = By.id(PKG + "sort_server_container");
    public final By sortOption = By.id(PKG + "tv_sort_option");         // current sort label
    // List
    public final By serverList = By.id(PKG + "lv_server_list");
    public final By allServersTitle = By.id(PKG + "tv_all_server_title");
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
        Assert.assertEquals(textOf(title), "Select Server Location", "Wrong server list title");
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
     * Selects a single row by its visible text (flat list — no cluster to open).
     */
    @Step("Select row {name} from the list")
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
    @Step("Select cluster {server}")
    private void selectCluster(String text) {
        System.out.println("🔎 scrollToRow: seeking '" + text + "'");
        By exact = AppiumBy.androidUIAutomator("new UiSelector().text(\"" + text + "\")");
        Set<String> seenWindows = new HashSet<>();
        for (int step = 0; step < MAX_LIST_STEPS; step++) {
            String focused = focusedRowText();
            if (text.equals(focused)) {
                System.out.println("✅ scrollToRow: found '" + text + "' at step " + step);
                return;
            }
            boolean visibleSomewhere = !appiumDriver.findElements(exact).isEmpty();
            String signature = visibleWindow() + "@" + focusedBounds();
            System.out.println("   step " + step + " focused='" + focused + "' targetVisible=" + visibleSomewhere);
            // Signature = visible rows + focus position. It changes while the focus moves within the
            // viewport (top of list) and while content scrolls; it repeats only when the list wraps
            // back to a state we've seen or a DPAD_DOWN does nothing (true dead end). If that happens
            // and the target isn't even on screen, we've been through everything — stop.
            if (!seenWindows.add(signature) && !visibleSomewhere) {
                System.out.println("⛔ scrollToRow: list cycled without '" + text + "' — giving up");
                break;
            }
            dpad.down();
        }
        if (text.equals(focusedRowText())) {
            return;
        }
        throw new NoSuchElementException("Could not find server '" + text + "' in the server list");
    }

    @Step("Select server {server}")
    private void selectAliasName(String text) {
        List<WebElement> elementList = appiumDriver
                .findElement(By.id("com.free.vpn.super.hotspot.open:id/server_popup_items_container"))
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
