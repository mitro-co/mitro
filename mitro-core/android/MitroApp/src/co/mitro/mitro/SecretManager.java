package co.mitro.mitro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.os.AsyncTask;
import android.os.Handler;
import co.mitro.mitro.MitroApi.SecretIdentifier;

import com.google.common.collect.Maps;

public class SecretManager {
  public interface Listener {
    public void onListSecrets(List<SecretIdentifier> secrets);
    public void onListSecretsFailed(Exception e);

    public void onGetSecretCriticalData(String criticalData);
    public void onGetSecretCriticalDataFailed(Exception e);
  }

  private MitroApi apiClient = null;
  private Map<Integer, SecretIdentifier> secretsMap = Maps.newHashMap();
 
  private List<Listener> listeners = new ArrayList<Listener>();
  private Handler handler = new Handler();
  private ListSecretsTask listSecretsTask = null;
  private List<GetSecretCriticalDataTask> getSecretCriticalDataTasks = new ArrayList<GetSecretCriticalDataTask>();
  
  SecretManager(MitroApi apiClient) {
    this.apiClient = apiClient;
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public void clear() {
    if (listSecretsTask != null) {
      listSecretsTask.cancel(true);
      listSecretsTask = null;
    }
    for (GetSecretCriticalDataTask task : getSecretCriticalDataTasks) {
      task.cancel(true);
    }
    getSecretCriticalDataTasks.clear();

    secretsMap.clear();
  }
  
  public SecretIdentifier getSecret(Integer secretId) {
    SecretIdentifier secret = secretsMap.get(secretId);
    return secret;
  }
  
  public void listSecrets() {
    if (listSecretsTask != null) {
      return;
    }

    listSecretsTask = new ListSecretsTask();
    listSecretsTask.execute();
  }

  public boolean getSecretCriticalData(Integer secretId) {
    SecretIdentifier secret = getSecret(secretId);
    if (secret == null) {
      return false;
    }

    GetSecretCriticalDataTask task = new GetSecretCriticalDataTask();
    // Important: add the task before it is executed or there is a race condition.
    getSecretCriticalDataTasks.add(task);
    task.execute(secret);
    
    return true;
  }

  class ListSecretsTask extends AsyncTask<Void, Void, Exception> {
    List<SecretIdentifier> secrets;

    @Override
    protected Exception doInBackground(Void... arg0) {
      try {
         secrets = apiClient.getSecretIdentiferData();
      } catch (Exception e) {
        return e;
      }
      return null;
    }

    protected void onPostExecute(final Exception e) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          if (e == null) {
            secretsMap.clear();
            for (SecretIdentifier secret : secrets) {
              secretsMap.put(Integer.valueOf(secret.id),  secret);
            }
          }
          for (Listener listener : listeners) {
            if (e == null) {
              listener.onListSecrets(secrets);
            } else {
              listener.onListSecretsFailed(e);
            }
          }
          listSecretsTask = null;
        }
      });
    }
  }

  class GetSecretCriticalDataTask extends AsyncTask<SecretIdentifier, Void, Exception> {
    String criticalData;

    @Override
    protected Exception doInBackground(SecretIdentifier... secrets) {
      SecretIdentifier secret = secrets[0];
      try {
        criticalData = apiClient.getCriticalDataContents(secret.id,
            secret.groupid, secret.secretData.type);
      } catch (Exception e) {
        return e;
      }
      return null;
    }

    protected void onPostExecute(final Exception e) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          for (Listener listener : listeners) {
            if (e == null) {
              listener.onGetSecretCriticalData(criticalData);
            } else {
              listener.onGetSecretCriticalDataFailed(e);
            }
          }
          getSecretCriticalDataTasks.remove(GetSecretCriticalDataTask.this);
        }
      });
    }
  }
}
