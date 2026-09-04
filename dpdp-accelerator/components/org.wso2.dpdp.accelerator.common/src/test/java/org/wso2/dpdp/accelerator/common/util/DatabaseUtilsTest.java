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

package org.wso2.dpdp.accelerator.common.util;

import org.mockito.InOrder;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.common.exception.DPDPCommonRuntimeException;
import org.wso2.dpdp.accelerator.common.persistence.JDBCPersistenceManager;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.expectThrows;

/**
 * {@code dataSource} and {@code instance} are both process-wide static singletons on
 * {@link JDBCPersistenceManager}, so every test resets both - a mock datasource is pre-set
 * before each test (letting {@code getInstance()} construct successfully without a real JNDI
 * context), and the one test that exercises the JNDI-failure path clears it again first.
 */
public class DatabaseUtilsTest {

    private DataSource dataSource;

    @BeforeMethod
    public void setUpDefaultDataSource() throws Exception {
        dataSource = mock(DataSource.class);
        setStaticDataSource(dataSource);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        setStaticDataSource(null);
        setStaticInstance(null);
    }

    @Test
    public void getDBConnectionDisablesAutoCommitAndReturnsTheConnection() throws Exception {

        Connection connection = mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);

        Connection returned = DatabaseUtils.getDBConnection();

        assertSame(returned, connection);
        verify(connection).setAutoCommit(false);
    }

    @Test
    public void getDBConnectionWrapsFailureWhenDatasourceIsUnavailable() throws Exception {

        setStaticDataSource(null);
        setStaticInstance(null);

        expectThrows(DPDPCommonRuntimeException.class, DatabaseUtils::getDBConnection);
    }

    @Test
    public void commitTransactionCommitsTheConnection() throws SQLException {

        Connection connection = mock(Connection.class);
        DatabaseUtils.commitTransaction(connection);
        verify(connection).commit();
    }

    @Test
    public void commitTransactionSwallowsSqlException() throws SQLException {

        Connection connection = mock(Connection.class);
        doThrow(new SQLException("boom")).when(connection).commit();
        DatabaseUtils.commitTransaction(connection);
    }

    @Test
    public void rollbackTransactionRollsBackTheConnection() throws SQLException {

        Connection connection = mock(Connection.class);
        DatabaseUtils.rollbackTransaction(connection);
        verify(connection).rollback();
    }

    @Test
    public void rollbackTransactionSwallowsSqlException() throws SQLException {

        Connection connection = mock(Connection.class);
        doThrow(new SQLException("boom")).when(connection).rollback();
        DatabaseUtils.rollbackTransaction(connection);
    }

    @Test
    public void closeConnectionClosesANonNullConnection() throws SQLException {

        Connection connection = mock(Connection.class);
        DatabaseUtils.closeConnection(connection);
        verify(connection).close();
    }

    @Test
    public void closeConnectionRollsBackAnOpenTransactionBeforeClosing() throws SQLException {

        Connection connection = mock(Connection.class);
        Mockito.when(connection.getAutoCommit()).thenReturn(false);

        DatabaseUtils.closeConnection(connection);

        InOrder inOrder = Mockito.inOrder(connection);
        inOrder.verify(connection).rollback();
        inOrder.verify(connection).close();
    }

    @Test
    public void closeConnectionDoesNotRollBackWhenAutoCommitIsOn() throws SQLException {

        Connection connection = mock(Connection.class);
        Mockito.when(connection.getAutoCommit()).thenReturn(true);

        DatabaseUtils.closeConnection(connection);

        verify(connection, Mockito.never()).rollback();
        verify(connection).close();
    }

    @Test
    public void closeConnectionStillClosesWhenTheRollbackFails() throws SQLException {

        Connection connection = mock(Connection.class);
        Mockito.when(connection.getAutoCommit()).thenReturn(false);
        doThrow(new SQLException("boom")).when(connection).rollback();

        DatabaseUtils.closeConnection(connection);

        verify(connection).close();
    }

    @Test
    public void closeConnectionToleratesNull() {

        DatabaseUtils.closeConnection(null);
    }

    @Test
    public void closeConnectionSwallowsSqlException() throws SQLException {

        Connection connection = mock(Connection.class);
        doThrow(new SQLException("boom")).when(connection).close();
        DatabaseUtils.closeConnection(connection);
    }

    private static void setStaticDataSource(DataSource dataSource) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(null, dataSource);
    }

    private static void setStaticInstance(JDBCPersistenceManager instance) throws Exception {
        Field field = JDBCPersistenceManager.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, instance);
    }
}
