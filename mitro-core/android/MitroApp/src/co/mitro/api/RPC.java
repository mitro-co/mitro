package co.mitro.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import co.mitro.core.exceptions.DoTwoFactorAuthException;
import co.mitro.core.exceptions.SendableException;
import co.mitro.recordio.JsonRecordWriter;

import com.google.common.collect.Lists;

public final class RPC {
  public static class LoginToken {
    public String email;
    public long timestampMs;
    public String nonce;
    public boolean twoFactorAuthVerified = false;
    public String extensionId;
  }

  public static class MitroException {
    private static SecureRandom randomNumberGenerator = new SecureRandom();

    private MitroException() {
      exceptionId = new BigInteger(32, randomNumberGenerator).toString(Character.MAX_RADIX);
    }

    public static MitroException createFromException(Exception e) {
      RPC.MitroException out = new RPC.MitroException();

      // DO NOT REMOVE! old versions of extensions expect reasons to have size
      // == 1.
      out.reasons = Lists.newLinkedList();
      out.reasons.add("");

      out.exceptionType = (e instanceof SendableException) ? e.getClass().getSimpleName()
          : "Exception";

      out.userVisibleError = (e instanceof SendableException) ? ((SendableException) e)
          .getUserVisibleMessage() : "Error";
      out.userVisibleError += " (" + out.exceptionId + ")";
      out.rawMessage = (e instanceof DoTwoFactorAuthException) ? ((DoTwoFactorAuthException) (e))
          .getUserVisibleMessage() : null;
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      out.stackTraceString = null;

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

    /** Optional transaction id for this request. */
    public String transactionId;

    /**
     * Serialized JSON object containing the actual request. A string is easy to
     * sign/verify.
     */
    public String request;

    /**
     * Request signed by identity's private key. Proves that the entity making
     * the request has access to the private key.
     * 
     * NOTE: There is no replay protection or confidentiality. Intended to be
     * sent over HTTPS.
     */
    public String signature;

    /** User making the request. */
    public String identity;

    /** client string */
    public String clientIdentifier = "";
  }

  public static class MitroRPC {
    public String transactionId = null;
    public String deviceId = null;
  };

  public static class GetPendingGroupApprovalsRequest extends MitroRPC {
    public String scope;
  };

  public static class GetPendingGroupApprovalsResponse extends MitroRPC {
    public static class PendingGroupApproval extends AddPendingGroupRequest.PendingGroup {
      public EditGroupRequest matchedGroup;
      public List<String> delegatedAdminUsers;
      public int id;
    };

    public List<PendingGroupApproval> pendingAdditionsAndModifications;
    public List<EditGroupRequest> pendingDeletions;
  };

  public static class AddPendingGroupRequest extends MitroRPC {

    public static class MemberList {
      public List<String> memberList;
      public String groupName; // The client should verify that this matches the
                               // group name outside
    }

    public static class PendingGroup {
      public String groupName, scope, memberListJson, signature;
      public int delegatedAdminGroupId;
    }

    public PendingGroup pendingGroup;
  };

  public static class AddPendingGroupResponse extends MitroRPC {
    ;
  };

  public static class BeginTransactionRequest extends MitroRPC {
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
  };

  public static class GetMyPrivateKeyResponse extends MitroRPC {
    public String myUserId;
    public String encryptedPrivateKey;
    public boolean changePasswordOnNextLogin = false;
    public boolean verified;

    // use this for subsequent requests:
    public String unsignedLoginToken;
    public String deviceKeyString;
  };

  public static class CheckTwoFactorRequiredRequest extends MitroRPC {
    public String extensionId;
  }

  public static class CheckTwoFactorRequiredResponse extends MitroRPC {
    public String twoFactorUrl;
  }

  public static class GetMyDeviceKeyRequest extends MitroRPC {
    // device ID & user id included in MitroRPC
  }

  public static class GetMyDeviceKeyResponse extends MitroRPC {
    /**
     * JSON-encoded AES key used for encrypting things on the device's local
     * disk.
     */
    public String deviceKeyString;
  }

  public static class GetPublicKeysForIdentityRequest extends MitroRPC {
    public List<String> userIds;
    public boolean addMissingUsers = false;
  };

  public static class GetPublicKeyForIdentityResponse extends MitroRPC {
    public Map<String, String> userIdToPublicKey;
    public List<String> missingUsers;
  };

  public static class ListMySecretsAndGroupKeysRequest extends MitroRPC {
    /**
     * @deprecated the requestor is identified by the outer {@link SignedRequest}
     *             .
     */
    @Deprecated
    public String myUserId;
  };

  public static class Secret {
    public int secretId;
    public String hostname;
    public String encryptedClientData;
    public String encryptedCriticalData;
    public List<Integer> groups;
    public List<Integer> hiddenGroups;
    public List<String> users; // this is automatically generated from
                               // autodelete groups.
    public List<String> icons = Lists.newArrayList(); // list of icons.
    public String title;
  }

  public static class ListMySecretsAndGroupKeysResponse extends MitroRPC {
    public String myUserId;

    public static class SecretToPath extends Secret {
      public List<Integer> groupIdPath;
    }

    public static class GroupInfo {
      public int groupId;
      public boolean autoDelete;
      public String name;
      public String encryptedPrivateKey;
    }

    public Map<Integer, SecretToPath> secretToPath;
    public Map<Integer, GroupInfo> groups;
    public List<String> autocompleteUsers = Lists.newArrayList();
  };

  public static class GetSecretRequest extends MitroRPC {
    /**
     * @deprecated the requestor is identified by the outer {@link SignedRequest}
     *             .
     */
    @Deprecated
    public String userId;
    public int secretId;
    public int groupId;
    public boolean includeCriticalData = false;
  };

  public static class GetSecretResponse extends MitroRPC {
    public GetSecretResponse() {
      secret = new Secret();
    }

    public Secret secret;
  };

  public static class RemoveAllPendingGroupApprovalsForScopeRequest extends MitroRPC {
    public String scope = null;
  }

  public static class AddSecretRequest extends MitroRPC {
    /**
     * @deprecated the requestor is identified by the outer {@link SignedRequest}
     *             .
     */
    @Deprecated
    public String myUserId;
    public Integer secretId = null;
    public int ownerGroupId;
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
    public String userId;
    public String encryptedPrivateKey;
  };

  public static class EditEncryptedPrivateKeyResponse extends MitroRPC {
  };

  public static class TwoFactorAuthRequest extends MitroRPC {
    public String tfaToken; // two factor auth token
    public String tfaSignature; // two factor auth token-signature
  }

  public static class AddIdentityRequest extends EditEncryptedPrivateKeyRequest {
    public String publicKey;
    public String analyticsId = null;
    public String groupKeyEncryptedForMe = null;
    public String groupPublicKey = null;
  };

  public static class AddIdentityResponse extends MitroRPC {
    public String unsignedLoginToken;
    public boolean verified;
    public Integer privateGroupId = null;
  };

  public static class AddGroupRequest extends MitroRPC {
    //
    public String name;
    public String publicKey;
    public String signatureString; // *** TODO: I forgot where this is supposed
                                   // to come from
    public String scope;
    // is this group hidden from the user?
    public boolean autoDelete;

    public static class ACL {
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

  public static class EditGroupRequest extends AddGroupRequest {
    public int groupId;
    public List<Secret> secrets;
  };

  public static class EditGroupResponse extends AddGroupResponse {
  };

  public static class GetGroupRequest {
    public String userId;
    public int groupId;
    public boolean includeCriticalData = false;
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
    public long offset;
    public long limit;
  }

  public static class AuditEvent {
    public int id;
    public int secretId;
    public String sourceIp;
    // public DBAudit.ACTION action;
    public int userId;
    public String username;
    public Long timestampMs;
  }

  public static class GetAuditLogResponse extends MitroRPC {
    public List<AuditEvent> events;
  }

  public static class Logger {
    static private JsonRecordWriter rpcLogger = null;

    public static JsonRecordWriter getLogger() {
      return rpcLogger;
    }

    public static void closeAndSetNewJsonLoggerFilename(String fn) throws IOException {
      if (rpcLogger != null) {
        rpcLogger.close();
      }
      rpcLogger = (null == fn) ? null : JsonRecordWriter.MakeFromFilePrefix(fn);
    }
  }
};