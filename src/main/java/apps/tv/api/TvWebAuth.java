package apps.tv.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.net.URI;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Programmatic Android TV sign-in via the account backend, replacing the manual QR/device-code flow.
 * <p>
 * Mirrors the web second-screen flow (reverse-engineered from web-frontend-staging):
 * both endpoints take an encrypted body {@code {"data": "<JWE>"}} where the JWE is
 * RSA-OAEP + A256GCM over the plaintext JSON (same scheme as the phone framework's WebPlatform).
 * <ol>
 *   <li>{@code POST /api/internal/customer/verify}  {username,password} → {@code userToken}</li>
 *   <li>{@code POST /api/internal/sessions/authorize}  {code,userToken} → approves the TV device code</li>
 * </ol>
 * After authorize succeeds, the TV app's polling completes and it lands on the main screen.
 */
public class TvWebAuth {

    // Staging (dev) RSA public key — same value the web frontend / phone WebPlatform uses.
    private static final String DEV_PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqCPGAFGbyQHDLrw5YQZCQQKS7PN4szAPzP5N9wQMJwLA"
            + "+VLX55g3d4tMVaofx2UnFGpSNrVu6Cgby3ZgYhkQkFtTwurMFCOj5nd4x+Qcuy7zgqw2sV7s/BccChTIVVReJWC3xwQ"
            + "LrXpAEYBJw0LxdT9+5bkaAT5aTuiaTBquhuw/aKxRvUwmFs8oOy5GqxPxDdAfnRYux3Q7T9bollw4MjoMvy+2otoPKI"
            + "zss4qst4qS3h3HZKhxq2CBIYjd27otrg2almNrZadIqhn2AaG0IIOSjnqkdfPC0qPKuWbCddb+ScP+ft7YP/TxscIy4"
            + "T2q8igUdfnubaymHOgnYiTv+QIDAQAB";

    private static final String VERIFY_PATH = "/api/internal/customer/verify";
    private static final String AUTHORIZE_PATH = "/api/internal/sessions/authorize";

    private final String baseUrl;
    private final String publicKeyPem;
    private final ObjectMapper mapper = new ObjectMapper();

    public TvWebAuth(String baseUrl, String publicKeyPem) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.publicKeyPem = publicKeyPem;
    }

    /** Staging/dev account backend. */
    public static TvWebAuth dev() {
        return new TvWebAuth("https://web-frontend-staging.frontend-qaaccount.superuntest.net", DEV_PUBLIC_KEY_PEM);
    }

    /** Full flow: authenticate the account, then authorize the device code shown on the TV. */
    @Step("Sign in the TV via API with device code {code}")
    public void signInWithCode(String email, String password, String code) {
        String userToken = getUserToken(email, password);
        Response response = authorizeDeviceCode(code, userToken);
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Device-code authorize failed: HTTP " + response.statusCode() + " → " + response.asString());
        }
    }

    /** Authenticate the account and return the raw account userToken (JWT string). */
    @Step("Fetch account userToken for {email}")
    public String getUserToken(String email, String password) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", email);
        body.put("password", password);

        Response response = postEncrypted(VERIFY_PATH, toJson(body));
        String userToken = response.jsonPath().getString("userToken");
        if (userToken == null || userToken.isBlank()) {
            throw new IllegalStateException(
                    "verify did not return a userToken: HTTP " + response.statusCode() + " → " + response.asString());
        }
        return userToken;
    }

    /** Approve the TV device code for the authenticated account. */
    @Step("Authorize device code {code}")
    public Response authorizeDeviceCode(String code, String userToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("userToken", userToken);
        return postEncrypted(AUTHORIZE_PATH, toJson(body));
    }

    private Response postEncrypted(String path, String plaintextJson) {
        URI url = URI.create(baseUrl + path);
        return given()
                .contentType("application/json")
                .accept("application/json")
                .header("accept-language", "en")
                .body(Map.of("data", encrypt(plaintextJson)))
                .post(url);
    }

    /** RSA-OAEP + A256GCM compact JWE, matching the frontend's encrypt(). */
    private String encrypt(String plaintextJson) {
        try {
            JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A256GCM)
                    .contentType("application/json")
                    .build();
            JWEObject jwe = new JWEObject(header, new Payload(plaintextJson));
            jwe.encrypt(new RSAEncrypter(parseRsaPublicKey(publicKeyPem)));
            return jwe.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    private RSAPublicKey parseRsaPublicKey(String pem) throws Exception {
        byte[] der = Base64.getDecoder().decode(pem.replaceAll("\\s", ""));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
