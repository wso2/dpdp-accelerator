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

package org.wso2.dpdp.accelerator.event.notifications.endpoint.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.DeliveryMode;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.PurposeFilterMode;
import java.io.IOException;

/** Preserves the service's existing enum aliases and malformed-JSON error handling. */
public final class RequestEnumDeserializers {
    private RequestEnumDeserializers() {
    }

    public static final class Status extends JsonDeserializer<
            org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus> {
        @Override
        public org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus deserialize(
                JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.currentToken().isScalarValue()) {
                return (org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus)
                        context.handleUnexpectedToken(
                                org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus.class, parser);
            }
            org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus status =
                    org.wso2.dpdp.accelerator.event.notifications.common.enums.SubscriptionStatus
                            .fromValue(parser.getValueAsString());
            return status == null ? null :
                    org.wso2.dpdp.accelerator.event.notifications.endpoint.dto.SubscriptionStatus
                            .fromValue(status.getValue());
        }
    }

    public static final class Delivery extends JsonDeserializer<DeliveryMode> {
        @Override
        public DeliveryMode deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.currentToken().isScalarValue()) {
                return (DeliveryMode) context.handleUnexpectedToken(DeliveryMode.class, parser);
            }
            String value = parser.getValueAsString();
            org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode mode =
                    org.wso2.dpdp.accelerator.event.notifications.common.enums.DeliveryMode.fromValue(value);
            return mode == null ? null : DeliveryMode.fromValue(mode.getValue());
        }
    }

    public static final class FilterMode extends JsonDeserializer<PurposeFilterMode> {
        @Override
        public PurposeFilterMode deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.currentToken().isScalarValue()) {
                return (PurposeFilterMode) context.handleUnexpectedToken(PurposeFilterMode.class, parser);
            }
            String value = parser.getValueAsString();
            org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode mode =
                    org.wso2.dpdp.accelerator.event.notifications.common.enums.PurposeFilterMode.fromValue(value);
            return mode == null ? null : PurposeFilterMode.fromValue(mode.getValue());
        }
    }
}
