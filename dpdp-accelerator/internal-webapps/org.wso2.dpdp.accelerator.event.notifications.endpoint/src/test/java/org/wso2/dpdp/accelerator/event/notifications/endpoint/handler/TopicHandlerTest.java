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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.handler;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.event.notifications.service.TopicService;
import org.wso2.dpdp.accelerator.event.notifications.service.dto.TopicDTO;
import org.wso2.dpdp.accelerator.event.notifications.service.model.PaginatedResult;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TopicHandlerTest {

    @Mock
    private TopicService topicService;

    private TopicHandler topicHandler;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        topicHandler = new TopicHandler(topicService);
    }

    @Test
    public void testCreateTopic() {
        TopicDTO req = new TopicDTO();
        req.setName("user-consent");
        req.setDescription("desc");

        TopicDTO dto = new TopicDTO("t1", "user-consent", "desc", "active");
        when(topicService.createTopic("org1", "user-consent", "desc")).thenReturn(dto);

        TopicDTO response = topicHandler.createTopic("org1", req);
        assertNotNull(response);
        assertEquals(response.getTopicId(), "t1");
        assertEquals(response.getName(), "user-consent");
        assertEquals(response.getStatus(), "active");
    }

    @Test
    public void testListTopics() {
        TopicDTO dto = new TopicDTO("t1", "user-consent", "desc", "active");
        PaginatedResult<TopicDTO> serviceResult = new PaginatedResult<>(Collections.singletonList(dto), 1);
        when(topicService.listTopics(anyString(), anyString(), any(), anyInt(), anyInt(), any())).thenReturn(serviceResult);

        PaginatedResult<TopicDTO> response = topicHandler.listTopics("org1", "active", null, 10, 0, "asc");
        assertNotNull(response);
        assertEquals(response.getTotal(), 1);
        assertEquals(response.getItems().size(), 1);
    }

    @Test
    public void testDeleteTopic() {
        TopicDTO dto = new TopicDTO("t1", "user-consent", "desc", "deleted");
        when(topicService.deleteTopic("org1", "t1")).thenReturn(dto);

        TopicDTO response = topicHandler.deleteTopic("org1", "t1");
        assertNotNull(response);
        assertEquals(response.getTopicId(), "t1");
        assertEquals(response.getStatus(), "deleted");
    }
}
