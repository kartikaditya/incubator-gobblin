package gobblin.writer.jdbc;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.configuration.WorkUnitState;
import gobblin.publisher.JdbcPublisher;
import gobblin.writer.commands.JdbcWriterCommands;
import gobblin.writer.commands.JdbcWriterCommandsFactory;

import org.mockito.InOrder;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = {"gobblin.writer"})
public class JdbcPublisherTest {
  private String stagingTable = "stg";
  private String destinationTable = "dest";
  private State state;
  private JdbcWriterCommands commands;
  private JdbcWriterCommandsFactory factory;
  private Connection conn;
  private WorkUnitState workUnitState;
  private Collection<WorkUnitState> workUnitStates;
  private JdbcPublisher publisher;

  @BeforeMethod
  private void setup() {
    state = new State();
    state.setProp(ConfigurationKeys.JDBC_PUBLISHER_FINAL_TABLE_NAME, destinationTable);

    commands = mock(JdbcWriterCommands.class);
    factory = mock(JdbcWriterCommandsFactory.class);
    when(factory.newInstance(any(State.class))).thenReturn(commands);

    conn = mock(Connection.class);

    workUnitStates = new ArrayList<>();
    workUnitState = mock(WorkUnitState.class);
    when(workUnitState.getProp(ConfigurationKeys.WRITER_STAGING_TABLE)).thenReturn(stagingTable);
    workUnitStates.add(workUnitState);

    publisher = new JdbcPublisher(state, factory);
    publisher = spy(publisher);
    doReturn(conn).when(publisher).createConnection();
  }

  @AfterMethod
  private void cleanup() throws IOException {
    publisher.close();
  }

  public void testPublish() throws IOException, SQLException {
    publisher.publish(workUnitStates);

    InOrder inOrder = inOrder(conn, commands, workUnitState);

    inOrder.verify(conn, times(1)).setAutoCommit(false);
    inOrder.verify(commands, times(1)).copyTable(conn, stagingTable, destinationTable);
    inOrder.verify(workUnitState, times(1)).setWorkingState(WorkUnitState.WorkingState.COMMITTED);
    inOrder.verify(conn, times(1)).commit();
    inOrder.verify(conn, times(1)).close();

    verify(commands, never()).deleteAll(conn, destinationTable);
  }

  public void testPublishReplaceOutput() throws IOException, SQLException {
    state.setProp(ConfigurationKeys.JDBC_PUBLISHER_REPLACE_FINAL_TABLE, Boolean.toString(true));
    publisher.publish(workUnitStates);

    InOrder inOrder = inOrder(conn, commands, workUnitState);

    inOrder.verify(conn, times(1)).setAutoCommit(false);
    inOrder.verify(commands, times(1)).deleteAll(conn, destinationTable);
    inOrder.verify(commands, times(1)).copyTable(conn, stagingTable, destinationTable);
    inOrder.verify(workUnitState, times(1)).setWorkingState(WorkUnitState.WorkingState.COMMITTED);
    inOrder.verify(conn, times(1)).commit();
    inOrder.verify(conn, times(1)).close();
  }

  public void testPublishFailure() throws SQLException, IOException {
    doThrow(RuntimeException.class).when(commands).copyTable(conn, stagingTable, destinationTable);

    try {
      publisher.publish(workUnitStates);
      Assert.fail("Test case didn't throw Exception.");
    } catch (RuntimeException e) {
      Assert.assertTrue(e instanceof RuntimeException);
    }

    InOrder inOrder = inOrder(conn, commands, workUnitState);

    inOrder.verify(conn, times(1)).setAutoCommit(false);
    inOrder.verify(commands, times(1)).copyTable(conn, stagingTable, destinationTable);

    inOrder.verify(conn, times(1)).rollback();
    inOrder.verify(conn, times(1)).close();

    verify(conn, never()).commit();
    verify(commands, never()).deleteAll(conn, destinationTable);
    verify(workUnitState, never()).setWorkingState(any(WorkUnitState.WorkingState.class));
  }
}