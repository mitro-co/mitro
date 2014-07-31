/*******************************************************************************
 * Copyright (c) 2013, 2014 Lectorius, Inc.
 * Authors:
 * Vijay Pandurangan (vijayp@mitro.co)
 * Evan Jones (ej@mitro.co)
 * Adam Hilss (ahilss@mitro.co)
 *
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You can contact the authors at inbound@mitro.co.
 *******************************************************************************/
package co.mitro.core.server.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.Date;

import org.junit.Test;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.DatabaseConnection;
import com.j256.ormlite.table.TableUtils;

public class DBEmailQueueTest {
  @Test
  public void testType() {
    // verifies that fromString returns the exact same instance, so == works.
    DBEmailQueue.Type roundTripped = DBEmailQueue.Type.fromString(DBEmailQueue.Type.INVITE.getValue());
    assertTrue(DBEmailQueue.Type.INVITE.equals(roundTripped));
    assertTrue(DBEmailQueue.Type.INVITE == roundTripped);
    assertEquals(DBEmailQueue.Type.INVITE,
        DBEmailQueue.Type.fromString(DBEmailQueue.Type.INVITE.getValue()));

    // verifies that fromString uses .equals and not == (previous bug)
    String copy = new String(DBEmailQueue.Type.INVITE.getValue());
    assertEquals(DBEmailQueue.Type.INVITE, DBEmailQueue.Type.fromString(copy));

    copy = new String(DBEmailQueue.Type.ADDRESS_VERIFICATION.getValue());
    assertEquals(DBEmailQueue.Type.ADDRESS_VERIFICATION, DBEmailQueue.Type.fromString(copy));
    assertEquals(DBEmailQueue.Type.ISSUE_REPORTED,
        DBEmailQueue.Type.fromString(DBEmailQueue.Type.ISSUE_REPORTED.getValue()));
  }

  @Test
  public void testMakeInvitation() {
    DBEmailQueue mandrillEmail = DBEmailQueue.makeInvitation("a@mitro.co", "b@mitro.co", "pass");
    assertEquals(DBEmailQueue.Type.MANDRILL_INVITE, mandrillEmail.getType());
    assertEquals(DBEmailQueue.Type.MANDRILL_INVITE.getValue(), mandrillEmail.mandrillTemplateName);
    assertArrayEquals(new String[]{null, null, null, null, "b@mitro.co"}, mandrillEmail.getArguments());
  }

  @Test
  public void testAddressVerification() {
    // TODO: Remove when we switch over to mandrill.
    DBEmailQueue email = DBEmailQueue.makeAddressVerification("foo", "bar", "https://mitro.co");
    assertEquals(DBEmailQueue.Type.ADDRESS_VERIFICATION, email.getType());
    assertArrayEquals(new String[]{"foo", "bar"}, email.getArguments());

    DBEmailQueue mandrillEmail = DBEmailQueue.makeAddressVerification("a@mitro.co", "bar", "https://mitro.co");
    assertEquals(DBEmailQueue.Type.MANDRILL_ADDRESS_VERIFICATION, mandrillEmail.getType());
    assertEquals(DBEmailQueue.Type.MANDRILL_ADDRESS_VERIFICATION.getValue(), mandrillEmail.mandrillTemplateName);
    assertArrayEquals(new String[]{null, null, null, null, "a@mitro.co"}, mandrillEmail.getArguments());
  }

  @Test
  public void testNewDeviceVerification() {
    DBEmailQueue email = DBEmailQueue.makeNewDeviceVerification("foo", "bar", "baz", "qux", "https://mitro.co");
    assertEquals(DBEmailQueue.Type.LOGIN_ON_NEW_DEVICE, email.getType());
    assertArrayEquals(new String[]{"foo", "bar", "baz"}, email.getArguments());

    DBEmailQueue mandrillEmail = DBEmailQueue.makeNewDeviceVerification("a@mitro.co", "bar", "baz", "qux", "https://mitro.co");
    assertEquals(DBEmailQueue.Type.MANDRILL_LOGIN_ON_NEW_DEVICE, mandrillEmail.getType());
    assertEquals(DBEmailQueue.Type.MANDRILL_LOGIN_ON_NEW_DEVICE.getValue(), mandrillEmail.mandrillTemplateName);
    assertArrayEquals(new String[]{null, null, null, null, "a@mitro.co"}, mandrillEmail.getArguments());
  }

  @Test
  public void testIssueReported() {
    assertEquals(DBEmailQueue.Type.ISSUE_REPORTED,
        DBEmailQueue.Type.fromString(DBEmailQueue.Type.ISSUE_REPORTED.getValue()));

    // this converts the integer to a string ... maybe it shouldn't, but it does
    DBEmailQueue issue = DBEmailQueue.makeNewIssue("email", "url", "type", "description", 123);
    assertEquals(5, issue.getArguments().length);
    assertEquals("123", issue.getArguments()[4]);
  }
  
  
  @Test
  public void testTimeZone() throws SQLException {
    // create a temporary H2 connection
    JdbcConnectionSource connection = new JdbcConnectionSource("jdbc:h2:mem:");
    TableUtils.createTable(connection, DBEmailQueue.class);
    Dao<DBEmailQueue, Integer> dao = DaoManager.createDao(connection, DBEmailQueue.class);

    DBEmailQueue email = DBEmailQueue.makeInvitation("s@o.com", "r@e.com", "pw");
    dao.create(email);

    // Force a daylight savings time string in the DB
    setRawDate(connection, email.getId(), "2013-05-17T14:47:59.864022");
    Date t = dao.queryForId(email.getId()).getAttemptedTime();
    assertEquals("2013-05-17T14:47:59.864Z", DBEmailQueue.getUtcIsoFormat().format(t));
    assertEquals(1368802079864L, t.getTime());

    // Set a date/time in standard time, not daylight time
    setRawDate(connection, email.getId(), "2013-11-04T15:38:11.997012");
    t = dao.queryForId(email.getId()).getAttemptedTime();
    assertEquals("2013-11-04T15:38:11.997Z", DBEmailQueue.getUtcIsoFormat().format(t));
    assertEquals(1383579491997L, t.getTime());
  }

  public void setRawDate(JdbcConnectionSource connection, int emailQueueId, String dateTimeString)
      throws SQLException {
    // Do a raw update to ensure we get the right time string in the DB
    String raw = String.format("UPDATE email_queue SET attempted_time='%s' WHERE id=%d",
        dateTimeString, emailQueueId);
    int result = connection.getReadWriteConnection().executeStatement(
        raw, DatabaseConnection.DEFAULT_RESULT_FLAGS);
    // updates 1 row
    assertEquals(1, result);
  }
}
