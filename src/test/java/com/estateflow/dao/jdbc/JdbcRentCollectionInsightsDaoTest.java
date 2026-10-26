package com.estateflow.dao.jdbc;

import com.estateflow.db.ConnectionHandle;
import com.estateflow.db.DatabaseManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcRentCollectionInsightsDaoTest {

    @Test
    void paymentBehaviorBindsOnlyItsDateRangeAndOptionalProperty() throws Exception {
        assertPaymentBehaviorBindings(null, 2);
        assertPaymentBehaviorBindings(17L, 3);
    }

    private static void assertPaymentBehaviorBindings(Long propertyId, int expectedParameters) throws Exception {
        DatabaseManager manager = mock(DatabaseManager.class);
        ConnectionHandle handle = mock(ConnectionHandle.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(manager.acquire()).thenReturn(handle);
        when(handle.connection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertEquals(expectedParameters, (int) sql.chars().filter(character -> character == '?').count());
            return statement;
        });
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Map<Integer, Object> bound = new HashMap<>();
        doAnswer(invocation -> {
            int index = invocation.getArgument(0);
            if (index > expectedParameters) throw new SQLException("Parameter index out of range", "S1009");
            bound.put(index, invocation.getArgument(1));
            return null;
        }).when(statement).setObject(anyInt(), any());
        doAnswer(invocation -> {
            int index = invocation.getArgument(0);
            if (index > expectedParameters) throw new SQLException("Parameter index out of range", "S1009");
            bound.put(index, invocation.getArgument(1));
            return null;
        }).when(statement).setLong(anyInt(), org.mockito.ArgumentMatchers.anyLong());

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        new JdbcRentCollectionInsightsDao(manager).paymentBehavior(from, to, propertyId, true);

        assertEquals(from, bound.get(1));
        assertEquals(to, bound.get(2));
        if (propertyId != null) assertEquals(propertyId, bound.get(3));
        assertEquals(expectedParameters, bound.size());
    }
}
