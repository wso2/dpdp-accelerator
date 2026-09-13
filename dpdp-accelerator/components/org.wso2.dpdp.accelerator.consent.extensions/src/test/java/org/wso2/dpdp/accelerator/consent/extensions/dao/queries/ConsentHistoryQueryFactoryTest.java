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

package org.wso2.dpdp.accelerator.consent.extensions.dao.queries;

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Covers the dialect routing in {@link ConsentHistoryQueryFactory} - the branch that picks a
 * MySQL provider, the ANSI fallback, and the metadata-based resolution used by the DAO.
 */
public class ConsentHistoryQueryFactoryTest {

    @Test
    public void resolvesMysqlProviderForMysqlDialect() {

        assertTrue(ConsentHistoryQueryFactory.getQueryProvider("MySQL") instanceof ConsentHistoryMysqlDBQueries);
    }

    @Test
    public void fallsBackToAnsiBaselineForOtherDialects() {

        ConsentHistoryCommonDBQueries queries = ConsentHistoryQueryFactory.getQueryProvider("H2");

        assertEquals(queries.getClass(), ConsentHistoryCommonDBQueries.class);
    }

    @Test
    public void blankAndNullDialectsResolveToTheDefaultProvider() {

        assertSame(ConsentHistoryQueryFactory.getQueryProvider((String) null),
                ConsentHistoryQueryFactory.getQueryProvider());
        assertSame(ConsentHistoryQueryFactory.getQueryProvider("  "),
                ConsentHistoryQueryFactory.getQueryProvider());
    }

    @Test
    public void providersAreCachedPerDialect() {

        assertSame(ConsentHistoryQueryFactory.getQueryProvider("MySQL"),
                ConsentHistoryQueryFactory.getQueryProvider("mysql"));
    }

    @Test
    public void resolvesProviderFromConnectionMetadata() throws Exception {

        try (Connection connection = DriverManager
                .getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")) {
            assertEquals(ConsentHistoryQueryFactory.getQueryProvider(connection).getClass(),
                    ConsentHistoryCommonDBQueries.class);
        }
    }

    @Test
    public void nullOrUnusableConnectionFallsBackToTheDefaultProvider() throws Exception {

        assertSame(ConsentHistoryQueryFactory.getQueryProvider((Connection) null),
                ConsentHistoryQueryFactory.getQueryProvider());

        // A closed connection makes getMetaData() throw - the factory swallows it and falls back.
        Connection closed = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        closed.close();
        assertSame(ConsentHistoryQueryFactory.getQueryProvider(closed),
                ConsentHistoryQueryFactory.getQueryProvider());
    }
}
