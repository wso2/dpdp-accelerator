/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.common.util;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.dpdp.accelerator.common.exception.DPDPSystemException;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.expectThrows;

public class CryptoUtilsTest {

    private static final String SAMPLE_CARBON_ENVELOPE = Base64.getEncoder().encodeToString(
            "{\"c\":\"sample-cipher-text\",\"t\":\"AES/GCM/NoPadding\"}".getBytes(StandardCharsets.UTF_8));

    private CryptoUtil cryptoUtil;

    @BeforeMethod
    public void setUp() {

        cryptoUtil = mock(CryptoUtil.class);
        CryptoUtils.setCryptoUtil(cryptoUtil);
        CryptoUtils.setEncryptionEnabled(true);
    }

    @AfterMethod
    public void tearDown() {

        CryptoUtils.setCryptoUtil(null);
        CryptoUtils.setEncryptionEnabled(null);
        CryptoUtils.setTestModeEnabled(false);
    }

    @Test
    public void testEncryptAndDecryptSuccessWithCryptoUtilMock() throws Exception {

        String plainText = "my-secret-key-123";
        String cipherText = "eyJjaXBoZXIiOiJteS1zZWNyZXQta2V5LTEyMyJ9";

        when(cryptoUtil.encryptAndBase64Encode(eq(plainText.getBytes(StandardCharsets.UTF_8))))
                .thenReturn(cipherText);
        when(cryptoUtil.base64DecodeAndDecrypt(eq(cipherText)))
                .thenReturn(plainText.getBytes(StandardCharsets.UTF_8));

        String encrypted = CryptoUtils.encrypt(plainText);
        assertEquals(encrypted, cipherText);

        String decrypted = CryptoUtils.decrypt(cipherText);
        assertEquals(decrypted, plainText);
    }

    @Test
    public void testEncryptAndDecryptSuccessInTestMode() {

        CryptoUtils.setCryptoUtil(null);
        CryptoUtils.setTestModeEnabled(true);

        String plainText = "super-secret-webhook-key";
        String encrypted = CryptoUtils.encrypt(plainText);

        assertNotNull(encrypted);
        assertNotEquals(encrypted, plainText);

        String decrypted = CryptoUtils.decrypt(encrypted);
        assertEquals(decrypted, plainText);
    }

    @Test
    public void testDecryptCorruptedCiphertextInTestModeThrows() {

        CryptoUtils.setCryptoUtil(null);
        CryptoUtils.setTestModeEnabled(true);

        expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.decrypt("dpdp_test_enc:invalid_corrupt_data"));
    }

    @Test
    public void testEncryptNullOrEmptyReturnsAsIs() {

        assertNull(CryptoUtils.encrypt(null));
        assertEquals(CryptoUtils.encrypt(""), "");
    }

    @Test
    public void testDecryptNullOrEmptyReturnsAsIs() {

        assertNull(CryptoUtils.decrypt(null));
        assertEquals(CryptoUtils.decrypt(""), "");
    }

    @Test
    public void testEncryptCryptoExceptionThrowsDPDPSystemException() throws Exception {

        when(cryptoUtil.encryptAndBase64Encode(any()))
                .thenThrow(new CryptoException("Encryption failed"));

        DPDPSystemException exception = expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.encrypt("sample-secret"));
        assertEquals(exception.getMessage(), "Error occurred while encrypting sensitive value");
    }

    @Test
    public void testDecryptCryptoExceptionThrowsDPDPSystemException() throws Exception {

        String cipherText = SAMPLE_CARBON_ENVELOPE;
        when(cryptoUtil.base64DecodeAndDecrypt(eq(cipherText)))
                .thenThrow(new CryptoException("Decryption failed"));

        DPDPSystemException exception = expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.decrypt(cipherText));
        assertEquals(exception.getMessage(), "Error occurred while decrypting sensitive value");
    }

    @Test
    public void testDecryptIllegalArgumentExceptionThrowsDPDPSystemException() throws Exception {

        String cipherText = SAMPLE_CARBON_ENVELOPE;
        when(cryptoUtil.base64DecodeAndDecrypt(eq(cipherText)))
                .thenThrow(new IllegalArgumentException("Illegal base64 character"));

        DPDPSystemException exception = expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.decrypt(cipherText));
        assertEquals(exception.getMessage(), "Error occurred while decrypting sensitive value");
    }

    @Test
    public void testDecryptNonTestCipherWithoutCryptoServiceThrowsDPDPSystemException() {

        CryptoUtils.setCryptoUtil(null);
        expectThrows(DPDPSystemException.class, () -> CryptoUtils.decrypt(SAMPLE_CARBON_ENVELOPE));
    }

    @Test
    public void testDecryptPreservesPlaintextJwtStartingWithEyJ() {

        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertEquals(CryptoUtils.decrypt(jwt), jwt);
    }

    @Test
    public void testDecryptPreservesPlaintextJwtHeaderOnly() {

        String jwtHeader = Base64.getEncoder().encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(CryptoUtils.decrypt(jwtHeader), jwtHeader);
    }

    @Test
    public void testDecryptPreservesNonBase64StartingWithEyJ() {

        String nonBase64 = "eyJ!!not-valid-base64!!";
        assertEquals(CryptoUtils.decrypt(nonBase64), nonBase64);
    }

    @Test
    public void testDecryptPreservesArbitraryJsonBase64StartingWithEyJ() {

        String arbitraryJson = Base64.getEncoder().encodeToString(
                "{\"username\":\"admin\",\"role\":\"subscriber\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals(CryptoUtils.decrypt(arbitraryJson), arbitraryJson);
    }

    @Test
    public void testEncryptWhenEncryptionDisabledReturnsPlainText() {

        CryptoUtils.setEncryptionEnabled(false);
        String plainText = "my-plain-secret";
        assertEquals(CryptoUtils.encrypt(plainText), plainText);
    }

    @Test
    public void testDecryptPlainTextReturnsPlainText() {

        String plainText = "my-plain-secret";
        assertEquals(CryptoUtils.decrypt(plainText), plainText);
    }

    @Test
    public void testDecryptEncryptedValueWhenEncryptionDisabledStillDecrypts() {

        CryptoUtils.setCryptoUtil(null);
        CryptoUtils.setTestModeEnabled(true);
        CryptoUtils.setEncryptionEnabled(true);
        String plainText = "test-webhook-secret";
        String encrypted = CryptoUtils.encrypt(plainText);

        CryptoUtils.setEncryptionEnabled(false);
        String decrypted = CryptoUtils.decrypt(encrypted);
        assertEquals(decrypted, plainText);
    }

    @Test
    public void testEncryptThrowsWhenCarbonUnavailableAndNotInTestMode() {

        CryptoUtils.setCryptoUtil(null);
        CryptoUtils.setTestModeEnabled(false);
        CryptoUtils.setEncryptionEnabled(true);

        DPDPSystemException exception = expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.encrypt("sample-secret"));
        assertEquals(exception.getMessage(), "CryptoUtil is not available; cannot encrypt sensitive value.");
    }

    @Test
    public void testDecryptThrowsForTestPrefixOutsideTestMode() {

        CryptoUtils.setTestModeEnabled(false);
        DPDPSystemException exception = expectThrows(DPDPSystemException.class,
                () -> CryptoUtils.decrypt("dpdp_test_enc:c29tZS1jaXBoZXI="));
        assertEquals(exception.getMessage(),
                "Test cipher prefix found but test mode is not enabled; refusing to decrypt.");
    }

    /**
     * Verifies that an invalid {@code EncryptSharedSecret} configuration value
     * (e.g. a typo such as {@code "enabled"}) propagates as an
     * {@link IllegalStateException} rather than being swallowed and silently
     * disabling encryption (fail-closed security requirement).
     */
    @Test
    public void testIsEncryptionEnabledPropagatesInvalidConfigValue() throws Exception {

        // Remove the override so isEncryptionEnabled() actually reads the parser.
        CryptoUtils.setEncryptionEnabled(null);

        Field parserField = org.wso2.dpdp.accelerator.common.config.DPDPConfigParser.class
                .getDeclaredField("parser");
        parserField.setAccessible(true);
        org.wso2.dpdp.accelerator.common.config.DPDPConfigParser parserInstance =
                (org.wso2.dpdp.accelerator.common.config.DPDPConfigParser) parserField.get(null);

        if (parserInstance == null) {
            // Parser singleton not yet initialised in this test JVM context — skip via early return.
            return;
        }

        Field configField = org.wso2.dpdp.accelerator.common.config.DPDPConfigParser.class
                .getDeclaredField("configuration");
        configField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> configuration =
                (java.util.Map<String, Object>) configField.get(parserInstance);

        Object originalValue = configuration.get(
                org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants
                        .EVENT_NOTIFICATIONS_ENCRYPT_SHARED_SECRET);
        try {
            configuration.put(
                    org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants
                            .EVENT_NOTIFICATIONS_ENCRYPT_SHARED_SECRET,
                    "enabled");           // invalid — not "true" or "false"

            // Must throw; must NOT silently return false.
            expectThrows(IllegalStateException.class, CryptoUtils::isEncryptionEnabled);
        } finally {
            // Restore the original state regardless of test outcome.
            if (originalValue == null) {
                configuration.remove(
                        org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants
                                .EVENT_NOTIFICATIONS_ENCRYPT_SHARED_SECRET);
            } else {
                configuration.put(
                        org.wso2.dpdp.accelerator.common.constant.DPDPCommonConstants
                                .EVENT_NOTIFICATIONS_ENCRYPT_SHARED_SECRET,
                        originalValue);
            }
            CryptoUtils.setEncryptionEnabled(true);   // restore the override used by other tests
        }
    }
}
