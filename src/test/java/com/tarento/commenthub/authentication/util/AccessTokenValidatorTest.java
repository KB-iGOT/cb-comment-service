package com.tarento.commenthub.authentication.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.authentication.model.KeyData;
import com.tarento.commenthub.constant.Constants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.Time;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    @Mock
    private KeyManager keyManager;

    @Mock
    private PublicKey mockPublicKey;

    @InjectMocks
    private AccessTokenValidator accessTokenValidator;

    @Spy
    private AccessTokenValidator spyAccessTokenValidator;

    private static final ObjectMapper mapper = new ObjectMapper();

    private String expiredToken;
    private String invalidSignatureToken;
    private String invalidIssuerToken;

    private String unauthorized = "Unauthorized";

    @BeforeEach
    void setUp() throws Exception {
        // Mock PropertiesCache.getInstance().getProperty(...) if needed
        // Generate tokens for different scenarios
        expiredToken = generateToken("expiredUserId", Time.currentTime() - 1000, "expectedIssuer");
        invalidSignatureToken = generateToken("invalidSignatureUserId", Time.currentTime() + 1000, "expectedIssuer");
        invalidIssuerToken = generateToken("invalidIssuerUserId", Time.currentTime() + 1000, "invalidIssuer");

    }

    @Test
    void testVerifyUserToken_ExpiredToken() {
        String userId = accessTokenValidator.verifyUserToken(expiredToken);
        assertEquals(unauthorized, userId);
    }

    @Test
    void testVerifyUserToken_InvalidSignature() {
        String userId = accessTokenValidator.verifyUserToken(invalidSignatureToken);
        assertEquals(unauthorized, userId);
    }

    @Test
    void testVerifyUserToken_InvalidIssuer() {
        String userId = accessTokenValidator.verifyUserToken(invalidIssuerToken);
        assertEquals(unauthorized, userId);
    }

    @Test
    void testFetchUserIdFromAccessToken_NullToken() {
        String userId = accessTokenValidator.fetchUserIdFromAccessToken(null);
        assertNull(userId);
    }

    // Helper method to generate tokens
    private String generateToken(String userId, int exp, String issuer) throws Exception {
        Map<String, Object> header = new HashMap<>();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", "testKeyId");

        Map<String, Object> body = new HashMap<>();
        body.put("sub", "user:" + userId);
        body.put("exp", exp);
        body.put("iss", issuer);

        String headerJson = mapper.writeValueAsString(header);
        String bodyJson = mapper.writeValueAsString(body);

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes());
        String encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyJson.getBytes());

        String unsignedToken = encodedHeader + "." + encodedBody;

        // For simplicity, we're not signing the token here
        String signature = "testSignature";

        return unsignedToken + "." + signature;
    }

    @Test
    void fetchUserIdFromAccessToken_validToken_returnsUserId(){
        String accessToken = "validToken";
        String expectedUserId = "user123";


        doReturn(expectedUserId).when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_unauthorizedToken_returnsNull(){
        String accessToken = "unauthorizedToken";

        doReturn("UNAUTHORIZED").when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertNull(actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_nullToken_returnsNull() {
        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(null);
        assertNull(actualUserId);
    }

    @Test
    void fetchUserIdFromAccessToken_exceptionThrown_returnsNull(){
        String accessToken = "token";

        doThrow(new RuntimeException("some error")).when(spyAccessTokenValidator).verifyUserToken(accessToken);

        String actualUserId = spyAccessTokenValidator.fetchUserIdFromAccessToken(accessToken);

        assertNull(actualUserId);
    }

    @Test
    void testVerifyUserToken_invalidSignature() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "http://localhost/realms/testRealm");
        payload.put(Constants.SUB, "user:invalid");
        payload.put("exp", Time.currentTime() + 1000);

        String token = mockToken(payload);
        when(keyManager.getPublicKey(anyString())).thenThrow(new RuntimeException("Invalid key"));

        String result = accessTokenValidator.verifyUserToken(token);
        assertEquals(Constants.UNAUTHORIZED_USER, result);
    }

    @Test
    void testVerifyUserToken_invalidTokenFormat() {
        String token = "invalid.token";
        String result = accessTokenValidator.verifyUserToken(token);
        assertEquals(Constants.UNAUTHORIZED_USER, result);
    }

    private String mockToken(Map<String, Object> payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String headerJson = mapper.writeValueAsString(Collections.singletonMap("kid", "key1"));
        String bodyJson = mapper.writeValueAsString(payload);

        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("fake-signature".getBytes(StandardCharsets.UTF_8));

        return String.join(".", header, body, signature);
    }

    @Test
    void testCheckIss_validIssuer() throws Exception {
        // Arrange
        Field realmUrlField = AccessTokenValidator.class.getDeclaredField("REALM_URL");
        realmUrlField.setAccessible(true);
        String validIssuer = (String) realmUrlField.get(null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", validIssuer);
        payload.put(Constants.SUB, "user:abc");
        payload.put("exp", Time.currentTime() + 5000);

        String token = mockToken(payload);

        // Mock KeyManager -> return a valid KeyData
        PublicKey mockPublicKey = mock(PublicKey.class);
        KeyData keyData = new KeyData("testKeyId", mockPublicKey);
        when(keyManager.getPublicKey(anyString())).thenReturn(keyData);

        // Also mock CryptoUtil.verifyRSASign to succeed
        try (MockedStatic<CryptoUtil> cryptoUtilMock = mockStatic(CryptoUtil.class)) {
            cryptoUtilMock.when(() ->
                    CryptoUtil.verifyRSASign(anyString(), any(), eq(mockPublicKey), eq(Constants.SHA_256_WITH_RSA))
            ).thenReturn(true);

            // Act
            String result = accessTokenValidator.verifyUserToken(token);

            // Assert
            assertEquals("abc", result);
        }
    }

    @Test
    void testDecodeFromBase64() throws Exception {
        Method method = AccessTokenValidator.class.getDeclaredMethod("decodeFromBase64", String.class);
        method.setAccessible(true);
        byte[] result = (byte[]) method.invoke(accessTokenValidator, "aGVsbG8="); // "hello"
        assertEquals("hello", new String(result));
    }

    @Test
    void testValidateToken_invalidJsonHeader() {
        // Header not JSON
        String badHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("not-json".getBytes());
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".getBytes());
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString("sig".getBytes());

        String token = badHeader + "." + body + "." + sig;
        String result = accessTokenValidator.verifyUserToken(token);
        assertEquals(Constants.UNAUTHORIZED_USER, result);
    }

    @Test
    void testValidateToken_signatureThrowsException() throws Exception {
        // Arrange a valid-looking token
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "http://validissuer");
        payload.put(Constants.SUB, "user:valid");
        payload.put("exp", Time.currentTime() + 9999);
        String token = mockToken(payload);

        // Force CryptoUtil to throw
        try (MockedStatic<CryptoUtil> cryptoMock = mockStatic(CryptoUtil.class)) {
            cryptoMock.when(() -> CryptoUtil.verifyRSASign(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("crypto error"));

            String result = accessTokenValidator.verifyUserToken(token);
            assertEquals(Constants.UNAUTHORIZED_USER, result);
        }
    }

}
