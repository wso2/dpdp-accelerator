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

package org.wso2.dpdp.accelerator.portal.webapp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

/**
 * HTTP response helpers implementing the {@code {code, message}} error
 * envelope the SPA expects.
 */
public final class HttpUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpUtil() {
    }

    public static ObjectMapper mapper() {

        return MAPPER;
    }

    public static void sendJson(HttpServletResponse response, int status, Object body) throws IOException {

        response.setStatus(status);
        response.setContentType(PortalConstants.CONTENT_TYPE_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(response.getWriter(), body);
    }

    public static void sendError(HttpServletResponse response, int status, String code, String message)
            throws IOException {

        ObjectNode body = MAPPER.createObjectNode();
        body.put("code", code);
        body.put("message", message);
        sendJson(response, status, body);
    }
}
