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
package org.wso2.dpdp.accelerator.event.notifications.endpoint.api;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.handler.EventHandler;

import javax.ws.rs.core.Response;

import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

public class DeliveryEndpointTest {

    @Mock
    private EventHandler eventHandler;
    private DeliveryEndpoint endpoint;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        endpoint = new DeliveryEndpoint(eventHandler, () -> "org1");
    }

    @Test
    public void completeDeliveryDelegatesAndReturnsNoContent() {
        String body = "{\n  \"completionEvidence\": \"https://example.com/receipt\","
                + "\n  \"completionStatus\": \"completed\"\n}";
        Response response = endpoint.completeDelivery("delivery-1", "group-1", "sha256=signature", body);

        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
        org.testng.Assert.assertNull(response.getEntity());
        verify(eventHandler).completeDelivery("org1", "group-1", "delivery-1", body, "sha256=signature");
    }
}
