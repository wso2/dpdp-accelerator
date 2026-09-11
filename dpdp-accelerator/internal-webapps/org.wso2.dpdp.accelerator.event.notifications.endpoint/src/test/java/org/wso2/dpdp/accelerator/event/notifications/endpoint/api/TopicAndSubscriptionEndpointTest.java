package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.SubscriptionHandler;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.TopicHandler;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionEventHistoryDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TopicAndSubscriptionEndpointTest {

    @Mock private TopicHandler topicHandler;
    @Mock private SubscriptionHandler subscriptionHandler;
    private TopicEndpoint topicEndpoint;
    private SubscriptionEndpoint subscriptionEndpoint;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        topicEndpoint = new TopicEndpoint(topicHandler, () -> "org-1");
        subscriptionEndpoint = new SubscriptionEndpoint(subscriptionHandler, () -> "org-1");
    }

    @Test
    public void topicOperationsForwardTenantAndReturnExpectedStatuses() {
        TopicDTO topic = new TopicDTO();
        PaginatedResult<TopicDTO> page = new PaginatedResult<>(Collections.singletonList(topic), 1);
        when(topicHandler.createTopic(eq("org-1"), any())).thenReturn(topic);
        when(topicHandler.listTopics("org-1", "active", "search", 20, 0, "name")).thenReturn(page);
        when(topicHandler.deleteTopic("org-1", "topic-1")).thenReturn(topic);

        assertEquals(topicEndpoint.createTopic(topic).getStatus(), 201);
        assertEquals(topicEndpoint.listTopics("active", "search", 20, 0, "name").getEntity(), page);
        assertEquals(topicEndpoint.deleteTopic("topic-1").getStatus(), 200);
        verify(topicHandler).deleteTopic("org-1", "topic-1");
    }

    @Test
    public void subscriptionOperationsForwardTenantAndReturnOk() {
        SubscriptionDTO subscription = new SubscriptionDTO();
        PaginatedResult<SubscriptionDTO> page = new PaginatedResult<>(Collections.singletonList(subscription), 1);
        SubscriptionEventHistoryDTO history = new SubscriptionEventHistoryDTO();
        when(subscriptionHandler.createSubscription(eq("org-1"), any())).thenReturn(subscription);
        when(subscriptionHandler.listSubscriptions("org-1", "active", "marketing", "search", 20, 0, "createdAt"))
                .thenReturn(page);
        when(subscriptionHandler.getSubscription("org-1", "sub-1")).thenReturn(subscription);
        when(subscriptionHandler.deleteSubscription("org-1", "sub-1")).thenReturn(subscription);
        when(subscriptionHandler.retryVerification("org-1", "sub-1")).thenReturn(subscription);
        when(subscriptionHandler.getSubscriptionEventHistory("org-1", "sub-1", "delivery-1")).thenReturn(history);
        when(subscriptionHandler.retryDelivery("org-1", "sub-1", "delivery-1")).thenReturn(history);

        assertEquals(subscriptionEndpoint.createSubscription(subscription).getStatus(), 201);
        assertEquals(subscriptionEndpoint.listSubscriptions("active", "marketing", "search", 20, 0, "createdAt").getEntity(), page);
        assertEquals(subscriptionEndpoint.getSubscription("sub-1").getEntity(), subscription);
        assertEquals(subscriptionEndpoint.deleteSubscription("sub-1").getEntity(), subscription);
        assertEquals(subscriptionEndpoint.retryVerification("sub-1").getEntity(), subscription);
        assertEquals(subscriptionEndpoint.getSubscriptionEventHistory("sub-1", "delivery-1").getEntity(), history);
        assertEquals(subscriptionEndpoint.retryDelivery("sub-1", "delivery-1").getStatus(), 202);
    }
}
