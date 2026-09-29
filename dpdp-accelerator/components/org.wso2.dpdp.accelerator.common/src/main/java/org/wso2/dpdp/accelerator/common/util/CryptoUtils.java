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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.crypto.api.CipherMetaDataHolder;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigParser;
import org.wso2.dpdp.accelerator.common.exception.DPDPSystemException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for keystore-backed reversible encryption and decryption of sensitive
 * values using WSO2 IS / Carbon {@link CryptoUtil}.
 *
 * <p>Whether encryption is enabled is controlled <strong>exclusively</strong> by
 * the DPDP accelerator configuration key
 * {@code EventNotifications.EncryptSharedSecret} (TOML:
 * {@code [dpdp_accelerator.event_notifications] encrypt_shared_secret = true}).
 * IS OAuth settings such as {@code oauth.hash_client_secret} or
 * {@code oauth.encrypt_client_secret} have <strong>no influence</strong> on
 * whether shared secrets are encrypted.
 */
public final class CryptoUtils {

    private static final Log LOG = LogFactory.getLog(CryptoUtils.class);

    private static final byte[] TEST_KEY_BYTES = "DPDP_TEST_SECRET_KEY_16_BYTES!".substring(0, 16)
            .getBytes(StandardCharsets.UTF_8);
    private static final String TEST_CIPHER_PREFIX = "dpdp_test_enc:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static CryptoUtil cryptoUtilInstance;
    private static Boolean encryptionEnabledOverride;
    private static boolean testModeEnabled = false;

    private CryptoUtils() {

    }

    // -----------------------------------------------------------------------
    // Test / override setters
    // -----------------------------------------------------------------------

    /**
     * Sets whether test-mode encryption/decryption is enabled.
     * When enabled, uses a local hardcoded key instead of Carbon CryptoUtil.
     * This must ONLY be used for unit and integration testing.
     *
     * @param testMode true to enable test mode, false otherwise
     */
    public static void setTestModeEnabled(boolean testMode) {

        testModeEnabled = testMode;
    }

    /**
     * Sets an explicit override for whether shared secret encryption is enabled,
     * primarily for unit and integration testing.
     *
     * @param encryptionEnabled the override value, or {@code null} to reset to configuration
     */
    public static void setEncryptionEnabled(Boolean encryptionEnabled) {

        encryptionEnabledOverride = encryptionEnabled;
    }

    /**
     * Sets a mock or custom {@link CryptoUtil} instance, primarily for unit
     * testing.
     *
     * @param cryptoUtil the CryptoUtil instance to use, or {@code null} to reset
     */
    public static void setCryptoUtil(CryptoUtil cryptoUtil) {

        cryptoUtilInstance = cryptoUtil;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when shared-secret encryption at rest is enabled.
     *
     * <p>The flag is read exclusively from the DPDP accelerator configuration key
     * {@code EventNotifications.EncryptSharedSecret}. IS OAuth settings have no
     * effect on this flag.
     *
     * <p>An {@link IllegalStateException} from the configuration parser (e.g. an
     * unrecognised value such as {@code "enabled"} instead of {@code "true"}) is
     * intentionally allowed to propagate so that a misconfigured value cannot
     * silently disable encryption. A missing key returns the safe default
     * ({@code false}) via {@code getValidatedBoolean}.
     *
     * @return true if encryption is enabled, false otherwise
     * @throws IllegalStateException if the configuration value is present but invalid
     */
    public static boolean isEncryptionEnabled() {

        if (encryptionEnabledOverride != null) {
            return encryptionEnabledOverride;
        }
        try {
            return DPDPConfigParser.getInstance().isEventNotificationEncryptSharedSecret();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.debug("Unable to read encryption configuration from parser; defaulting to disabled.", e);
            return false;
        }
    }

    /**
     * Encrypts the provided plaintext string using Carbon's keystore-backed
     * reversible encryption and returns the Base64-encoded ciphertext.
     *
     * <p>If encryption is not enabled the plaintext is returned unchanged.
     * {@code null} and empty inputs are returned unchanged unconditionally.
     *
     * @param plainText the plaintext to encrypt
     * @return Base64-encoded ciphertext, or the input if null or empty
     * @throws DPDPSystemException if encryption fails
     */
    public static String encrypt(String plainText) {

        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        if (!isEncryptionEnabled()) {
            return plainText;
        }

        if (cryptoUtilInstance != null || isCarbonCryptoAvailable()) {
            try {
                CryptoUtil cryptoUtil = cryptoUtilInstance != null
                        ? cryptoUtilInstance
                        : CryptoUtil.getDefaultCryptoUtil();
                if (cryptoUtil == null) {
                    throw new DPDPSystemException("CryptoUtil is not available or registered.");
                }
                return cryptoUtil.encryptAndBase64Encode(plainText.getBytes(StandardCharsets.UTF_8));
            } catch (CryptoException e) {
                LOG.error("Error occurred while encrypting sensitive value", e);
                throw new DPDPSystemException("Error occurred while encrypting sensitive value", e);
            }
        }

        if (testModeEnabled) {
            return encryptTest(plainText);
        }
        throw new DPDPSystemException("CryptoUtil is not available; cannot encrypt sensitive value.");
    }

    /**
     * Decrypts the provided Base64-encoded ciphertext using Carbon's
     * keystore-backed reversible encryption. If the stored value is plaintext,
     * it is returned as-is.
     *
     * @param cipherText the Base64-encoded ciphertext to decrypt, or plaintext
     * @return the decrypted plaintext string, or the input if null or empty
     * @throws DPDPSystemException if decryption of an encrypted value fails
     */
    public static String decrypt(String cipherText) {

        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }

        if (cipherText.startsWith(TEST_CIPHER_PREFIX)) {
            if (!testModeEnabled) {
                throw new DPDPSystemException(
                        "Test cipher prefix found but test mode is not enabled; refusing to decrypt.");
            }
            return decryptTest(cipherText);
        }

        if (!isEncryptedValue(cipherText)) {
            return cipherText;
        }

        if (cryptoUtilInstance != null || isCarbonCryptoAvailable()) {
            try {
                CryptoUtil cryptoUtil = cryptoUtilInstance != null
                        ? cryptoUtilInstance
                        : CryptoUtil.getDefaultCryptoUtil();
                if (cryptoUtil == null) {
                    throw new DPDPSystemException("CryptoUtil is not available or registered.");
                }
                byte[] decryptedBytes = cryptoUtil.base64DecodeAndDecrypt(cipherText);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (CryptoException | IllegalArgumentException e) {
                LOG.error("Error occurred while decrypting sensitive value", e);
                throw new DPDPSystemException("Error occurred while decrypting sensitive value", e);
            }
        }

        LOG.error("CryptoService is not available and ciphertext does not match test cipher prefix.");
        throw new DPDPSystemException("CryptoService is not registered and ciphertext cannot be decrypted.");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static boolean isCarbonCryptoAvailable() {

        return System.getProperty("carbon.home") != null;
    }

    private static boolean isEncryptedValue(String value) {

        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith(TEST_CIPHER_PREFIX)) {
            return true;
        }
        if (!value.startsWith("eyJ")) {
            return false;
        }
        return isCarbonEncryptedEnvelope(value);
    }

    private static boolean isCarbonEncryptedEnvelope(String value) {

        // A JWT token or dot-separated compact token is plaintext, not a Carbon ciphertext envelope.
        if (value.indexOf('.') > 0) {
            return false;
        }
        try {
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException e) {
                return false;
            }
            String json = new String(decoded, StandardCharsets.UTF_8).trim();
            if (!json.startsWith("{") || !json.endsWith("}")) {
                return false;
            }
            // A JWT header or token contains "alg":; Carbon ciphertext envelopes never contain "alg":.
            if (json.contains("\"alg\"")) {
                return false;
            }
            CryptoUtil cryptoUtil = cryptoUtilInstance != null
                    ? cryptoUtilInstance
                    : (isCarbonCryptoAvailable() ? CryptoUtil.getDefaultCryptoUtil() : null);
            if (cryptoUtil != null) {
                try {
                    CipherMetaDataHolder holder = cryptoUtil.cipherTextToCipherMetaDataHolder(decoded);
                    if (holder != null && holder.getCipherText() != null && holder.getTransformation() != null) {
                        return true;
                    }
                } catch (Exception e) {
                    LOG.debug("Unable to deserialize cipher metadata holder from candidate envelope.", e);
                }
            }
            boolean hasCipher = json.contains("\"c\"") || json.contains("\"cipher\"")
                    || json.contains("\"cipherText\"");
            boolean hasTransformation = json.contains("\"t\"") || json.contains("\"transformation\"");
            return hasCipher && (hasTransformation || json.contains("\"cipher\""));
        } catch (Exception e) {
            LOG.debug("Value starting with eyJ is not a valid Carbon encrypted envelope; treating as plaintext.", e);
            return false;
        }
    }

    private static String encryptTest(String plainText) {

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TEST_KEY_BYTES, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return TEST_CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new DPDPSystemException("Error encrypting value in test mode", e);
        }
    }

    private static String decryptTest(String cipherText) {

        try {
            String encoded = cipherText.substring(TEST_CIPHER_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] cipherBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(TEST_KEY_BYTES, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new DPDPSystemException("Error decrypting value in test mode", e);
        }
    }
}
