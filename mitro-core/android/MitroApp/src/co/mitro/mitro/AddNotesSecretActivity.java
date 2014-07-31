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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import co.mitro.core.crypto.KeyInterfaces.CryptoError;

public class AddNotesSecretActivity extends MitroActivity {
  private EditText nameView;
  private EditText noteView;

  private Handler handler = new Handler();

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.add_secret_activity_note);

    nameView = (EditText) findViewById(R.id.name);
    noteView = (EditText) findViewById(R.id.note);

    Spinner spinner = (Spinner) findViewById(R.id.spinner);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
        R.array.secretNoteTypesArray, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);

    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent.getItemAtPosition(pos).equals("Password")) {
          Intent addSecret = new Intent(AddNotesSecretActivity.this, AddSecretActivity.class);
          startActivity(addSecret);
          AddNotesSecretActivity.this.finish();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {
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

  public void addSecret(View v) {
    if (nameView.getText().length() > 0 && noteView.getText().length() > 0) {
      addSecretPasswordTask adder = new addSecretPasswordTask();
      adder.execute(nameView.getText().toString(), noteView.getText().toString());
      finish();
    } else {
      Toast.makeText(this, "You must fill in all fields.", Toast.LENGTH_SHORT).show();
    }
  }

  class addSecretPasswordTask extends AsyncTask<String, Void, String> {
    boolean fail = false;

    @Override
    protected String doInBackground(String... param) {
      try {
        getApp().getApiClient().addSecretNote(param[0], param[1]);
      } catch (MalformedURLException e) {
        fail = true;
      } catch (CryptoError e) {
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
            Toast.makeText(AddNotesSecretActivity.this,
                "Your note could not be added at this time. Check your network connection.",
                Toast.LENGTH_SHORT).show();
          }

          AddNotesSecretActivity.this.finish();
        }
      });
    }
  }
}
