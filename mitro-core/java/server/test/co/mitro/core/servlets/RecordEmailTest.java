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
package co.mitro.core.servlets;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.server.data.DBEmailRecord;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class RecordEmailTest extends MemoryDBFixture {
  private RecordEmail servlet;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @Before
  public void setUp() {
    servlet = new RecordEmail(managerFactory, null);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @Test
  public void testMissingEmail() throws IOException {
    servlet.doPost(request, response);
    assertIsErrorRedirect();
  }

  private void assertIsErrorRedirect() {
    String[] parts = response.getHeader("Location").split("\\?");
    assertEquals(RecordEmail.ERROR_PATH, parts[0]);
  }

  @Test
  public void testBadEmails() throws IOException {
    request.setParameter("email", "");
    servlet.doPost(request, response);
    assertIsErrorRedirect();
    request.setParameter("email", "notanemail");
    assertIsErrorRedirect();
  }

  @Test
  public void testSuccessNoName() throws IOException, SQLException {
    request.setParameter("email", "user+suffix@example.com");
    servlet.doPost(request, response);
    assertEquals(RecordEmail.SUCCESS_PATH, response.getHeader("Location"));
    assertEquals(1L, manager.emailRecordDao.countOf());
  }

  @Test
  public void testSuccessWithName() throws IOException, SQLException {
    request.setParameter("email", "user+suffix@example.com");
    request.setParameter("fullName", "Hello world");
    servlet.doPost(request, response);
    assertEquals(RecordEmail.SUCCESS_PATH, response.getHeader("Location"));
    assertEquals(1L, manager.emailRecordDao.countOf());
    DBEmailRecord record = manager.emailRecordDao.iterator().next();
    assertEquals("Hello world", record.getFullName());
  }
}
