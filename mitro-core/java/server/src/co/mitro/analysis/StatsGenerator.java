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
package co.mitro.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.exceptions.DoEmailVerificationException;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.Templates;
import co.mitro.core.server.data.DBAcl;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBHistoricalOrgState;
import co.mitro.core.server.data.DBHistoricalUserState;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.RPC.GetOrganizationStateResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.GroupInfo;
import co.mitro.core.servlets.GetOrganizationState;
import co.mitro.core.servlets.ListMySecretsAndGroupKeys;
import co.mitro.core.servlets.MitroServlet.MitroRequestContext;

import com.github.mustachejava.Mustache;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;

public class StatsGenerator {
  private static final Mustache userStateTemplate = Templates.compile("user_state.mustache");
  private static final Mustache orgStateTemplate = Templates.compile("org_state.mustache");
  private static final Mustache indexTemplate = Templates.compile("index_state.mustache");
  private static final Logger logger = LoggerFactory.getLogger(StatsGenerator.class);

  public static final class Link implements Comparable<Object> {
    public String text;
    public String url;
    public int count;
    public Link(String t, String u, int count) {
      text = t;
      url = u;
      this.count = count;
    }
    @Override
    public int compareTo(Object o) {
      Link l = (Link) o;
      return (text + url).compareTo(l.text + l.url);
    }
  }

  public static class Snapshot {
    public final ArrayList<DBHistoricalOrgState> orgStateObjects = new ArrayList<>();
    public final ArrayList<DBHistoricalUserState> userStateObjects = new ArrayList<>();
  }

  /**
   * Generate statistics and return newly created objects that have not been committed.
   * @param outDir directory in which to write summary files. Subdirectories outDir/users 
   *        and outDir/orgs must exist. Supply null for no output.
   */
  public static Snapshot generateStatistics(String outDir, Manager manager)
          throws SQLException, IOException, MitroServletException {
    final long runTimestampMs = System.currentTimeMillis();
    Snapshot output = new Snapshot();

    // TODO: don't do this in one gigantic transaction.
    Multimap<Integer, Link> countToFile = TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
    // get all orgs.
    Map<Integer, GroupInfo> orgIdToOrg = Maps.newHashMap();
    for (DBGroup o : DBGroup.getAllOrganizations(manager)) {
      GroupInfo newGi = new GroupInfo();
      newGi.autoDelete = o.isAutoDelete();
      newGi.groupId = o.getId();
      newGi.isTopLevelOrg = true;
      newGi.name = o.getName();
      Set<String> users = Sets.newHashSet();
      for (DBGroup orgGroup : o.getAllOrgGroups(manager)) {
        users.add(orgGroup.getName());
      }
      newGi.users = Lists.newArrayList(users);
      orgIdToOrg.put(newGi.groupId, newGi);
    }
    int numPeople = 0;
    for (DBIdentity id : manager.identityDao.queryForAll()) {
      ++numPeople;
      try {
        logger.info(id.getName() + ": " + id.getGuidCookie());
        DBHistoricalUserState userState = getHistoricalUserState(manager,
            runTimestampMs, orgIdToOrg, id);
        output.userStateObjects.add(userState);

        String filename = id.getName() + ".html";
        renderIfOutputEnabled(outDir, "/users/" + filename, userStateTemplate, userState);
        countToFile.put(userState.numSecrets, new Link(id.getName(), filename, userState.numSecrets));
      } catch (MitroServletException e) {
        logger.error("UNKNOWN ERROR", e);
      }
    }
    renderIfOutputEnabled(outDir, "/users/index.html", indexTemplate, countToFile.values());

    countToFile.clear();
    int numOrgs = 0;
    // now do the orgs
    for (DBGroup org : DBGroup.getAllOrganizations(manager)) {
      ++numOrgs;
      // hack to make this work
      Set<Integer> admins = Sets.newHashSet();
      org.putDirectUsersIntoSet(admins, DBAcl.adminAccess());
      int userId = admins.iterator().next();
      DBIdentity dbi = manager.identityDao.queryForId(userId);
      MitroRequestContext context = new MitroRequestContext(dbi, null, manager, null);
      GetOrganizationStateResponse resp = GetOrganizationState.doOperation(context, org.getId());
      DBHistoricalOrgState orgState = new DBHistoricalOrgState(resp, org.getId(), runTimestampMs);
      output.orgStateObjects.add(orgState);

      String filename = org.getId() + ".html";
      renderIfOutputEnabled(outDir, "/orgs/" + filename, orgStateTemplate, orgState);
      countToFile.put(orgState.numMembers + orgState.numAdmins, 
          new Link(org.getName() + org.getId(), org.getId() + ".html", orgState.numAdmins + orgState.numMembers));
    }
    renderIfOutputEnabled(outDir, "/orgs/index.html", indexTemplate, countToFile.values());
    renderIfOutputEnabled(outDir, "/index.html", indexTemplate, ImmutableList.of(
        new Link("organizations", "orgs/index.html", numOrgs),
        new Link("users", "users/index.html", numPeople)));

    return output;
  }

  public static DBHistoricalUserState getHistoricalUserState(Manager manager,
      final long runTimestampMs, Map<Integer, GroupInfo> orgIdToOrg,
      DBIdentity id) throws SQLException, MitroServletException,
      DoEmailVerificationException {
    MitroRequestContext context = new MitroRequestContext(id, null, manager, null);
    ListMySecretsAndGroupKeysResponse out = ListMySecretsAndGroupKeys.executeWithoutAuditLog(context);
    DBHistoricalUserState userState = new DBHistoricalUserState(out, orgIdToOrg, runTimestampMs);
    userState.referrerDomain = id.getReferrer();
    return userState;
  }

  /**
   * Renders template with scope to a file located at outDir + suffix.
   *
   * @param outDir must not end with /
   * @param suffix must start with /
   * @param template
   * @param scope
   */
  private static void renderIfOutputEnabled(
      String outDir, String suffix, Mustache template, Object scope) throws IOException {
    assert suffix.charAt(0) == '/';
    if (outDir != null) {
      assert !outDir.endsWith("/");
      String path = outDir + suffix;
      try (BufferedWriter writer = Files.newWriter(new File(path), Charsets.UTF_8)) {
        template.execute(writer, scope);
      }
    }
  }
}
