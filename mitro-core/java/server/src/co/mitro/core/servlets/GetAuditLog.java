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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.annotation.WebServlet;

import co.mitro.analysis.AuditLogProcessor.ActionType;
import co.mitro.core.accesscontrol.AuthenticatedDB;
import co.mitro.core.exceptions.MitroServletException;
import co.mitro.core.server.Manager;
import co.mitro.core.server.data.DBIdentity;
import co.mitro.core.server.data.DBProcessedAudit;
import co.mitro.core.server.data.RPC;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse;
import co.mitro.core.server.data.RPC.MitroRPC;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

@WebServlet("/api/GetAuditLog")
public class GetAuditLog extends MitroServlet {
  private static final long serialVersionUID = 1L;

  private static final Set<ActionType> USER_ACTION_TYPES = Sets.immutableEnumSet(
      ActionType.SIGNUP,
      ActionType.MITRO_LOGIN,
      ActionType.MITRO_AUTO_LOGIN,
      ActionType.EDIT_PASSWORD,
      ActionType.NEW_DEVICE,
      ActionType.INVITED_BY_USER);

  private static final Set<ActionType> SECRET_ACTION_TYPES = Sets.immutableEnumSet(
      ActionType.CREATE_SECRET,
      ActionType.DELETE_SECRET,
      ActionType.EDIT_SECRET,
      ActionType.EDIT_SECRET_ACL,
      ActionType.GRANTED_ACCESS_TO,
      ActionType.REVOKED_ACCESS_TO,
      ActionType.GET_SECRET_CRITICAL_DATA_FOR_LOGIN);

  private static final Set<ActionType> GROUP_ACTION_TYPES = Sets.immutableEnumSet(
      ActionType.CREATE_GROUP,
      ActionType.DELETE_GROUP,
      ActionType.EDIT_GROUP);

  // TODO: These events are currently unsupported
  // INVITE_USER
  // ORG_APPLY_SYNC
  // ORG_VIEW_SYNC
  // ORG_MUTATE

  /* OR a list of ormlite where clauses.
   *
   * rawtypes needed for ant compilation.  Do not remove.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void orWhereClauses(Where<DBProcessedAudit, Integer> where, List<Where<DBProcessedAudit, Integer>> clauses) {
    // If list contains one clause, nothing needs to be done.
    if (clauses.size() == 2) {
      where.or(clauses.get(0), clauses.get(1));
    } else if (clauses.size() > 2) {
      where.or(clauses.get(0), clauses.get(1), clauses.subList(2, clauses.size()).toArray((Where<DBProcessedAudit, Integer>[]) new Where[clauses.size() - 2]));
    }
  }

  // Needed to suppress warning about using a generic array for varargs parameter in WHERE clause below.
  // http://stackoverflow.com/questions/4257883/warning-for-generic-varargs?rq=1
  @SuppressWarnings({ "unchecked" })
  protected List<RPC.AuditEvent> queryAuditEvents(
      Manager manager,
      Set<Integer> userIds,
      Set<Integer> secretIds,
      Set<Integer> groupIds,
      Long limit, Long offset,
      Long startTimeMs, Long endTimeMs) throws SQLException, MitroServletException {
    List<RPC.AuditEvent> events = Lists.newArrayList();

    QueryBuilder<DBProcessedAudit, Integer> query = manager.processedAuditDao.queryBuilder();
    Where<DBProcessedAudit, Integer> where = query.where();

    List<Where<DBProcessedAudit, Integer>> clauses = new ArrayList<>();
    if (!userIds.isEmpty()) {
      clauses.add(where.and(where.in(DBProcessedAudit.ACTOR_FIELD_NAME, userIds),
                            where.in(DBProcessedAudit.ACTION_FIELD_NAME, 
                                Manager.makeSelectArgsFromList(USER_ACTION_TYPES))));
    }
    if (!secretIds.isEmpty()) {
      clauses.add(where.and(where.in(DBProcessedAudit.AFFECTED_SECRET_FIELD_NAME, secretIds),
                            where.in(DBProcessedAudit.ACTION_FIELD_NAME, 
                                Manager.makeSelectArgsFromList(SECRET_ACTION_TYPES))));
    }
    if (!groupIds.isEmpty()) {
      clauses.add(where.and(where.in(DBProcessedAudit.AFFECTED_GROUP_FIELD_NAME, groupIds),
                            where.in(DBProcessedAudit.ACTION_FIELD_NAME, 
                                Manager.makeSelectArgsFromList(GROUP_ACTION_TYPES))));
    }

    if (clauses.size() > 0) {
      orWhereClauses(where, clauses);

      if (startTimeMs != null) {
        where.and(where, where.ge(DBProcessedAudit.TIMESTAMP_FIELD_NAME, startTimeMs));
      }
      if (endTimeMs != null) {
        if (startTimeMs != null && startTimeMs > endTimeMs) {
          throw new MitroServletException("start time must be before end time");
        }
        where.and(where, where.le(DBProcessedAudit.TIMESTAMP_FIELD_NAME, endTimeMs));
      }
      // offset and limit of null interpreted as no offset and no limit.
      // Boolean param of orderBy specifies DESC order.
      query.offset(offset).limit(limit).orderBy(DBProcessedAudit.TIMESTAMP_FIELD_NAME, false);
      for (DBProcessedAudit dbAudit : query.query()) {
        RPC.AuditEvent auditEvent = new RPC.AuditEvent();
        fillRPCAuditEvent(manager, dbAudit, auditEvent);
        events.add(auditEvent);
      }
    }
    return events;
  }

  // userIds, secretIds, and groupIds are output parameters.
  private void getQueryIdsForUser(DBIdentity user, Manager mgr, Set<Integer> userIds, Set<Integer> secretIds, Set<Integer> groupIds)
      throws IOException, SQLException, MitroServletException {
    userIds.clear();
    userIds.add(user.getId());

    // TODO: This is a horrible hack for listing secrets.  We need an API.
    RPC.ListMySecretsAndGroupKeysRequest request = new RPC.ListMySecretsAndGroupKeysRequest();
    ListMySecretsAndGroupKeys servlet = new ListMySecretsAndGroupKeys();
    
    RPC.ListMySecretsAndGroupKeysResponse out = 
        (ListMySecretsAndGroupKeysResponse) servlet.processCommand(new MitroRequestContext(user, gson.toJson(request), mgr, null));

    secretIds.clear();
    secretIds.addAll(out.secretToPath.keySet());

    groupIds.clear();
    groupIds.addAll(out.groups.keySet());
  }

  protected void getQueryIdsForOrg(MitroRequestContext context, int orgId, Set<Integer> userIds, Set<Integer> secretIds, Set<Integer> groupIds)
      throws MitroServletException, SQLException {
    // TODO: We need to create an API for orgs and clean this up.
    @SuppressWarnings("deprecation")
    AuthenticatedDB userDb = AuthenticatedDB.deprecatedNew(context.manager, context.requestor);
    if (!userDb.isOrganizationAdmin(orgId)) {
      throw new MitroServletException("Not org or no access");
    }

    RPC.GetOrganizationStateResponse out = GetOrganizationState.doOperation(context,  orgId);

    userIds.clear();

    // TODO: change GetOrganizationState query to return the user id column.
    if (!out.members.isEmpty()) {
      QueryBuilder<DBIdentity, Integer> query = context.manager.identityDao.queryBuilder();
      query.selectColumns(DBIdentity.ID_NAME);
      Where<DBIdentity, Integer> where = query.where();
      where.in(DBIdentity.NAME_FIELD_NAME, Manager.makeSelectArgsFromList(out.members));

      for (DBIdentity identity : query.query()) {
        userIds.add(identity.getId());
      }
    }

    secretIds.clear();
    secretIds.addAll(out.orgSecretsToPath.keySet());
    secretIds.addAll(out.orphanedSecretsToPath.keySet());

    groupIds.clear();
    groupIds.addAll(out.groups.keySet());
  }

  @Override
  protected MitroRPC processCommand(MitroRequestContext context)
      throws IOException, SQLException, MitroServletException {
    // TODO: Support all audit event types.  Currently just supports events for secret.
    RPC.GetAuditLogRequest in = gson.fromJson(context.jsonRequest, RPC.GetAuditLogRequest.class);

    Set<Integer> userIds = Sets.newHashSet();
    Set<Integer> secretIds = Sets.newHashSet();
    Set<Integer> groupIds = Sets.newHashSet();

    if (in.orgId == null) {
      getQueryIdsForUser(context.requestor, context.manager, userIds, secretIds, groupIds);
    } else {
      getQueryIdsForOrg(context, in.orgId.intValue(), userIds, secretIds, groupIds);
    }

    RPC.GetAuditLogResponse out = new RPC.GetAuditLogResponse();
    out.events = queryAuditEvents(context.manager, userIds, secretIds, groupIds, in.limit, in.offset, in.startTimeMs, in.endTimeMs);

    return out;
  }

  protected static void fillRPCAuditEvent(Manager mgr, 
      final DBProcessedAudit dbAudit,
      RPC.AuditEvent auditEvent) throws SQLException {
    auditEvent.id = dbAudit.getId();

    auditEvent.secretId = dbAudit.getAffectedSecret();

    if (dbAudit.getAffectedGroup() != null) {
      auditEvent.groupId = dbAudit.getAffectedGroup().getId();
    } else {
      auditEvent.groupId = null;
    }
    
    if (dbAudit.getSourceIp() == null) {
      auditEvent.sourceIp = "";
    } else {
      auditEvent.sourceIp = dbAudit.getSourceIp();
    }
    
    auditEvent.action = dbAudit.getAction();

    assert(dbAudit.getActor() != null);
    DBIdentity user = mgr.identityDao.queryForId(dbAudit.getActor().getId());
    auditEvent.userId = user.getId();
    auditEvent.username = user.getName();

    auditEvent.timestampMs = dbAudit.getTimestampMs();

    if (dbAudit.getAffectedUser() != null) {
       auditEvent.affectedUserId = dbAudit.getAffectedUser().getId();
       auditEvent.affectedUsername = dbAudit.getAffectedUserName();
    }
  }
}
