package apps.tv.api;

import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Manual validation of the API sign-in against the live staging backend.
 * Not part of the smoke suite. Run with a fresh code shown on the TV:
 * <pre>
 *   ./gradlew test --tests "apps.tv.api.TvWebAuthTryTest" -Dcode=ABC123
 * </pre>
 * Optionally override -Demail=... -Dpassword=...
 */
public class TvWebAuthTryTest {

    @Test
    public void authorizesDeviceCode() {
        String email = System.getProperty("tvEmail");
        String password = System.getProperty("tvPassword");
        String code = System.getProperty("code");
        Assert.assertNotNull(email, "Set tvEmail (local.properties or -DtvEmail=...)");
        Assert.assertNotNull(password, "Set tvPassword (local.properties or -DtvPassword=...)");
        Assert.assertNotNull(code, "Pass the TV code via -Dcode=XXXXXX");

        TvWebAuth auth = TvWebAuth.dev();

        String userToken = auth.getUserToken(email, password);
        System.out.println("✅ userToken received (length " + userToken.length() + ")");

        Response response = auth.authorizeDeviceCode(code, userToken);
        System.out.println("↩️  authorize HTTP " + response.statusCode() + " → " + response.asString());

        Assert.assertEquals(response.statusCode(), 200,
                "Expected authorize to succeed for code " + code);
    }
}
