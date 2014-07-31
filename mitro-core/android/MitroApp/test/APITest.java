import junit.framework.TestCase;


public class APITest extends TestCase {
/*	private static final Gson gson = new Gson();

	private PrivateKeyInterface key = KeyInterfacesTest.loadTestKey();
	MitroApi user;
	FakePOSTRequestSender fakePoster;
	RPC.GetMyPrivateKeyResponse response;


	
	protected void setUp() throws Exception {
		fakePoster = new FakePOSTRequestSender();
		PrivateKeyInterface key = KeyInterfacesTest.loadTestKey();
		response = new RPC.GetMyPrivateKeyResponse();
		response.encryptedPrivateKey = key.exportEncrypted("fakepass");
		fakePoster.nextResponse = gson.toJson(response);
		user = new MitroApi(fakePoster);
	}

	
	public void testLogin() throws CryptoError, IOException {
		//user.login("fakeuser", "fakepass", false, null);
		
		//Checks if password is correct
		//boolean result = user.login("fakeuser", "fakepass");
		//assertTrue(result);
		//Checks that wrong password is indeed wrong
		//result = user.login("fakeuser","wrongpassword");
		//assertFalse(result);
		
		RPC.SignedRequest signedRequest = gson.fromJson(fakePoster.lastRequest, RPC.SignedRequest.class);
		assertEquals("fakeuser", signedRequest.identity);
		
		//Checks if key is the cryptoKey is being set properly
		assertEquals(user.decryptCryptoKey(response.encryptedPrivateKey,"fakepass").toString(),user.getKey().toString());	
	
	}
	
	public void testgetSecretIdentiferData() throws CryptoError, IOException{
		//user.login("fakeuser","fakepass", false null);

		RPC.ListMySecretsAndGroupKeysResponse secrets_json = new RPC.ListMySecretsAndGroupKeysResponse();		
		RPC.ListMySecretsAndGroupKeysResponse.SecretToPath secretToPath = new RPC.ListMySecretsAndGroupKeysResponse.SecretToPath();
		RPC.ListMySecretsAndGroupKeysResponse.GroupInfo groupInfoDefinition = new RPC.ListMySecretsAndGroupKeysResponse.GroupInfo();
		RPC.Secret secret = new RPC.Secret();
		
		Map<Integer,ListMySecretsAndGroupKeysResponse.SecretToPath> secretPath = new HashMap<Integer,ListMySecretsAndGroupKeysResponse.SecretToPath>();
		Map<Integer,ListMySecretsAndGroupKeysResponse.GroupInfo> groupInfo = new HashMap<Integer,ListMySecretsAndGroupKeysResponse.GroupInfo>();
		
		//Sets up the RPC.ListMySecretsAndGroupKeysResponse
		secrets_json.myUserId="fakeuser";
		secrets_json.transactionId ="fake_transaction";
		
		//Sets up the secret before encryption
		secret.secretId=666;
		secret.title="fake_title";
		secret.encryptedClientData = user.getKey().encrypt("This secret is fake.");
		
		//Sets up the secretToPath
		secretToPath.title = "fake_title";
		secretToPath.encryptedClientData = user.getKey().encrypt(gson.toJson(secret));
		secretToPath.secretId = 666;		
		secrets_json.secretToPath = secretPath;
		secrets_json.secretToPath.put(77, secretToPath);
		
		//Sets up secretToPath groupIdPath
		secretToPath.groupIdPath = new ArrayList<Integer>();
		groupInfoDefinition.groupId = 1;
		groupInfoDefinition.encryptedPrivateKey = key.encrypt(key.toString());
		secrets_json.groups = groupInfo;
		secrets_json.groups.put(1,groupInfoDefinition);
		secretToPath.groupIdPath.add(1);

		// add a second secret
		secret = new RPC.Secret();
		secret.secretId=667;
		secret.title="title2";
		secret.encryptedClientData = user.getKey().encrypt("This secret is fake.");
		
		//Sets up the secretToPath
		secretToPath = new RPC.ListMySecretsAndGroupKeysResponse.SecretToPath();
		secretToPath.title = "secretToPath title";
		secretToPath.encryptedClientData = user.getKey().encrypt(gson.toJson(secret));
		secretToPath.secretId = 667;
		secretToPath.groupIdPath = Lists.newArrayList(1);
		secrets_json.secretToPath.put(78, secretToPath);

		//Sets the next response to be the RPC.ListMySecretsAndGroupKeysResponse object
		fakePoster.nextResponse = gson.toJson(secrets_json);

		
		//Gets the secretIdentifierData
		ArrayList<SecretIdentifier> test_secrets = user.getSecretIdentiferData();
		MitroApi.SecretIdentifier returned_secret = test_secrets.get(0);
		MitroApi.ClientDataStruct returned_clientDataStruct = returned_secret.secretData;
		
		//Asserts that everything being returned is as it was before it was sent
		assertEquals(2, test_secrets.size());
		assertEquals("fake_title", test_secrets.get(0).title);
		assertEquals("title2", test_secrets.get(1).title);
		assertEquals(666,returned_secret.id);
		assertEquals(1,returned_secret.groupid);
		assertEquals("fake_title",returned_clientDataStruct.title);
		assertEquals("manual",returned_clientDataStruct.type);		
		assertEquals(666,returned_secret.id);		
	}
	
	public void testgetCriticalDataContents() throws CryptoError, IOException{
		//user.login("fakeuser","fakepass", false, null);
*/
		/*
		 * The following code(from the previous test) is run as to make sure certain variables in the api get set. A
		 * user of the api would need to get all the secrets for a user to get have the knowledge to get the critical data so this test
		 * remains consistent with real-life usage of the api. 
		 */
	/*
		RPC.ListMySecretsAndGroupKeysResponse secrets_json = new RPC.ListMySecretsAndGroupKeysResponse();		
		RPC.ListMySecretsAndGroupKeysResponse.SecretToPath secretToPath = new RPC.ListMySecretsAndGroupKeysResponse.SecretToPath();
		RPC.ListMySecretsAndGroupKeysResponse.GroupInfo groupInfoDefinition = new RPC.ListMySecretsAndGroupKeysResponse.GroupInfo();
		RPC.Secret secret = new RPC.Secret();
		
		Map<Integer,ListMySecretsAndGroupKeysResponse.SecretToPath> secretPath = new HashMap<Integer,ListMySecretsAndGroupKeysResponse.SecretToPath>();
		Map<Integer,ListMySecretsAndGroupKeysResponse.GroupInfo> groupInfo = new HashMap<Integer,ListMySecretsAndGroupKeysResponse.GroupInfo>();
		
		//Sets up the RPC.ListMySecretsAndGroupKeysResponse
		secrets_json.myUserId="fakeuser";
		secrets_json.transactionId ="fake_transaction";
		
		//Sets up the secret before encryption
		secret.secretId=666;
		secret.title="fake_title";
		secret.encryptedClientData = user.getKey().encrypt("This secret is fake.");
		
		//Sets up the secretToPath
		secretToPath.title = "fake_title";
		secretToPath.encryptedClientData = user.getKey().encrypt(gson.toJson(secret));
		secretToPath.secretId = 666;		
		secrets_json.secretToPath = secretPath;
		secrets_json.secretToPath.put(77, secretToPath);
		
		//Sets up secretToPath groupIdPath
		secretToPath.groupIdPath = new ArrayList<Integer>();
		groupInfoDefinition.groupId = 1;
		groupInfoDefinition.encryptedPrivateKey = key.encrypt(key.toString());
		secrets_json.groups = groupInfo;
		secrets_json.groups.put(1,groupInfoDefinition);
		secretToPath.groupIdPath.add(1);

		//Sets the next response to be the RPC.ListMySecretsAndGroupKeysResponse object
		fakePoster.nextResponse = gson.toJson(secrets_json);
		//Sets up the secret response for get Critical Data
		RPC.GetSecretResponse secretResponse = new RPC.GetSecretResponse();
		
		//Creates and encrypts the critical data.
		MitroApi.CriticalDataStruct criticalData = new MitroApi.CriticalDataStruct();
		criticalData.note="fake_note";
		String criticalDataMessage = user.getKey().encrypt(gson.toJson(criticalData));
		
		//Sets up necessary fields for the creation of a secret
		secretResponse.secret.secretId=666;
		secretResponse.secret.encryptedCriticalData=criticalDataMessage;
		secretResponse.secret.title="fake_secret";
		secretResponse.secret.groups = new ArrayList<Integer>();
		secretResponse.secret.groups.add(1);
		
		user.getSecretIdentiferData();
		//Sets the response and calls the api method
		fakePoster.nextResponse =gson.toJson(secretResponse);
		String decrytpedCriticalData = user.getCriticalDataContents(666,1,"note");
		
		//Asserts consistency of the data before and after going through the api. 
		assertEquals("fake_note",decrytpedCriticalData);
		
	}*/
}
