package com.openfgc.nomineeservice.security;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Trusts any TLS certificate - dev-only, for talking to a local IS instance
 * using its default self-signed certificate. Enabled via
 * identityserver.tls-skip-verify.
 */
class InsecureClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    private static final TrustManager[] TRUST_ALL = {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            },
    };

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (connection instanceof HttpsURLConnection https) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, TRUST_ALL, new SecureRandom());
                https.setSSLSocketFactory(sslContext.getSocketFactory());
                https.setHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                throw new IOException("failed to configure insecure TLS", e);
            }
        }
        super.prepareConnection(connection, httpMethod);
    }
}
