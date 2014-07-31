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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import co.mitro.analysis.AuditLogProcessor;
import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.SendableException;
import co.mitro.core.server.data.DBGroupSecret.CRITICAL;
import co.mitro.core.server.data.RPC.ListMySecretsAndGroupKeysResponse.SecretToPath;
import co.mitro.core.server.data.RPC.MitroException.MitroExceptionReason;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public final class RPC { 
  public static class LoginToken {
    public String email;
    public long timestampMs;
    public String nonce;
    public boolean twoFactorAuthVerified=false;
    public String extensionId;
    public String deviceId;
  }
  public static class MitroException {
    private static SecureRandom randomNumberGenerator = new SecureRandom();
    
    private MitroException() {
      exceptionId = new BigInteger(32, randomNumberGenerator).toString(Character.MAX_RADIX);
    }
    
    public static enum MitroExceptionReason {FOR_USER, FOR_LOG};

    public static MitroException createFromException(Throwable e, MitroExceptionReason exceptionReason) {
      RPC.MitroException out = new RPC.MitroException();
      final boolean forLog = exceptionReason.equals(MitroExceptionReason.FOR_LOG);
      // DO NOT REMOVE! old versions of extensions expect reasons to have size == 1.
      out.reasons = Lists.newLinkedList();
      out.reasons.add("");

      out.exceptionType = (e instanceof SendableException) ? e.getClass().getSimpleName() : "Exception";
      out.userVisibleError = (e instanceof SendableException)
            ? ((SendableException)e).getUserVisibleMessage() : "Error";
      out.userVisibleError += " (" + out.exceptionId + ")";
      out.rawMessage = (e instanceof DoTwoFactorAuthException) ? ((DoTwoFactorAuthException)(e)).getUserVisibleMessage() : null;
      if (forLog) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        out.stackTraceString = sw.toString(); 
      } else {
        out.stackTraceString = null;
      }
      return out;
    }

    public List<String> reasons;

    public String exceptionId;
    public String stackTraceString;
    
    public String rawMessage;
    public String userVisibleError;
    public String exceptionType;
  };

  public static class SignedRequest {
    // TODO: should these be signed?
    public boolean implicitEndTransaction = false;
    public boolean implicitBeginTransaction = false;
    public String operationName = "";
    
    /** Optional transaction id for this request. */
    public String transactionId;

    /** Serialized JSON object containing the actual request. A string is easy to sign/verify. */
    public String request;

    /**
     * Request signed by identity's private key. Proves that the entity making the request
     * has access to the private key.
     *
     * NOTE: There is no replay protection or confidentiality. Intended to be sent over HTTPS.
     */
    public String signature;

    /** User making the request. */
    public String identity;
    
    /** client string */
    public String clientIdentifier="";
    
    // platform, as specified by the client
    public String platform; 
  }

  public static class MitroRPC {
    public String transactionId=null;
    public String deviceId=null;
  };
  
  public static class GetPendingGroupApprovalsRequest extends MitroRPC {
    public String scope;
  };

  public static class GetPendingGroupApprovalsResponse extends MitroRPC {
    public static class PendingGroupApproval extends AddPendingGroupRequest.PendingGroup {
      public EditGroupRequest matchedGroup;
      public String scope;
    };
    public List<PendingGroupApproval> pendingAdditionsAndModifications;
    public List<EditGroupRequest> pendingDeletions;
    
    public String syncNonce;
    public Map<String, GroupDiff> diffs;
    public int orgId;
    
    public List<String> newOrgMembers;
    
    /** This lists the users who should be removed from the org.
     * NB: if ALL org users are not in at least one synced group, this will 
     * list all org users who are not in any synced groups, which could 
     * be a much larger set that what you expect.
     */
    public List<String> deletedOrgMembers;
  };
  
  public static class GroupDiff {
    public static enum GroupModificationType {
      IS_NEW,
      IS_DELETED, 
      MEMBERSHIP_MODIFIED,
      IS_UNCHANGED
    };

    public String groupName;
    public List<String> newUsers = Lists.newArrayList();
    public List<String> deletedUsers = Lists.newArrayList();
    public GroupModificationType groupModification = GroupModificationType.IS_UNCHANGED;
    
    public boolean isDifferent() {
      return (!groupModification.equals(GroupModificationType.IS_UNCHANGED));

    }
  }
  
  public static class AddPendingGroupRequest extends MitroRPC {
    public static class MemberList {
      public List<String> memberList;
      public String groupName; // The client should verify that this matches the group name outside
    }

    public static class PendingGroup {
      public String groupName, memberListJson, signature;
    }

    /** Unique id for the upstream source of these users and groups. */
    public String scope;
    public List<PendingGroup> pendingGroups;

    public static class AdminInfo {
      public String domainAdminEmail;
      // TODO: this is for future use.
      // public Integer orgId;
      // public String mitroOrgSignature;
    }
    public AdminInfo adminInfo;
    
    public static class SyncedUserInfo {
      public String name;
      public String photoUrl;
    }
    
    /** this will be used for displays in org state etc. */
    public Map<String, SyncedUserInfo> emailToUserInfo;
    
  };
  
  public static class AddPendingGroupResponse extends MitroRPC {
    /** This is used only for tests */
    transient public Map<String, GroupDiff> diffs;
    public String syncNonce;
  };
  
  public static class BeginTransactionRequest extends MitroRPC {
    public String operationName;
  };

  public static class BeginTransactionResponse extends MitroRPC {
  };
  
  public static class EndTransactionRequest extends MitroRPC {
  };

  public static class EndTransactionResponse extends MitroRPC {
  };
  
  public static class GetMyPrivateKeyRequest extends MitroRPC {
    public String userId;
    
    // TODO: if this is not provided, we should require 
    // a two factor auth or email verification
    public String loginToken;
    public String loginTokenSignature;
    public String extensionId;
    public String twoFactorCode;
    public boolean automatic = false;
  };
  
  public static class GetMyPrivateKeyResponse extends MitroRPC {
    public String myUserId;
    public String encryptedPrivateKey;
    public boolean changePasswordOnNextLogin=false;
    public boolean verified;
    
    // use this for subsequent requests: 
    public String unsignedLoginToken;
    public String deviceKeyString;
  };
  
  public static class CheckTwoFactorRequiredRequest extends MitroRPC{
    public String extensionId;
  }
  
  public static class CheckTwoFactorRequiredResponse extends MitroRPC{
    public String twoFactorUrl;
  }
  public static class GetMyDeviceKeyRequest extends MitroRPC {
    // device ID & user id included in MitroRPC
  }
  
  public static class GetMyDeviceKeyResponse extends MitroRPC {
    /** JSON-encoded AES key used for encrypting things on the device's local disk.*/
    public String deviceKeyString;
  }
  
  public static class GetPublicKeysForIdentityRequest extends MitroRPC {
    public List<String> userIds;
    public List<Integer> groupIds;
    public boolean addMissingUsers = false;
  };
  
  public static class GetPublicKeyForIdentityResponse extends MitroRPC {
    public Map<String,String> userIdToPublicKey = Maps.newHashMap();
    public Map<Integer,String> groupIdToPublicKey = Maps.newHashMap();
    public List<String> missingUsers;
  };
  
  public static class GetOrganizationStateRequest extends MitroRPC {
    public int orgId;
  }
  
  public static class AuditAction {
    public String userName;
    public int timestampSec;
  }
  
  public static class GetOrganizationStateResponse extends MitroRPC {
    public List<String> members = Lists.newArrayList();
    
    // this should only have this org.
    public Map<Integer, ListMySecretsAndGroupKeysResponse.GroupInfo> organizations = Maps.newHashMap();

    public Map<Integer, ListMySecretsAndGroupKeysResponse.GroupInfo> groups = Maps.newHashMap();

    public ArrayList<String> admins = Lists.newArrayList();
    
    public Map<Integer, SecretToPath> orgSecretsToPath = Maps.newHashMap();
    public Map<Integer, SecretToPath> orphanedSecretsToPath = Maps.newHashMap();
  }
  
  public static class ListMySecretsAndGroupKeysRequest extends MitroRPC {
    /** @deprecated the requestor is identified by the outer {@link SignedRequest}. */
    @Deprecated
    public String myUserId;
  };
  
  public static class Secret {
    public int secretId;
    // TODO: Remove this completely after clients are updated
    @Deprecated
    public String hostname;
    public String encryptedClientData;
    public String encryptedCriticalData;
    public List<Integer> groups;
    public List<Integer> hiddenGroups;
    
    public List<String> users; // this is automatically generated from autodelete groups.
    public List<String> icons = Lists.newArrayList(); // list of icons.
    /** Maps group ids to group names to display them in the ACL. */

    // groupNames is deprecated.  Use groupMap instead.
    public Map<Integer, String> groupNames;
    // TODO: Phase out groups, use this instead?
    public Map<Integer, ListMySecretsAndGroupKeysResponse.GroupInfo> groupMap;
    public String title;
    
    /** Group ID of the owning organization. This is immutable. */
    public Integer owningOrgId;
    public String owningOrgName;
    
    public AuditAction lastModified;
    public AuditAction lastAccessed;
    public String king;
    public boolean isViewable;
    
    public boolean canEditServerSecret = false;
    public Map<Integer, String> groupIdToPublicKeyMap;
  }
  public static class EditSecretContentRequest extends MitroRPC {
    public int secretId;
    public static final class SecretContent {
      public SecretContent(String clientData, String criticalData) {
        this.encryptedClientData = clientData;
        this.encryptedCriticalData = criticalData;
      }
      public String encryptedClientData;
      public String encryptedCriticalData;
    }
    public Map<Integer, SecretContent> groupIdToEncryptedData;
    @Deprecated
    public String hostname;
  }
  
  public static class EditSecretContentResponse extends MitroRPC {
  }
  
  
  public static class UpdateSecretFromAgentRequest extends MitroRPC {
    public static class UserData {
      public static class CriticalData {
        public String password;
        public String note;
        public String oldPassword;
        
        @Override
        public boolean equals(Object obj) {
          CriticalData right = (CriticalData)obj;
          return (Objects.equal(right.password, password) 
              && Objects.equal(right.note, note) 
              && Objects.equal(right.oldPassword, oldPassword));
        }
      }
      public int secretId;
      public String userId;
      public CriticalData criticalData;
    }
    public String dataFromUser; // UserData type.
    public String dataFromUserSignature;
  }
  
  public static class ListMySecretsAndGroupKeysResponse extends MitroRPC {
    public String myUserId;
    
    public static class SecretToPath extends Secret {
      public List<Integer> groupIdPath;
      // these are used internally when building this data structure 
      transient public Set<Integer> hiddenGroupsSet = Sets.newHashSet();
      transient public Set<Integer> visibleGroupsSet = Sets.newHashSet();
    }

    public static class GroupInfo {
      public int groupId;
      public boolean autoDelete;
      public String name;
      public String encryptedPrivateKey;
      public boolean isOrgPrivateGroup;
      public boolean isNonOrgPrivateGroup;
      public Integer owningOrgId;
      public String owningOrgName;
      
      public List<String> users;
      
      public boolean isTopLevelOrg;
      public boolean isRequestorAnOrgAdmin = false;
    }
    
    public Map<Integer, GroupInfo> organizations;
    public Map<Integer, SecretToPath> secretToPath;
    public Map<Integer, GroupInfo> groups;
    
    public List<String> autocompleteUsers=Lists.newArrayList();
    transient public Set<String> autocompleteUsersSet = Sets.newHashSet();
  };
  

  public static class GetSecretRequest  extends MitroRPC {
    /** @deprecated the requestor is identified by the outer {@link SignedRequest}. */
    @Deprecated
    public String userId;
    public int secretId;
    public Integer groupId;
    public String includeCriticalData = CRITICAL.NO_CRITICAL_DATA.getClientString();
  };
  
  public static class GetSecretResponse extends MitroRPC {
    public GetSecretResponse() {
      secret = new Secret();
    }
    
    public Secret secret;
    public String encryptedGroupKey = null;
    public int groupId;
  };
  

  public static class RemoveAllPendingGroupApprovalsForScopeRequest extends MitroRPC {
    public String scope=null;
  }
  public static class AddSecretRequest extends MitroRPC {
    /** @deprecated the requestor is identified by the outer {@link SignedRequest}. */
    @Deprecated
    public String myUserId;
    public Integer secretId = null;
    public int ownerGroupId;
    /** @deprecated remove once the clients stop sending it */
    @Deprecated
    public String hostname;
    public String encryptedClientData;
    public String encryptedCriticalData;
  };
  
  public static class AddSecretResponse extends MitroRPC {
    public int secretId;
  };
  
  public static class RemoveSecretRequest extends MitroRPC {
    @Deprecated
    public String myUserId;
    
    // If provided, remove the secret from only this group
    public Integer groupId = null;
    
    public int secretId;
  };
  
  public static class RemoveSecretResponse extends MitroRPC {
  };
  
  public static class EditEncryptedPrivateKeyRequest extends TwoFactorAuthRequest {
    @Deprecated
    public String userId;
    public String encryptedPrivateKey;
  };
  public static class EditEncryptedPrivateKeyResponse extends MitroRPC {
  };
  
  public static class TwoFactorAuthRequest extends MitroRPC{
    /** Two-factor authentication token. */
    public String tfaToken;

    /** Signature on tfaToken. */
    public String tfaSignature;
  }

  public static class AddIdentityRequest extends MitroRPC {
    public String userId;
    public String encryptedPrivateKey;
    public String publicKey;
    public String analyticsId = null;
    public String groupKeyEncryptedForMe = null;
    public String groupPublicKey = null;
  };
  
  public static class AddIdentityResponse extends MitroRPC {
    public String unsignedLoginToken;
    public boolean verified;
    public Integer privateGroupId=null;
  };
  
  public static class AddGroupRequest extends MitroRPC {
    // 
    public String name;
    public String publicKey;
    public String signatureString; //*** TODO: I forgot where this is supposed to come from
    public String scope;
    // is this group hidden from the user?
    public boolean autoDelete;
    
    public static class ACL {
      public DBAcl.AccessLevelType level;
      public String groupKeyEncryptedForMe;
      public String myPublicKey;
      public Integer memberGroup;
      public String memberIdentity;
    }
    
    public List<ACL> acls;
    
  };
  public static class AddGroupResponse extends MitroRPC {
    public int groupId;
  };
  
  public static class DeleteGroupRequest extends MitroRPC {
    public int groupId;
  };
  
  public static class DeleteGroupResponse extends MitroRPC {
    public int groupId;
  };

  
  public static class EditSecretRequest extends MitroRPC {
    public int secretId;
    public boolean isViewable = true;
    @Deprecated
    public String hostname;
  };
  
  public static class EditSecretResponse extends MitroRPC {
  };
  
  public static class EditGroupRequest extends AddGroupRequest {
    public int groupId;
    /** If not null, contains the re-encrypted secrets. If null, the secrets are unchanged. */
    public List<Secret> secrets;
  };

  public static class GetGroupRequest {
    /** @deprecated the requestor is identified by the outer {@link SignedRequest}. */
    @Deprecated
    public String userId;
    public int groupId;
    public boolean includeCriticalData=false; 
  }
  public static class GetGroupResponse extends EditGroupRequest {
  };
  
  public static class AddIssueRequest extends MitroRPC {
    public String url;
    public String type;
    public String description;
    public String email;
    public String logs;
  }
  
  public static class AddIssueResponse extends MitroRPC {
  }

  public static class InviteNewUserRequest extends MitroRPC {
    public List<String> emailAddresses;
  }

  public static class InviteNewUserResponse extends MitroRPC {
    public Map<String, String> publicKeys;
  }

  public static class GetAuditLogRequest extends MitroRPC {
    public Long offset;
    public Long limit;
    public Integer orgId;
    public Long startTimeMs;
    public Long endTimeMs;
  }
  
  public static class AuditEvent {
    public int id;
    public Integer secretId;
    public Integer groupId;
    public String sourceIp;
    public AuditLogProcessor.ActionType action;
    public int userId;
    public String username;
    public Long timestampMs;
    public Integer affectedUserId;
    public String affectedUsername;
  }
  
  public static class GetAuditLogResponse extends MitroRPC {
      public List<AuditEvent> events;
  }
  
  public static class LogMetadata {
    public long timestamp;
    public String endpoint;
    public String sourceIp;
    public Object response;
    public int responseCode = 0;
    public boolean isRequest;
    public Object rawException;
    public String rawExceptionId;
    
    public LogMetadata(HttpServletRequest request) {
      timestamp = System.currentTimeMillis();
      endpoint = request.getServletPath();
      sourceIp = request.getHeader("X-Real-Ip");
      isRequest = true;
    }
    
    public void setException(Throwable e) {
      MitroException mitroException = MitroException.createFromException(e, MitroExceptionReason.FOR_LOG);
      rawException = mitroException.stackTraceString;
      rawExceptionId = mitroException.exceptionId;
    }
    
    public void setResponse(int responseCode, Object responseObj) {
      this.responseCode = responseCode;
      this.response = responseObj;
    }
  }

  public static class CreateOrganizationRequest extends MitroRPC {
    public String name;
    public String publicKey;

    /** Maps admin identities to the organization private key, encrypted for each user. */
    public Map<String, String> adminEncryptedKeys;

    /** Maps member identities to private group keys, encrypted for (member, organization). */
    public Map<String, PrivateGroupKeys> memberGroupKeys;
    public static class PrivateGroupKeys {
      public String publicKey;
      public String keyEncryptedForUser;
      public String keyEncryptedForOrganization;
    }
  }

  public static class MutateOrganizationRequest extends MitroRPC {
    public int orgId;
    public Map<String, String> promotedMemberEncryptedKeys;
    public Map<String, CreateOrganizationRequest.PrivateGroupKeys> newMemberGroupKeys;
    public List<String> adminsToDemote;
    public List<String> membersToRemove;
  }
  
  public static class MutateOrganizationResponse extends MitroRPC {
    ;
  }
  
  public static class CreateOrganizationResponse extends MitroRPC {
    public int organizationGroupId;
  }

  public static class GetCustomerResponse {
    public String orgName;
    public int numUsers;
    public String planName;
    public int planUnitCost;
  };
  
  
};
