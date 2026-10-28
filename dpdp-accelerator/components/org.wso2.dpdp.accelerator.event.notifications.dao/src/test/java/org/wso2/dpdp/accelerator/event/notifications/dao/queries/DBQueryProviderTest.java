/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

public class DBQueryProviderTest {

    @Test
    public void queryProvidersExposeNonEmptyQueries() throws Exception {
        for (EventNotificationCommonDBQueries provider : new EventNotificationCommonDBQueries[] {
                new EventNotificationCommonDBQueries(), new EventNotificationMysqlDBQueries(),
                new EventNotificationH2DBQueries(), new EventNotificationPostgresDBQueries() }) {
            for (Method method : EventNotificationCommonDBQueries.class.getMethods()) {
                if (method.getDeclaringClass() == EventNotificationCommonDBQueries.class
                        && method.getReturnType() == String.class && method.getParameterCount() == 0) {
                    Assert.assertFalse(((String) method.invoke(provider)).trim().isEmpty(), method.getName());
                }
            }
        }
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("postgres")
                instanceof EventNotificationPostgresDBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("mysql")
                instanceof EventNotificationMysqlDBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("h2") instanceof EventNotificationH2DBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider() instanceof EventNotificationH2DBQueries);
        EventNotificationCommonDBQueries common = new EventNotificationCommonDBQueries();
        Assert.assertTrue(common.getPromoteUserTopicsToSystemQuery(2).contains("INITIATED_BY = 'system'"));
        Assert.assertTrue(common.getFindOrgsWithMissingSystemTopicsQuery(2).contains("GROUP BY ORG_ID"));
    }

    @Test
    public void pendingWebhookClaimHonorsRetrySchedule() {
        String query = new EventNotificationCommonDBQueries().getClaimWebhookDeliveryQuery();

        Assert.assertTrue(query.contains("NEXT_RETRY_AT IS NULL OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP"));
    }

    @Test
    public void transactionalFanOutQueriesUseLocks() {
        EventNotificationCommonDBQueries common = new EventNotificationCommonDBQueries();

        Assert.assertTrue(common.getActiveTopicByOrgAndNameForUpdateQuery().endsWith("FOR UPDATE"));
        Assert.assertTrue(common.getActiveSubscriptionsForFanOutQuery().endsWith("FOR UPDATE"));
        Assert.assertTrue(common.getAddEventQuery().contains("STATUS = 'active'"));
        Assert.assertTrue(common.getLockSubscriptionForVerificationQuery().contains("DELIVERY_MODE = 'webhook'"));
        Assert.assertTrue(common.getLockSubscriptionForVerificationQuery().contains("STATUS = ?"));
        Assert.assertTrue(common.getUpdatePollDeliveryStatusByDeliveryAndSubscriptionQuery()
                .contains("STATUS = 'pending'"));

        // MySQL override must also lock, but via a point-lookup on ACTIVE_NAME.
        Assert.assertTrue(new EventNotificationMysqlDBQueries()
                .getActiveTopicByOrgAndNameForUpdateQuery().endsWith("FOR UPDATE"));
    }

    /**
     * Guards the MySQL-specific deadlock fix: the override must reference {@code ACTIVE_NAME}
     * (the stored generated column) rather than {@code LOWER(NAME)} so that InnoDB can use
     * {@code UQ_TOPIC_ORG_ACTIVE_NAME} for a single-row seek, eliminating the gap locks that
     * cause the AB-BA deadlock with concurrent subscription-creation transactions.
     *
     * @see EventNotificationMysqlDBQueries#getActiveTopicByOrgAndNameForUpdateQuery()
     */
    @Test
    public void mysqlTopicLockUsesGeneratedColumnNotFunctionCall() {
        String mysqlQuery = new EventNotificationMysqlDBQueries().getActiveTopicByOrgAndNameForUpdateQuery();
        String commonQuery = new EventNotificationCommonDBQueries().getActiveTopicByOrgAndNameForUpdateQuery();

        // MySQL override must use the generated column so the index UQ_TOPIC_ORG_ACTIVE_NAME is used.
        Assert.assertTrue(mysqlQuery.contains("ACTIVE_NAME"),
                "MySQL override must reference the ACTIVE_NAME generated column for an index seek");

        // It must NOT use LOWER(NAME), which bypasses the index and causes a gap-locking range scan.
        Assert.assertFalse(mysqlQuery.contains("LOWER(NAME)"),
                "MySQL override must not use LOWER(NAME) — that bypasses UQ_TOPIC_ORG_ACTIVE_NAME");

        // The common (non-MySQL) query should still use LOWER(NAME) for portability.
        Assert.assertTrue(commonQuery.contains("LOWER(NAME)"),
                "Common query should still use LOWER(NAME) for non-MySQL dialects");

        // Both must still end with FOR UPDATE.
        Assert.assertTrue(mysqlQuery.endsWith("FOR UPDATE"), "MySQL override must still end with FOR UPDATE");
        Assert.assertTrue(commonQuery.endsWith("FOR UPDATE"), "Common query must still end with FOR UPDATE");
    }
}

