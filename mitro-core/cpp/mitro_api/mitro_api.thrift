namespace cpp mitro_api

struct SignedRequest {
  1:optional string transactionId;
  2:string request;
  3:optional string signature;
  4:string identity;
  5:string clientIdentifier;
}

struct MitroException {
  1:string exceptionId;
  2:string stackTraceString;
  3:string rawMessage;
  4:string userVisibleError;
  5:optional string exceptionType;
}

struct MitroRPC {
  1:string transactionId;
  2:string deviceId;
}

struct GetMyPrivateKeyRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string userId;
  4:string loginToken;
  5:string loginTokenSignature;
  6:string extensionId;
  7:string twoFactorCode;
}

struct GetMyPrivateKeyResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string myUserId;
  4:optional string encryptedPrivateKey;
  5:bool changePasswordOnNextLogin = false;
  6:bool verified;
  7:string unsignedLoginToken;
  8:optional string deviceKeyString;
}

struct CheckTwoFactorRequiredRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string extensionId;
}

struct CheckTwoFactorRequiredResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string twoFactorUrl;
}

typedef MitroRPC GetMyDeviceKeyRequest

struct GetMyDeviceKeyResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string deviceKeyString;
}

struct SecretClientData {
  1: optional string type;
  2: optional string loginUrl
  3: optional string username;
  4: optional string usernameField;
  5: optional string passwordField;
  6: optional string title;
}

struct SecretCriticalData {
  1: optional string password;
  2: optional string note;
}

struct Secret {
  1: optional i32 secretId;
  2: optional string hostname;
  3: optional string encryptedClientData;
  4: optional string encryptedCriticalData;
  5: optional list<i32> groups;
  6: optional list<i32> hiddenGroups;
  7: optional list<string> users;
  8: optional list<string> icons;
  9: optional map<string, string> groupNames;
  10: optional string title;
  11: optional list<i32> groupIdPath;
  12: optional SecretClientData clientData;
  13: optional SecretCriticalData criticalData;
}

struct GroupInfo {
  1:i32 groupId;
  2:bool autoDelete;
  3:string name;
  4:string encryptedPrivateKey;
}

struct ListMySecretsAndGroupKeysRequest {
  1:string transactionId;
  2:string deviceId;
  3:optional string myUserId;
}

struct ListMySecretsAndGroupKeysResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string myUserId;
  4:map<string, Secret> secretToPath;
  5:map<string, GroupInfo> groups;
  6:list<string> autocompleteUsers;
}

struct GetSecretRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string userId;
  4:i32 secretId;
  5:i32 groupId;
  6:bool includeCriticalData = false;
}

struct GetSecretResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:Secret secret;
}

struct AddSecretRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string myUserId;
  4:optional i32 secretId;
  5:i32 ownerGroupId;
  6:string hostname;
  7:string encryptedClientData;
  8:string encryptedCriticalData;
}

struct AddSecretResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:i32 secretId;
}

struct RemoveSecretRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string myUserId;
  4:optional i32 groupId;
  5:i32 secretId;
}

typedef MitroRPC RemoveSecretResponse

struct EditEncryptedPrivateKeyRequest /* extends MitroRPC {
  1:string transactionId;
  2:string deviceId;
  3:string tfaToken;
  4:string tfaSignature;
  5:string userId;
  6:string encryptedPrivateKey;
}

typedef MitroRPC EditEncryptedPrivateKeyResponse

struct AddIdentityRequest /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string userId;
  4:string encryptedPrivateKey;
  5:string publicKey;
  6:string analyticsId;
  7:string groupKeyEncryptedForMe;
  8:string groupPublicKey;
}

struct AddIdentityResponse /* extends MitroRPC */ {
  1:string transactionId;
  2:string deviceId;
  3:string unsignedLoginToken;
  4:bool verified;
  5:optional i32 privateGroupId;
}
