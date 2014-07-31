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
package co.mitro.access.servlets;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import co.mitro.core.servlets.MemoryDBFixture;
import co.mitro.test.MockHttpServletRequest;
import co.mitro.test.MockHttpServletResponse;

public class ManageAccessEmailerTest extends MemoryDBFixture {

  private ManageAccessEmailer emailer;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @Before
  public void setup() {
    emailer = new ManageAccessEmailer(managerFactory, null);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }
  
  @Test
  public void noParameters() throws Exception {
    emailer.handleRequest(request, response);
    assertEquals("{\"errorMessage\":\"Please enter a valid email address\"}", response.getOutput());
  }
  
  @Test
  public void invalidParameters() throws Exception {
    request.setParameter(MitroAccessEmailer.FORM_RECIPIENT_EMAIL_ID, "2234@#$");
    emailer.handleRequest(request, response);
    assertEquals("{\"errorMessage\":\"Please enter a valid email address\"}", response.getOutput());
  }
  
  @Test
  public void validParameters() throws Exception {
    request.setParameter(MitroAccessEmailer.FORM_RECIPIENT_EMAIL_ID, "a@a.com");
    emailer.handleRequest(request, response);
    assertEquals(1, manager.mitroAccessEmailDao.countOf());
    assertEquals("{\"successMessage\":\"Message successfully sent\"}", response.getOutput());
  }

}
