package co.mitro.mitro;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import co.mitro.mitro.MitroApi.SecretIdentifier;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

public class SecretListActivity extends MitroActivity implements SecretManager.Listener {

  public enum TabIndex {
    ALL_TAB_INDEX,
    PASSWORDS_TAB_INDEX,
    NOTES_TAB_INDEX
  }

  public class SecretListAdapter extends BaseAdapter {
    LayoutInflater inflater = SecretListActivity.this.getLayoutInflater();

    @Override
    public int getCount() {
      return filteredSecrets.size();
    }

    @Override
    public SecretIdentifier getItem(int index) {
      return filteredSecrets.get(index);
    }

    @Override
    public long getItemId(int arg0) {
      return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = inflater.inflate(R.layout.secret_list_view, parent, false);
      }

      TextView titleText = (TextView) convertView.findViewById(R.id.title);
      TextView linkText = (TextView) convertView.findViewById(R.id.url);
      ImageView linkIcon = (ImageView) convertView.findViewById(R.id.link_icon);

      SecretIdentifier secret = getItem(position);
      titleText.setText(secret.displayTitle);

      String domain = secret.getDomain();
      linkText.setText(domain);
      linkIcon.setVisibility(TextUtils.isEmpty(domain) ? View.INVISIBLE : View.VISIBLE);

      return convertView;
    }
  }

  // View Elements
  private TextView waitMessage;
  private ProgressBar loading;

  // Secrets list view, model, and adapter
  private ListView secretsListView;
  private SecretListAdapter adapter;
  private List<SecretIdentifier> allSecrets = new ArrayList<SecretIdentifier>();
  private List<SecretIdentifier> filteredSecrets = new ArrayList<SecretIdentifier>();

  private boolean needsRefresh = true;

  // Top Action bar
  private ActionBar actionBar;

  // Swipe to go Data
  private String criticalDataForSwipeToGo;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    // Instantiates views and arraylists
    secretsListView = (ListView) findViewById(R.id.titles);
    waitMessage = (TextView) findViewById(R.id.wait);
    loading = (ProgressBar) findViewById(R.id.decrypting_progress);

    actionBar = getSupportActionBar();

    // Create a tab listener that is called when the user changes tabs.
    ActionBar.TabListener tabListener = new ActionBar.TabListener() {
      @Override
      public void onTabSelected(Tab tab, FragmentTransaction arg1) {
        filterSecrets();
        secretsListView.setSelectionFromTop(0, 0);
      }

      @Override
      public void onTabUnselected(Tab arg0, FragmentTransaction arg1) {
      }

      @Override
      public void onTabReselected(Tab arg0, FragmentTransaction arg1) {
      }
    };

    Tab allTab = actionBar.newTab().setText("All").setTabListener(tabListener);
    Tab passwordsTab = actionBar.newTab().setText("Passwords").setTabListener(tabListener);
    Tab notesTab = actionBar.newTab().setText("Notes").setTabListener(tabListener);

    actionBar.addTab(allTab, TabIndex.ALL_TAB_INDEX.ordinal(), true);
    actionBar.addTab(passwordsTab, TabIndex.PASSWORDS_TAB_INDEX.ordinal(), false);
    actionBar.addTab(notesTab, TabIndex.NOTES_TAB_INDEX.ordinal(), false);

    actionBar.setLogo(R.drawable.title_logo);
    actionBar.setDisplayUseLogoEnabled(true);
    actionBar.setDisplayShowTitleEnabled(false);

    adapter = new SecretListAdapter();
    secretsListView.setAdapter(adapter);

    // Creates the gesture detector to enable swipe to go
    final GestureDetector gestureDetector = new GestureDetector(getApplicationContext(), new SwipeDetector());

    secretsListView.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent aEvent) {
        if (gestureDetector.onTouchEvent(aEvent)) {
          return true;
        } else {
          return false;
        }
      }
    });

    secretsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
        SecretIdentifier secret = (SecretIdentifier) secretsListView.getItemAtPosition(position);
        Intent intent = new Intent(SecretListActivity.this, SecretViewActivity.class);
        intent.putExtra("secret_id", secret.id);
        startActivity(intent);
      }
    });
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.refresh:
        refreshSecrets();
        return true;
      case R.id.add_secret:
        Intent addSecret = new Intent(this, AddSecretActivity.class);
        startActivity(addSecret);
        needsRefresh = true;
        return true;
      case R.id.logout_icon:
        getApp().getSecretManager().clear();
        getApp().getApiClient().logout();
        Intent logout = new Intent(this, LoginActivity.class);
        startActivity(logout);
        finish();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  public void refreshSecrets() {
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    loading.setVisibility(View.VISIBLE);
    waitMessage.setVisibility(View.VISIBLE);
    secretsListView.setVisibility(View.INVISIBLE);

    getApp().getSecretManager().listSecrets();
  }

  @Override
  public void onStart() {
    super.onStart();

    getApp().getSecretManager().addListener(this);

    if (getApp().isLoggedIn() && needsRefresh) {
      needsRefresh = false;
      refreshSecrets();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    getApp().getSecretManager().removeListener(this);
  }

  public void filterSecrets() {
    TabIndex tabIndex = TabIndex.values()[actionBar.getSelectedTab().getPosition()];
    filteredSecrets.clear();

    switch (tabIndex) {
      case NOTES_TAB_INDEX:
      for (int i = 0; i < allSecrets.size(); i++) {
        if (allSecrets.get(i).getType().equals("note")) {
          filteredSecrets.add(allSecrets.get(i));
        }
      }
      break;
      case PASSWORDS_TAB_INDEX:
      for (int i = 0; i < allSecrets.size(); i++) {
        if (!allSecrets.get(i).getType().equals("note")) {
          filteredSecrets.add(allSecrets.get(i));
        }
      }
      break;
      case ALL_TAB_INDEX:
      filteredSecrets.addAll(allSecrets);
      break;
    }

    adapter.notifyDataSetChanged();
  }

  public void setDisplayTitles(List<SecretIdentifier> secrets) {
    Map<String, Integer> titles = Maps.newHashMap();

    for (SecretIdentifier secret: secrets) {
      if (titles.containsKey(secret.title)) {
        titles.put(secret.title, 2);
      } else {
        titles.put(secret.title, 1);
      }
    }

    for (SecretIdentifier secret: secrets) {
      if (titles.get(secret.title).intValue() > 1 &&
          !Strings.isNullOrEmpty(secret.secretData.username)) {
        secret.displayTitle = secret.title + " (" + secret.secretData.username + ")";
      } else {
        secret.displayTitle = secret.title;
      }
    }
  }

  public class TitleComparator implements Comparator<SecretIdentifier> {
    @Override
    public int compare(SecretIdentifier o1, SecretIdentifier o2) {
      return o1.displayTitle.compareToIgnoreCase(o2.displayTitle);
    }
  }

  public void onListSecrets(List<SecretIdentifier> secrets) {
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    loading.setVisibility(View.INVISIBLE);
    waitMessage.setVisibility(View.INVISIBLE);
    secretsListView.setVisibility(View.VISIBLE);

    allSecrets.clear();
    allSecrets.addAll(secrets);
    setDisplayTitles(allSecrets);
    Collections.sort(allSecrets, new TitleComparator());
    filterSecrets();
}

  public void onListSecretsFailed(Exception e) {
    if (e instanceof IOException) {
      Toast.makeText(SecretListActivity.this, "Check your network connection", Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(SecretListActivity.this, "Unable to get your secrets at this time.",
          Toast.LENGTH_LONG).show();
      Intent intent = new Intent(SecretListActivity.this, LoginActivity.class);
      startActivity(intent);
      finish();
    }
  }

  public void onGetSecretCriticalData(String criticalData) {
    criticalDataForSwipeToGo = criticalData;
  }

  public void onGetSecretCriticalDataFailed(Exception e) {
    Toast.makeText(SecretListActivity.this,
        "Unable to get secret data. Check your network connection.", Toast.LENGTH_LONG).show();
  }

  // Class to detect swipes and perform the appropriate actions
  class SwipeDetector extends SimpleOnGestureListener {
    // TODO: I think you need to scale these based on the device's dpi.
    private static final int SWIPE_MIN_DISTANCE = 150;
    private static final int SWIPE_MAX_OFF_PATH = 100;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;
    private MotionEvent mLastOnDownEvent = null;

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      if (e1 == null)
        e1 = mLastOnDownEvent;
      if (e1 == null || e2 == null)
        return false;
      float dX = e2.getX() - e1.getX();
      float dY = e1.getY() - e2.getY();
      if (Math.abs(dY) < SWIPE_MAX_OFF_PATH && Math.abs(velocityX) >= SWIPE_THRESHOLD_VELOCITY
          && Math.abs(dX) >= SWIPE_MIN_DISTANCE) {
        if (dX > 0) {
          int position = secretsListView.pointToPosition(Math.round(e1.getX()), Math.round(e1.getY()));
          Object o = secretsListView.getItemAtPosition(position);
          SecretIdentifier clickedSecret = (SecretIdentifier) o;
          String loginUrl = clickedSecret.secretData.loginUrl;
          String secretUsername = clickedSecret.secretData.username;
          String type = clickedSecret.secretData.type;

          if ((Strings.isNullOrEmpty(loginUrl) || Strings.isNullOrEmpty(secretUsername) || type
              .equals("note"))) {
            Toast.makeText(getApplicationContext(), "This item does not support swipe to go.",
                Toast.LENGTH_SHORT).show();
          } else {
            getApp().getSecretManager().getSecretCriticalData(clickedSecret.id);
            notificationPasswordCopy(clickedSecret);
            Toast.makeText(getApplicationContext(), "Username copied to clipboard.",
                Toast.LENGTH_SHORT).show();
          }
        }

        return true;
      }
      return false;
    }
  };

  public void notificationPasswordCopy(SecretIdentifier secretForCopy) {
    try {
      copyText("username", secretForCopy.secretData.username);
      Uri uri = Uri.parse(secretForCopy.secretData.loginUrl);
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      startActivity(intent);

      // The following code handles the notification copy
      Intent notificationIntent = new Intent(this, NotificationCopyActivity.class);
      notificationIntent.setAction("android.intent.action.MAIN");
      notificationIntent.addCategory("android.intent.category.LAUNCHER");
      PendingIntent pIntent = PendingIntent.getActivity(this, 0, notificationIntent,
          PendingIntent.FLAG_UPDATE_CURRENT | Notification.FLAG_AUTO_CANCEL);
      Notification noti = new NotificationCompat.Builder(this).setContentTitle(secretForCopy.title)
          .setContentText("Password Available for Copy!").setSmallIcon(R.drawable.mitro_logo_small)
          .setContentIntent(pIntent).addAction(0, "Copy Password", pIntent).build();

      final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      notificationManager.notify(4123, noti);

      // Broadcast Reciever that copies critical data to the clipboard
      // upon notification click
      BroadcastReceiver call_method = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action_name = intent.getAction();
          if (action_name.equals("copy")) {
            copyText("password", criticalDataForSwipeToGo);
            notificationManager.cancel(4123);
            PackageManager pm = getPackageManager();
            Intent queryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.mitro.co"));
            ActivityInfo af = queryIntent.resolveActivityInfo(pm, 0);
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.setClassName(af.packageName, af.name);
            startActivity(launchIntent);
          }
        }
      };

      registerReceiver(call_method, new IntentFilter("copy"));

    } catch (Exception e) {
      Toast.makeText(getApplicationContext(), "This item does not support swipe to go .",
          Toast.LENGTH_SHORT).show();
    }
  }
}
