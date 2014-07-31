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

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.mitro.core.alerts.EmailAlertManager;
import co.mitro.core.server.Main;
import co.mitro.core.server.Manager;
import co.mitro.core.server.ManagerFactory;
import co.mitro.core.server.data.DBAudit;
import co.mitro.core.server.data.DBAudit.ACTION;
import co.mitro.core.server.data.DBGroup;
import co.mitro.core.server.data.DBProcessedAudit;
import co.mitro.core.server.data.DBServerVisibleSecret;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.SelectArg;

public class AuditLogProcessor {
  private static final Logger logger = LoggerFactory.getLogger(AuditLogProcessor.class);
  public static enum ActionType {
    MITRO_AUTO_LOGIN,
    GET_SECRET_NON_CRITICAL_DATA,
    GET_SECRET_CRITICAL_DATA_FOR_LOGIN,
    GET_SECRET_CRITICAL_DATA_FOR_EDIT,
    EDIT_PASSWORD,
    EDIT_SECRET,
    CREATE_GROUP,
    EDIT_GROUP,
    DELETE_GROUP,
    CREATE_SECRET,
    DELETE_SECRET,
    EDIT_SECRET_ACL,
    INVITE_USER,
    INVITED_BY_USER,
    SIGNUP,
    NEW_DEVICE,
    ORG_APPLY_SYNC,
    ORG_VIEW_SYNC,
    ORG_MUTATE,
    // for operations that don't affect the audit log, like pings or refreshes
    NOOP, 
    UNKNOWN, 
    MITRO_LOGIN,
    GRANTED_ACCESS_TO,
    REVOKED_ACCESS_TO,
    DELETE_IDENTITY,
  };
  
  /**
   * creates and inserts into the DB processed audit logs for a specific transaction ids.
   * 
   * Additionally, this enqueues alerts to be sent out in the future.
   * 
   * NB: This function commits the transaction in the manager that is provided.
   * 
   * @return the number of rows we added to the processed audit log table.
   */
  public static final int putActionsForTransactionId(Manager manager, String transactionId) throws SQLException {
    Collection<DBProcessedAudit> actions = getActionsForTransactionId(manager, transactionId);
    for (DBProcessedAudit pa : actions) {
      manager.processedAuditDao.create(pa);
    }
    // some processed audit logs are added directly in the transaction.
    actions = manager.processedAuditDao.queryForEq(DBProcessedAudit.TRANSACTION_ID_FIELD_NAME, new SelectArg(transactionId));
    long minTimestampMs = Long.MAX_VALUE;
    for (DBProcessedAudit action : actions) {
      minTimestampMs = Math.min(action.getTimestampMs(), minTimestampMs);
    }
    EmailAlertManager.getInstance().createFutureAlertsFromAudits(manager, actions, minTimestampMs);
    manager.commitTransaction();
    
    return actions.size();
  }
  
  private static final Map<String, ActionType> OP_NAME_TO_ACTION_TYPE = 
      ImmutableMap.<String, AuditLogProcessor.ActionType>builder()
          .put("VERIFY_DEVICE", ActionType.NEW_DEVICE)
          .put("addGroup", ActionType.CREATE_GROUP)
          .put("addSecret", ActionType.CREATE_SECRET)
          .put("addSite", ActionType.CREATE_SECRET)
          .put("applyPendingGroups", ActionType.ORG_APPLY_SYNC)
          .put("checkTwoFactor", ActionType.UNKNOWN)
          .put("deleteSecret", ActionType.DELETE_SECRET)
          .put("getAuditLog", ActionType.NOOP)
          .put("getGroup", ActionType.NOOP)
          .put("getPendingGroups", ActionType.ORG_VIEW_SYNC)
          .put("mutateGroup", ActionType.EDIT_GROUP)
          .put("mutateOrganization", ActionType.ORG_MUTATE)
          .put("mutatePrivateKeyPassword", ActionType.EDIT_PASSWORD)
          .put("mutateSecret", ActionType.EDIT_SECRET)
          .put("mutateSite", ActionType.EDIT_SECRET)
          .put("editSitePassword", ActionType.EDIT_SECRET)
          .put("removeGroup", ActionType.DELETE_GROUP)
          .put("shareSite", ActionType.EDIT_SECRET_ACL)
          .put("shareSiteAndOptionallySetOrg", ActionType.EDIT_SECRET_ACL)
          .build();

  private static final Set<ActionType> TRACK_SECRETS = Sets.immutableEnumSet(ActionType.CREATE_SECRET, 
      ActionType.DELETE_SECRET, ActionType.EDIT_SECRET, ActionType.EDIT_SECRET_ACL, 
      ActionType.GET_SECRET_CRITICAL_DATA_FOR_LOGIN);
  private static final Set<ActionType> TRACK_GROUPS = Sets.immutableEnumSet(ActionType.CREATE_GROUP, 
      ActionType.DELETE_GROUP, ActionType.EDIT_GROUP);
  
  public static Collection<DBProcessedAudit> getActionsForTransactionId(Manager manager, String transactionId) throws SQLException {
    Set<DBAudit.ACTION> actions = Sets.newHashSet();
    List<DBAudit> matchingAuditLogs = manager.auditDao.queryForEq(DBAudit.TRANSACTION_ID_FIELD_NAME, new SelectArg(transactionId));
    String operationName = null;
    
    List<DBProcessedAudit> rval = Lists.newArrayList();
    List<DBProcessedAudit> invites = Lists.newArrayList();
    
    // these need to be maps and not sets because equals() and hashCode() aren't
    // properly implemented in these db objects.
    Map<Integer, DBGroup> affectedGroups = Maps.newHashMap();
    Map<Integer, DBServerVisibleSecret> affectedSecrets = Maps.newHashMap();
    Map<Integer, DBAudit> actionTargets = Maps.newHashMap();
    
    // First look through the audit logs that match the txn id, and figure
    // out if we've invited any users. If so, we make special events for them.
    for (DBAudit audit : matchingAuditLogs) {
      if (audit.getUser() == null && !DBAudit.TRANSACTION_ACTIONS.contains(audit.getAction())) {
        continue;
      }
      if (audit.getTargetGroup() != null) {
        affectedGroups.put(audit.getTargetGroup().getId(), audit.getTargetGroup());
      }
      if (audit.getTargetSVS() != null) {
        affectedSecrets.put(audit.getTargetSVS().getId(), audit.getTargetSVS());
      }
      
      actions.add(audit.getAction());
      operationName = (operationName == null) ? audit.getOperationName() : operationName;
      if (ACTION.INVITE_NEW_USER == audit.getAction()) {
        invites.add(new DBProcessedAudit(ActionType.INVITE_USER, audit));
        invites.add(new DBProcessedAudit(ActionType.INVITED_BY_USER, audit));
        // we don't care about the new user's private group
        audit.setTargetGroup(null);
        if (null != audit.getTargetUser()) {
          actionTargets.put(audit.getId(), audit);
        }
      }
    }
    
    // has this transaction been cancelled or rolled back? If so, don't add any events.
    if (!Sets.intersection(actions, DBAudit.UNCOMMITTED_TRANSACTIONS).isEmpty() ||
        !actions.contains(DBAudit.ACTION.TRANSACTION_COMMIT)) {
      return Collections.emptyList();
    }
    
    ActionType actionType = null;
    if (!Strings.isNullOrEmpty(operationName)) {
      // operation name is present in the log. This is pretty easy.
       actionType = OP_NAME_TO_ACTION_TYPE.get(operationName);
      if (actionType != null && actionType != ActionType.UNKNOWN && actionType != ActionType.NOOP) {
        if (actionTargets.isEmpty()) {
          // if we don't have any action targets, it doesn't matter which audit object we use to create
          // the processed audit.
          addFromMatchingAudits(actionType, matchingAuditLogs, rval);
        } else {
          // here, we have information about action targets. We must add a processed audit record for each.
          for (DBAudit audit : actionTargets.values())
            rval.add(new DBProcessedAudit(actionType, audit));
        }
      }
    } else {
      // no operation name, thus we must infer what happened in this transaction.
      if (actions.contains(DBAudit.ACTION.CREATE_IDENTITY)) {
        actionType = ActionType.SIGNUP;
      } else if (actions.contains(DBAudit.ACTION.GET_SECRET_WITH_CRITICAL)
          && actions.size() == 2) {
        // there are a bunch of different txns that could have GET_SECRET_WITH_CRITICAL
        // the ones that have only that and commit txn are for logins. 
        actionType = ActionType.GET_SECRET_CRITICAL_DATA_FOR_LOGIN;
      } else if (actions.contains(DBAudit.ACTION.GET_PRIVATE_KEY)) {
        actionType = ActionType.MITRO_LOGIN;
      }
      addFromMatchingAudits(actionType, matchingAuditLogs, rval);
    }
    
    // some kinds of transactions affect at most one group.
    if (TRACK_GROUPS.contains(actionType)) {
      if (affectedGroups.size() > 1) {
        logger.warn("transaction {} has more than one affected group. Ignoring groups for now...", transactionId);
      } else if (!affectedGroups.isEmpty()) {
        final DBGroup g = affectedGroups.values().iterator().next();
        for (DBProcessedAudit a : rval) {
          a.setAffectedGroup(g);
        }
      }
    } 
    
    // some kinds of transactions affect at most one secret.
    if (TRACK_SECRETS.contains(actionType)) {
      if (affectedSecrets.size() > 1) {
        logger.warn("transaction {} has more than one affected secret. Ignoring secrets for now...", transactionId);
      } else if (!affectedSecrets.isEmpty()) {
        final DBServerVisibleSecret s = affectedSecrets.values().iterator().next();
        for (DBProcessedAudit a : rval) {
          a.setAffectedSecret(s);
        }
      }
    }
    
    rval.addAll(invites);
    return rval;
  }

  private static void addFromMatchingAudits(ActionType actionType,
      List<DBAudit> matchingAuditLogs, List<DBProcessedAudit> rval) {
    // if we've discovered what kind of action this is, we should add it.
    if (actionType != null) {
      for (DBAudit audit : matchingAuditLogs) {
        // some old crappy logs don't set the user properly on transaction close properties
        if (audit.getUser() != null) {
          rval.add(new DBProcessedAudit(actionType, audit));
          break;
        }
      }
    }
  }

  /**
   * Tries to create processed audit logs for any audit records that are missing 
   * processed logs. This could take a while...
   */
  public static void main(String[] args) throws SQLException {
    Main.exitIfAssertionsDisabled();
    Set<String> transactionsToProcess = Sets.newHashSet();
    try (Manager mgr = ManagerFactory.getInstance().newManager()) {
      mgr.disableAuditLogs();
      if (args.length == 0) { // find all transactions
        // this crazy string is necessary because postgres 9.1 does not properly optimize NOT IN queries
        String QUERY = "SELECT DISTINCT transaction_id FROM audit WHERE audit.action = 'INVITE_NEW_USER'";
        List<String[]> inviteResults = Lists.newArrayList(mgr.processedAuditDao.queryRaw(QUERY));
        for (String[] row : inviteResults) {
          String tid = row[0];
          if (Strings.isNullOrEmpty(tid)) {
            continue;
          }
          transactionsToProcess.add(tid);
        }
      } else { // use specified transaction ids.
        for (int i = 0; i < args.length; ++i) {
          transactionsToProcess.add(args[i]);
        }
      }
      
      
      if (true) {
        // ONLY FOR RE-CREATING ALL LOGS. THIS IS DANGEROUS
        for (String tid : transactionsToProcess) {
          System.out.println("deleting " + tid);
          DeleteBuilder<DBProcessedAudit, Integer> deleter = mgr.processedAuditDao.deleteBuilder();
          deleter.where().eq("transaction_id", tid);
          deleter.delete();
        }
      }
      /////
      
      
      
      logger.info("we must process logs for {} transactions.", transactionsToProcess.size());
      for (String tid : transactionsToProcess) {
        

        int count = putActionsForTransactionId(mgr, tid);        
        mgr.commitTransaction();
        logger.info("transaction {} -> {} events.", tid, count);
      }
    }
  }
}

