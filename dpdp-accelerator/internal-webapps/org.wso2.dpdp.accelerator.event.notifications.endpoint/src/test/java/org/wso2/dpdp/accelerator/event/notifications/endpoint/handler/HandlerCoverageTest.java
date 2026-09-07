package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.mockito.Mockito;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.EventPublishService;
import org.wso2.dpdp.accelerator.event.notifications.service.SubscriptionService;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.EventCreateDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.SubscriptionDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;

import java.util.Collections;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** Covers endpoint-handler forwarding, including nullable pagination translation. */
public class HandlerCoverageTest {

    @Test
    public void eventHandlerForwardsEveryOperation() {
        EventPublishService service = Mockito.mock(EventPublishService.class);
        EventHandler handler = new EventHandler(service);
        handler.publishEvent("org", "group", new EventCreateDTO("topic", Collections.singletonList("p"),
                new HashMap<>()));
        handler.publishEvent("org", "group", null);
        handler.searchEvents("org", "search", null, -1);
        handler.searchEvents("org", "topic", "status", "group", "sub", "p", "search", Integer.MAX_VALUE, -1);
        handler.searchEvents("org", "topic", "status", "group", "p", "search", 10, 2);
        handler.listOrgDeliveries("org", null, null, null, null, null, 0, null);
        handler.getDeliveryHistory("org", "delivery");
        handler.getEventById("org", "event");
        handler.getEventDeliveries("org", "event", Integer.MAX_VALUE, -1);

        verify(service).publishEvent(eq("org"), eq("group"), eq("topic"), any(), any());
        verify(service).getDeliveryHistory("org", "delivery");
        verify(service).getEventById("org", "event");
        verify(service).getEventDeliveries("org", "event", Integer.MAX_VALUE, -1);
    }

    @Test
    public void subscriptionHandlerForwardsEveryOperation() {
        SubscriptionService service = Mockito.mock(SubscriptionService.class);
        SubscriptionHandler handler = new SubscriptionHandler(service);
        handler.createSubscription(" org ", new SubscriptionDTO());
        handler.createSubscription(null, null);
        handler.listSubscriptions("org", null, null, null, null, -1, null);
        handler.listSubscriptions("org", null, null, null, Integer.MAX_VALUE, 2, null);
        handler.getSubscription("org", "sub");
        handler.deleteSubscription("org", "sub");
        handler.retryVerification("org", "sub");
        handler.listSubscriptionEvents("org", "sub", 0, null);
        handler.listSubscriptionEvents("org", "sub", Integer.MAX_VALUE, 3);
        handler.getSubscriptionEventHistory("org", "sub", "delivery");
        handler.retryDelivery("org", "sub", "delivery");

        verify(service).getSubscription("org", "sub");
        verify(service).deleteSubscription("org", "sub");
        verify(service).retryVerification("org", "sub");
        verify(service).getSubscriptionEventHistory("org", "sub", "delivery");
        verify(service).retryDelivery("org", "sub", "delivery");
    }

    @Test
    public void topicHandlerForwardsEveryOperation() {
        TopicService service = Mockito.mock(TopicService.class);
        TopicHandler handler = new TopicHandler(service);
        handler.createTopic("org", new TopicDTO());
        handler.createTopic("org", null);
        handler.listTopics("org", null, null, null, -1, null);
        handler.listTopics("org", null, null, Integer.MAX_VALUE, 2, null);
        handler.deleteTopic("org", "topic");
        verify(service).deleteTopic("org", "topic");
    }
}
