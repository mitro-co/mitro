package co.mitro.mitro;

import java.io.IOException;
import java.net.MalformedURLException;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;

public class AddSecretActivity extends MitroActivity {
  private TextView titleView;
  private TextView urlView;
  private TextView usernameView;
  private TextView passwordView;

  private Handler handler = new Handler();

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.add_secret_activity_password);

    titleView = (TextView) findViewById(R.id.title);
    urlView = (TextView) findViewById(R.id.url);
    usernameView = (TextView) findViewById(R.id.username);
    passwordView = (TextView) findViewById(R.id.password);
    Spinner spinner = (Spinner) findViewById(R.id.spinner);

    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
        R.array.secretTypesArray, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);

    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent.getItemAtPosition(pos).equals("Note")) {
          Intent addSecret = new Intent(AddSecretActivity.this, AddNotesSecretActivity.class);
          startActivity(addSecret);
          AddSecretActivity.this.finish();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    });
    ActionBar actionBar = getSupportActionBar();
    actionBar.setDisplayHomeAsUpEnabled(true);
    actionBar.setHomeButtonEnabled(true);
    actionBar.setLogo(R.drawable.title_logo);
    actionBar.setDisplayUseLogoEnabled(true);
    actionBar.setDisplayShowTitleEnabled(false);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    // Respond to the action bar's Up/Home button
      case android.R.id.home:
        this.finish();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public boolean isTextViewEmpty(TextView textView) {
    return textView.getText().length() == 0;
  }

  public void addSecret(View v) {
    if (!(isTextViewEmpty(titleView)) && !(isTextViewEmpty(usernameView)) && !(isTextViewEmpty(passwordView))) {
      addSecretPasswordTask adder = new addSecretPasswordTask();
      adder.execute(titleView.getText().toString(), urlView.getText().toString(), usernameView
          .getText().toString(), passwordView.getText().toString());
      finish();
    } else {
      Toast.makeText(AddSecretActivity.this, "You haven't filled in all the required fields.",
          Toast.LENGTH_SHORT).show();
    }
  }

  class addSecretPasswordTask extends AsyncTask<String, Void, String> {
    boolean fail = false;

    @Override
    protected String doInBackground(String... params) {
      try {
        getApp().getApiClient().addSecretPassword(params[0], params[1], params[2], params[3]);
      } catch (MalformedURLException e) {
        fail = true;
      } catch (CryptoError e) {
        fail = true;
      } catch (IOException e) {
        fail = true;
      }
      return null;
    }

    protected void onPostExecute(Void result) {
      handler.post(new Runnable() {
        @Override
        public void run() {
          if (fail) {
            Toast.makeText(AddSecretActivity.this,
                "Your secret could not be added at this time. Check your network connection.",
                Toast.LENGTH_SHORT).show();
          }
          finish();
        }
      });
    }
  }
}
