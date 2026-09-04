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

package org.wso2.dpdp.accelerator.event.notifications.dao.impl;

import org.wso2.dpdp.accelerator.event.notifications.common.constants.EventNotificationCommonConstants;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDataAccessException;
import org.wso2.dpdp.accelerator.event.notifications.dao.constants.EventNotificationDBColumns;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.EventNotificationDuplicateResourceException;
import org.wso2.dpdp.accelerator.common.util.DatabaseUtils;
import org.wso2.dpdp.accelerator.event.notifications.dao.DeliveryAckDAO;
import org.wso2.dpdp.accelerator.event.notifications.dao.model.WebhookDeliveryAck;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationCommonDBQueries;
import org.wso2.dpdp.accelerator.event.notifications.dao.queries.EventNotificationQueryFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class DeliveryAckDAOImpl implements DeliveryAckDAO {

    private EventNotificationCommonDBQueries getQueries(Connection conn) {
        return EventNotificationQueryFactory.getQueryProvider(conn);
    }

    @Override
    public boolean addDeliveryAck(WebhookDeliveryAck ack) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            boolean result = addDeliveryAck(conn, ack);
            DatabaseUtils.commitTransaction(conn);
            return result;
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }

    private boolean addDeliveryAck(Connection conn, WebhookDeliveryAck ack) {
        try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getAddWebhookDeliveryAckQuery())) {
            ps.setString(1, ack.getAckId());
            ps.setString(2, ack.getDeliveryId());
            ps.setTimestamp(3, ack.getCompletedAt());
            ps.setString(4, ack.getCompletionStatus());
            ps.setString(5, ack.getCompletionEvidence());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                throw new EventNotificationDuplicateResourceException(
                        EventNotificationCommonConstants.ERROR_DELIVERY_ACK_ALREADY_EXISTS, e);
            }
            throw new EventNotificationDataAccessException(
                    String.format(EventNotificationCommonConstants.ERROR_ADDING_DELIVERY_ACK, (ack != null ? ack.getAckId() : "null")), e);
        }
    }

    @Override
    public Optional<WebhookDeliveryAck> getDeliveryAckByDeliveryId(String deliveryId) {
        Connection conn = DatabaseUtils.getDBConnection();
        try {
            try (PreparedStatement ps = conn.prepareStatement(getQueries(conn).getGetWebhookDeliveryAckByDeliveryIdQuery())) {
                ps.setString(1, deliveryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        WebhookDeliveryAck ack = new WebhookDeliveryAck(
                                rs.getString(EventNotificationDBColumns.ACK_ID),
                                rs.getString(EventNotificationDBColumns.DELIVERY_ID),
                                rs.getTimestamp(EventNotificationDBColumns.COMPLETED_AT),
                                rs.getString(EventNotificationDBColumns.COMPLETION_STATUS),
                                rs.getString(EventNotificationDBColumns.COMPLETION_EVIDENCE)
                        );
                        DatabaseUtils.commitTransaction(conn);
                        return Optional.of(ack);
                    }
                }
                DatabaseUtils.commitTransaction(conn);
                return Optional.empty();
            } catch (SQLException e) {
                DatabaseUtils.rollbackTransaction(conn);
                throw new EventNotificationDataAccessException(
                        String.format(EventNotificationCommonConstants.ERROR_GETTING_DELIVERY_ACK_BY_DELIVERY_ID, deliveryId), e);
            }
        } catch (RuntimeException e) {
            DatabaseUtils.rollbackTransaction(conn);
            throw e;
        } finally {
            DatabaseUtils.closeConnection(conn);
        }
    }
}
