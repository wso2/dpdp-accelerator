/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.service.dispatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.event.notifications.common.util.HmacSigner;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDelivery;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAudit;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link WebhookDeliveryTask}. The {@link HttpClient} is mocked; everything
 * else (delivery row, audit row, retry state, request envelope, signature) is verified
 * through Mockito assertions and Jackson tree reads.
 */
public class WebhookDeliveryTaskTest {

    private static final String DELIVERY_ID = "deliv-1";
    private static final String SUBSCRIPTION_ID = "sub-1";
    private static final String EVENT_ID = "event-1";
    private static final String ORG_ID = "org-1";
    private static final String GROUP_ID = "group-1";
    private static final String CALLBACK_URL = "https://callback.example.com/hook";
    private static final String SHARED_SECRET = "secret";
    private static final String TOPIC_NAME = "accounts";

    @Mock
    private DeliveryDAO deliveryDAO;

    @Mock
    private HttpClient httpClient;

    @Mock
    private DPDPConfigurationService configurationService;

    @Mock
    private EventPayloadSigner payloadSigner;

    @Mock
    private java.sql.Connection connection;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        javax.sql.DataSource dataSource = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        setStaticInstance(null);
        setStaticDataSource(dataSource);
        when(configurationService.getEventNotificationMaxRetries()).thenReturn(5);
        when(configurationService.getEventNotificationBaseBackoffSeconds()).thenReturn(5L);
    }

    @org.testng.annotations.AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    private static void setStaticDataSource(javax.sql.DataSource dataSource) throws Exception {
        java.lang.reflect.Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager instance) throws Exception {
        java.lang.reflect.Field field = org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }

    private WebhookDelivery delivery(int attemptCount) {
        return new WebhookDelivery(
                DELIVERY_ID,
                SUBSCRIPTION_ID,
                EVENT_ID,
                "pending",
                attemptCount,
                null,
                new Timestamp(System.currentTimeMillis() - 1000L),
                new Timestamp(System.currentTimeMillis() - 1000L),
                null);
    }

    private WebhookDeliveryTask task(WebhookDelivery delivery) {
        return task(delivery, "{\"hello\":\"world\"}");
    }

    private WebhookDeliveryTask task(WebhookDelivery delivery, String payload) {
        return task(delivery, payload, SHARED_SECRET);
    }

    private WebhookDeliveryTask task(WebhookDelivery delivery, String payload, String sharedSecret) {
        return new WebhookDeliveryTask(
                delivery,
                ORG_ID,
                GROUP_ID,
                payload,
                CALLBACK_URL,
                sharedSecret,
                TOPIC_NAME,
                deliveryDAO,
                httpClient,
                configurationService,
                payloadSigner);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int status) {
        HttpResponse<String> response = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("ignored");
        return response;
    }

    // doReturn bypasses Mockito's generic type-checking on HttpResponse<String>.
    private void stubHttpResponse(int status) throws Exception {
        org.mockito.Mockito.doReturn(mockResponse(status))
                .when(httpClient)
                .send(any(HttpRequest.class), any());
    }

    private void stubHttpException(IOException ex) throws Exception {
        org.mockito.Mockito.doThrow(ex)
                .when(httpClient)
                .send(any(HttpRequest.class), any());
    }

    /**
     * Captures the actual HttpRequest that the task sent, so envelope tests can assert on
     * the body, the signature header, and the routing headers without re-implementing the
     * dispatch logic.
     */
    private HttpRequest captureRequest() throws Exception {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        return requestCaptor.getValue();
    }

    private String bodyOf(HttpRequest request) throws Exception {
        // HttpRequest.BodyPublisher doesn't expose its bytes; subscribe a tiny in-memory
        // collector to it so tests can read what the task actually sent. We only use this
        // with BodyPublishers.ofString, which is synchronous under request(Long.MAX_VALUE).
        return request.bodyPublisher().map(pub -> {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> sink =
                    new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                            s.request(Long.MAX_VALUE);
                        }
                        @Override public void onNext(java.nio.ByteBuffer item) {
                            byte[] buf = new byte[item.remaining()];
                            item.get(buf);
                            out.write(buf, 0, buf.length);
                        }
                        @Override public void onError(Throwable t) { }
                        @Override public void onComplete() { }
                    };
            pub.subscribe(sink);
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }).orElseThrow(() -> new AssertionError("expected request to carry a body"));
    }

    @Test
    public void testSuccessMarksDeliveredAndWritesAudit() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).recordSuccessfulAttempt(any(java.sql.Connection.class), auditCaptor.capture(), updatedCaptor.capture());

        WebhookDelivery updated = updatedCaptor.getValue();
        assertEquals(updated.getStatus(), "delivered");
        assertEquals(updated.getAttemptCount(), 1);
        assertNotNull(updated.getDeliveredAt());
        assertNull(updated.getNextRetryAt());

        WebhookDeliveryAudit audit = auditCaptor.getValue();
        assertEquals(audit.getResponseCode(), "200");
        assertEquals(audit.getDeliveryId(), DELIVERY_ID);

        // On success we never release.
        verify(deliveryDAO, never()).recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    public void testRetryableFailureReleasesWithExponentialBackoff() throws Exception {
        // attempt 0 → fails → newAttempt = 1, which is below maxRetries (default 5).
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(500);
        when(deliveryDAO.recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> attemptCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Timestamp> nextCaptor = ArgumentCaptor.forClass(Timestamp.class);

        verify(deliveryDAO).recordRetryableFailure(any(java.sql.Connection.class), auditCaptor.capture(), idCaptor.capture(), attemptCaptor.capture(), nextCaptor.capture());
        assertEquals(auditCaptor.getValue().getResponseCode(), "500");
        assertEquals(idCaptor.getValue(), DELIVERY_ID);
        assertEquals(attemptCaptor.getValue().intValue(), 1);

        // 5s * 3^(1-1) = 5s after now; the next retry should be ~5_000ms in the future.
        long delayMs = nextCaptor.getValue().getTime() - System.currentTimeMillis();
        assertTrue(delayMs >= 4_000L && delayMs <= 6_500L,
                "expected ~5s backoff, got " + delayMs + "ms");

        // We don't mark as delivered or failed on a retryable failure.
        verify(deliveryDAO, never()).recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any());
        verify(deliveryDAO, never()).recordPermanentFailure(any(java.sql.Connection.class), any(), any());
    }

    @Test
    public void testExceptionIsTreatedAsRetryableFailure() throws Exception {
        WebhookDelivery delivery = delivery(2);
        stubHttpException(new IOException("boom"));
        when(deliveryDAO.recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO).recordRetryableFailure(any(java.sql.Connection.class), auditCaptor.capture(), eq(DELIVERY_ID), org.mockito.ArgumentMatchers.eq(3), any());
        assertEquals(auditCaptor.getValue().getResponseCode(), "EXCEPTION");
    }

    @Test
    public void testFifthFailedAttemptSchedulesFifthAndFinalRetry() throws Exception {
        // attempt 4 → fails → newAttempt = 5, which equals maxRetries (default) → retry once more.
        WebhookDelivery delivery = delivery(4);
        stubHttpResponse(500);
        when(deliveryDAO.recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(true);

        task(delivery).run();

        verify(deliveryDAO).recordRetryableFailure(any(java.sql.Connection.class), any(), eq(DELIVERY_ID),
                org.mockito.ArgumentMatchers.eq(5), any());
        verify(deliveryDAO, never()).recordPermanentFailure(any(java.sql.Connection.class), any(), any());
    }

    @Test
    public void testSixthFailedAttemptMarksTerminalFailed() throws Exception {
        // attempt 5 → fails → newAttempt = 6, exceeding five retries → terminal failure.
        WebhookDelivery delivery = delivery(5);
        stubHttpResponse(500);
        when(deliveryDAO.recordPermanentFailure(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).recordPermanentFailure(any(java.sql.Connection.class), auditCaptor.capture(), updatedCaptor.capture());

        assertEquals(updatedCaptor.getValue().getStatus(), "failed");
        assertEquals(updatedCaptor.getValue().getAttemptCount(), 6);
        assertNull(updatedCaptor.getValue().getNextRetryAt());
        assertEquals(auditCaptor.getValue().getResponseCode(), "500");

        // On terminal failure we never release for a retry.
        verify(deliveryDAO, never()).recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    public void testManualRetrySuccessPreservesAttemptHistory() throws Exception {
        WebhookDelivery delivery = delivery(6);
        delivery.setManualRetryUsed(true);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).recordSuccessfulAttempt(any(java.sql.Connection.class), any(), updatedCaptor.capture());
        assertEquals(updatedCaptor.getValue().getStatus(), "delivered");
        assertEquals(updatedCaptor.getValue().getAttemptCount(), 7);
        assertNotNull(updatedCaptor.getValue().getDeliveredAt());
        verify(deliveryDAO, never()).recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    public void testManualRetryFailureRemainsTerminal() throws Exception {
        WebhookDelivery delivery = delivery(6);
        delivery.setManualRetryUsed(true);
        stubHttpResponse(500);
        when(deliveryDAO.recordPermanentFailure(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        ArgumentCaptor<WebhookDelivery> updatedCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO).recordPermanentFailure(any(java.sql.Connection.class), any(), updatedCaptor.capture());
        assertEquals(updatedCaptor.getValue().getStatus(), "failed");
        assertEquals(updatedCaptor.getValue().getAttemptCount(), 7);
        assertNull(updatedCaptor.getValue().getNextRetryAt());
        verify(deliveryDAO, never()).recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    public void testAuditIdIsUniquePerAttempt() throws Exception {
        // Two failed attempts of two different deliveries should produce two distinct audit IDs.
        WebhookDelivery d1 = delivery(0);
        WebhookDelivery d2 = delivery(0);
        d2.setDeliveryId("deliv-2");

        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(d1).run();
        task(d2).run();

        ArgumentCaptor<WebhookDeliveryAudit> captor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        ArgumentCaptor<WebhookDelivery> devCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryDAO, times(2)).recordSuccessfulAttempt(any(java.sql.Connection.class), captor.capture(), devCaptor.capture());
        java.util.List<WebhookDeliveryAudit> rows = captor.getAllValues();
        assertEquals(rows.size(), 2);
        // Different UUIDs for each.
        assertNotEquals(rows.get(0).getAuditId(), rows.get(1).getAuditId(),
                "audit IDs must be distinct per attempt");
        UUID.fromString(rows.get(0).getAuditId());
        UUID.fromString(rows.get(1).getAuditId());
    }

    // -------- Envelope & signature tests --------

    @Test
    public void testRequestBodyIsEnvelopeNotRawPayload() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery, "{\"hello\":\"world\"}").run();

        HttpRequest request = captureRequest();
        String body = bodyOf(request);

        // Body must be parseable JSON, distinct from the raw payload string.
        JsonNode envelope = new ObjectMapper().readTree(body);
        assertTrue(envelope.has("eventPayload"), "envelope should carry an eventPayload field");
        assertEquals(envelope.get("eventPayload").get("hello").asText(), "world");
    }

    @Test
    public void testEnvelopeCarriesAllRoutingFields() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery, "{\"k\":\"v\"}").run();

        JsonNode envelope = new ObjectMapper().readTree(bodyOf(captureRequest()));
        assertEquals(envelope.get("deliveryId").asText(), DELIVERY_ID);
        assertEquals(envelope.get("eventId").asText(), EVENT_ID);
        assertEquals(envelope.get("subscriptionId").asText(), SUBSCRIPTION_ID);
        assertEquals(envelope.get("orgId").asText(), ORG_ID);
        assertNull(envelope.get("topicId"));
        assertEquals(envelope.get("topic").asText(), TOPIC_NAME);
        assertNull(envelope.get("topicName"));
    }

    @Test
    public void testEventSignatureIsHmacOfEnvelope() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery, "{\"hello\":\"world\"}").run();

        HttpRequest request = captureRequest();
        String body = bodyOf(request);

        // The header must be present and prefixed with the algorithm.
        String signatureHeader = request.headers().firstValue("event-signature").orElse(null);
        assertNotNull(signatureHeader, "Event-Signature header should be set");
        assertTrue(signatureHeader.startsWith("sha256="),
                "Event-Signature should be prefixed with 'sha256=', got: " + signatureHeader);

        // The signature is over the full envelope body — not the raw payload. Recomputing
        // HMAC over the envelope must match what's in the header.
        String expected = HmacSigner.sign(SHARED_SECRET, body);
        assertEquals(signatureHeader, "sha256=" + expected,
                "signature must be HMAC over the envelope body");
    }

    @Test
    public void testCertificateSignedPayloadEmbedsCompleteEventWithoutUnsignedCopy() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);
        when(configurationService.isEventNotificationPayloadSigningEnabled()).thenReturn(true);
        when(configurationService.getEventNotificationPayloadSigningAudience())
                .thenReturn("dpdp-event-notifications");
        when(payloadSigner.sign(any(EventPayloadSigningContext.class))).thenReturn("header.claims.signature");

        task(delivery, "{\"hello\":\"world\"}").run();

        HttpRequest request = captureRequest();
        JsonNode body = new ObjectMapper().readTree(bodyOf(request));
        assertEquals(body.size(), 1);
        assertEquals(body.get("signedPayload").asText(), "header.claims.signature");
        assertNull(body.get("payload"), "the event must not be duplicated outside the JWS");
        assertNull(body.get("payloadHash"), "the hash is an integrity claim inside the JWS");

        ArgumentCaptor<EventPayloadSigningContext> contextCaptor =
                ArgumentCaptor.forClass(EventPayloadSigningContext.class);
        verify(payloadSigner).sign(contextCaptor.capture());
        EventPayloadSigningContext context = contextCaptor.getValue();
        assertEquals(context.getTenantDomain(), ORG_ID);
        assertEquals(context.getSubject(), GROUP_ID);
        assertEquals(context.getAudience(), "dpdp-event-notifications");
        assertEquals(context.getDeliveryId(), DELIVERY_ID);
        assertEquals(context.getEventId(), EVENT_ID);
        assertEquals(context.getPayload().get("deliveryId").asText(), DELIVERY_ID);
        assertEquals(context.getPayload().get("subscriptionId").asText(), SUBSCRIPTION_ID);
        assertEquals(context.getPayload().get("orgId").asText(), ORG_ID);
        assertEquals(context.getPayload().get("groupId").asText(), GROUP_ID);
        assertEquals(context.getPayload().get("topic").asText(), TOPIC_NAME);
        assertEquals(context.getPayload().get("eventPayload").get("hello").asText(), "world");
        assertEquals(context.getPayloadHash(), HmacSigner.sign(SHARED_SECRET,
                new ObjectMapper().writeValueAsString(context.getPayload())));

        String expectedBodySignature = "sha256=" + HmacSigner.sign(SHARED_SECRET, bodyOf(request));
        assertEquals(request.headers().firstValue("Event-Signature").orElse(null), expectedBodySignature);
    }

    @Test
    public void testPayloadSigningFailurePreventsHttpRequestAndSchedulesRetry() throws Exception {
        WebhookDelivery delivery = delivery(0);
        when(configurationService.isEventNotificationPayloadSigningEnabled()).thenReturn(true);
        when(configurationService.getEventNotificationPayloadSigningAudience())
                .thenReturn("dpdp-event-notifications");
        when(payloadSigner.sign(any(EventPayloadSigningContext.class)))
                .thenThrow(new IllegalStateException("issuer unavailable"));
        when(deliveryDAO.recordRetryableFailure(any(java.sql.Connection.class), any(), anyString(), anyInt(), any())).thenReturn(true);

        task(delivery, "{\"hello\":\"world\"}").run();

        verify(httpClient, never()).send(any(), any());
        verify(deliveryDAO).recordRetryableFailure(any(java.sql.Connection.class), any(), eq(DELIVERY_ID), eq(1), any());
    }

    @Test
    public void testSignatureDiffersWhenRawPayloadWouldHaveBeenSigned() throws Exception {
        // Regression guard: the signature must NOT be computed over the raw payload. If a
        // future refactor accidentally signs the payload before wrapping, the digest will
        // match HMAC(payload) and this test will fail.
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        String rawPayload = "{\"hello\":\"world\"}";
        task(delivery, rawPayload).run();

        HttpRequest request = captureRequest();
        String body = bodyOf(request);
        String signatureHeader = request.headers().firstValue("event-signature").orElseThrow();

        String signedOverRawPayload = "sha256=" + HmacSigner.sign(SHARED_SECRET, rawPayload);
        assertNotEquals(signatureHeader, signedOverRawPayload,
                "signature must be over the envelope, not the raw payload");
    }

    @Test
    public void testDeliveryIdHeaderIsSetForDedupe() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        String deliveryIdHeader = captureRequest().headers().firstValue("Delivery-Id").orElse(null);
        assertEquals(deliveryIdHeader, DELIVERY_ID,
                "Delivery-Id header lets receivers dedupe without recomputing the HMAC");
    }

    @Test
    public void testContentTypeIsJson() throws Exception {
        WebhookDelivery delivery = delivery(0);
        stubHttpResponse(200);
        when(deliveryDAO.recordSuccessfulAttempt(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery).run();

        String contentType = captureRequest().headers().firstValue("Content-Type").orElseThrow();
        assertEquals(contentType, "application/json");
    }

    @Test
    public void testUnparseablePayloadIsMarkedAsPermanentFailure() throws Exception {
        WebhookDelivery delivery = delivery(0);
        when(deliveryDAO.recordPermanentFailure(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery, "not-json-at-all").run();

        verify(deliveryDAO).recordPermanentFailure(any(java.sql.Connection.class), any(), any());
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    public void testNullPayloadIsMarkedAsPermanentFailure() throws Exception {
        WebhookDelivery delivery = delivery(0);
        when(deliveryDAO.recordPermanentFailure(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery, null).run();

        verify(deliveryDAO).recordPermanentFailure(any(java.sql.Connection.class), any(), any());
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    public void testMissingSharedSecretFailsPermanentlyWithoutSendingHttpRequest() throws Exception {
        when(deliveryDAO.recordPermanentFailure(any(java.sql.Connection.class), any(), any())).thenReturn(true);

        task(delivery(0), "{\"hello\":\"world\"}", " ").run();

        ArgumentCaptor<WebhookDeliveryAudit> auditCaptor = ArgumentCaptor.forClass(WebhookDeliveryAudit.class);
        verify(deliveryDAO).recordPermanentFailure(any(java.sql.Connection.class), auditCaptor.capture(), any());
        assertEquals(auditCaptor.getValue().getResponseCode(), "MISSING_SECRET");
        verify(httpClient, never()).send(any(), any());
    }

    // Quick helper; keeps the test file readable.
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
