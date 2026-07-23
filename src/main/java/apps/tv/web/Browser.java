package apps.tv.web;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Thin Selenium (Chrome) wrapper for web flows — e.g. approving the TV device code on the account
 * web frontend. Separate from the Appium/TV driver, so it does NOT extend the Appium {@code BasePage}.
 * Selenium Manager (bundled with Selenium 4) auto-provisions ChromeDriver — no extra dependency.
 */
public class Browser implements AutoCloseable {

    private final ChromeDriver driver;
    private final WebDriverWait wait;

    public Browser() {
        this(true);
    }

    public Browser(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
                "--window-size=1320,900", "--lang=en-US");
        this.driver = new ChromeDriver(options);
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(3));
    }

    public Browser open(String url) {
        driver.get(url);
        return this;
    }

    public String url() {
        return driver.getCurrentUrl();
    }

    /**
     * Waits for the element to be clickable, then types {@code value} (real key events). Retries on
     * a transient not-interactable (modal still animating); falls back to setting the value via JS.
     */
    public Browser type(By locator, String value) {
        WebElement el = wait.until(ExpectedConditions.elementToBeClickable(locator));
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                el.clear();
                el.sendKeys(value);
                return this;
            } catch (org.openqa.selenium.ElementNotInteractableException notReady) {
                sleep(Duration.ofMillis(600));
                el = driver.findElement(locator);
            }
        }
        // Last resort: set via native setter + input event (React-safe).
        setValueByJs(el, value);
        return this;
    }

    private void setValueByJs(WebElement el, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "const i = arguments[0];"
                        + " const s = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
                        + " s.call(i, arguments[1]);"
                        + " i.dispatchEvent(new Event('input', { bubbles: true }));"
                        + " i.dispatchEvent(new Event('change', { bubbles: true }));", el, value);
    }

    public List<WebElement> findAll(By locator) {
        return driver.findElements(locator);
    }

    /** Best-effort type: fills {@code value} if present & interactable; swallows any error. */
    public boolean typeIfPresent(By locator, String value) {
        try {
            List<WebElement> els = driver.findElements(locator);
            if (els.isEmpty()) {
                return false;
            }
            els.get(0).clear();
            els.get(0).sendKeys(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public WebElement waitVisible(By locator, Duration duration) {
        new WebDriverWait(driver, duration)
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Clicks a clickable control by its (case-insensitive) text/value — matches &lt;button&gt;,
     * {@code input[type=submit|button]}, {@code [role=button]} and links. Exact match first, then
     * substring. Returns true if something was clicked.
     */
    public boolean clickButtonByText(String text) {
        Object found = ((JavascriptExecutor) driver).executeScript(
                "const t = arguments[0].toLowerCase();"
                        + " const els = [...document.querySelectorAll(\"button, input[type=submit], input[type=button], [role=button], a\")];"
                        + " const label = e => ((e.textContent || '') + ' ' + (e.value || '')).trim().toLowerCase();"
                        + " const b = els.find(e => label(e) === t) || els.find(e => label(e).includes(t));"
                        + " if (b) b.click(); return !!b;", text);
        return Boolean.TRUE.equals(found);
    }

    /** Sends the Enter key to an element (submits the surrounding form). */
    public void pressEnter(By locator) {
        driver.findElement(locator).sendKeys(org.openqa.selenium.Keys.ENTER);
    }

    /** TEMP diagnostics: JSON snapshot of inputs (with values) and clickable controls on the page. */
    public String debugControls() {
        return (String) ((JavascriptExecutor) driver).executeScript(
                "return JSON.stringify({ url: location.href,"
                        + " inputs: [...document.querySelectorAll('input')].map(i=>({type:i.type,name:i.name,val:i.value,vis:i.offsetParent!==null})),"
                        + " clickables: [...document.querySelectorAll('button,input[type=submit],input[type=button],[role=button]')]"
                        + "   .map(b=>({tag:b.tagName,txt:(b.textContent||'').trim().slice(0,24),val:b.value||'',dis:!!b.disabled})) });");
    }

    public void saveScreenshot(String path) {
        try {
            byte[] png = ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES);
            java.nio.file.Files.write(java.nio.file.Path.of(path), png);
        } catch (Exception ignored) {
        }
    }

    public void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        driver.quit();
    }
}
