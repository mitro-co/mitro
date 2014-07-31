package co.mitro.mitro;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import co.mitro.mitro.MitroApi.SecretIdentifier;
import co.mitro.mitro.R.layout;

public class TutorialActivity extends MitroActivity {

public class SecretArrayAdapter extends ArrayAdapter<SecretIdentifier>{
  // TODO: you should use BaseAdapter if you plan on managing the array yourself.
  // ArrayAdapter has an internal array that you aren't using.
  private ArrayList<SecretIdentifier> entries;

  public SecretArrayAdapter(Context context, int textViewResourceId,
      ArrayList<SecretIdentifier> objects) {
    super(context, layout.secret_list_view, objects);
    this.entries = objects; 
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // This shouldn't be here.  This is called for every list item.
    Collections.sort(entries, new TitleComparator());
    View rowView;
    LayoutInflater inflater = (LayoutInflater) getContext()
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    SecretIdentifier currentSecret = entries.get(position);

    //Set the adapter layout for the type of secret
    rowView = inflater.inflate(R.layout.secret_list_view, parent, false);

    TextView titles = (TextView) rowView.findViewById(R.id.title);
    TextView urls = (TextView) rowView.findViewById(R.id.url);
    String loginUrlForListDisplay;
    String title;

    if(checkForExistingTitle(currentSecret)){
      title = currentSecret.title + " (" + currentSecret.secretData.username + ")";}
    else{
      title = currentSecret.title; }

    titles.setText(title);

    if(currentSecret.secretData.type.equals("note")){
      loginUrlForListDisplay = "Note";
    }
    else{
      URL loginUrl;

      try {
        loginUrl = new URL(currentSecret.secretData.loginUrl);
        loginUrlForListDisplay = loginUrl.getHost();
      } catch (MalformedURLException e) {
        loginUrlForListDisplay = currentSecret.secretData.loginUrl;
        e.printStackTrace();
      }
    }
    urls.setText(loginUrlForListDisplay);

    //If a manual password has an empty URL puts this placeholder for the URL space.
    if(TextUtils.isEmpty(loginUrlForListDisplay)){
      urls.setText("Password");
    }

    return rowView;
  } 
  
  @Override
  public void clear(){
    entries.clear();
  }

  public boolean checkForExistingTitle(SecretIdentifier currentSecret){
    ArrayList<SecretIdentifier> tempRemovedTitle = new ArrayList<SecretIdentifier>();
    tempRemovedTitle.addAll(entries);
    tempRemovedTitle.remove(currentSecret);
    for(int i =0;i<tempRemovedTitle.size();i++){

      if(tempRemovedTitle.get(i).title.equals(currentSecret.title)){
        return true;
      }
    }
    return false;
  }

  public class TitleComparator implements Comparator<SecretIdentifier> {
    @Override
    public int compare(SecretIdentifier o1, SecretIdentifier o2) {
      return o1.title.compareToIgnoreCase(o2.title);
    }
  }
}
  // View elements
  EditText username_field;

  int step = 0;

  TextView rightSwipeTextTitle;
  TextView rightSwipeTextBullet1;
  TextView rightSwipeTextBullet2;
  TextView rightSwipeTextBullet3;
  TextView thisIsASecret;
  ImageView listImage;
  ImageView notificationImage;
  ImageView arrow;
  Button nextButton;
  Button howToPaste;
  Button goToLogin;
  ListView exampleView;
  SecretArrayAdapter adapter;
  SharedPreferences.Editor editor;
  SharedPreferences prefs;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.first_activity_main);
    rightSwipeTextTitle = (TextView) findViewById(R.id.right_swipe_text_title);
    rightSwipeTextBullet1 = (TextView) findViewById(R.id.right_swipe_text_bullet1);
    rightSwipeTextBullet2 = (TextView) findViewById(R.id.right_swipe_text_bullet2);
    rightSwipeTextBullet3 = (TextView) findViewById(R.id.right_swipe_text_bullet3);
    listImage = (ImageView) findViewById(R.id.listview_image);
    howToPaste = (Button) findViewById(R.id.howToPaste);
    goToLogin = (Button) findViewById(R.id.goToLogin);
    arrow = (ImageView) findViewById(R.id.arrow);
    notificationImage = (ImageView) findViewById(R.id.notificationImage);
    nextButton = (Button) findViewById(R.id.next);
    rightSwipeTextBullet1.setVisibility(View.INVISIBLE);
    rightSwipeTextBullet2.setVisibility(View.INVISIBLE);
    rightSwipeTextBullet3.setVisibility(View.INVISIBLE);
    arrow.setVisibility(View.INVISIBLE);
    goToLogin.setVisibility(View.INVISIBLE);

    notificationImage.setVisibility(View.INVISIBLE);
    exampleView = (ListView) findViewById(R.id.example);

    editor = getPreferences(MODE_PRIVATE).edit();
    prefs = getPreferences(MODE_PRIVATE);

    final GestureDetector mGestureDetector = new GestureDetector(getApplicationContext(), new SwipeDetector());
    OnTouchListener mGestureListener = new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent aEvent) {
        if (mGestureDetector.onTouchEvent(aEvent)) {
          return true;
        } else {
          return false;
        }
      }
    };

    ArrayList<SecretIdentifier> secrets = new ArrayList<SecretIdentifier>();
    SecretIdentifier fakeSecret = new SecretIdentifier();
    MitroApi.ClientDataStruct fakeClient = new MitroApi.ClientDataStruct();

    howToPaste.setVisibility(View.INVISIBLE);
    fakeSecret.title = "Lectorius,Inc. Email";
    fakeClient.loginUrl = "https://www.lectorius.com/";
    fakeClient.type = "manual";
    fakeSecret.secretData = fakeClient;
    secrets.add(fakeSecret);
    adapter = new SecretArrayAdapter(TutorialActivity.this, android.R.layout.simple_list_item_1,
        secrets);
    exampleView.setAdapter(adapter);
    exampleView.setOnTouchListener(mGestureListener);
  }

  @Override
  public void onBackPressed() {
    step = step - 2;
    next(new View(getBaseContext()));
    return;
  }

  public void next(View v) {
    Animation leftToRightAnimation = AnimationUtils.loadAnimation(this, R.anim.left_to_right);

    step++;
    switch (step) {
      case -1:
        Intent killIntro = new Intent(this, LoginActivity.class);
        startActivity(killIntro);
        finish();
      case 0:
        fadeOutThenIn("Above is a secret.");

        break;
      case 1:
        arrow.clearAnimation();
        arrow.setVisibility(View.GONE);
        fadeOutThenIn("Secrets are your personal encrypted data.\n\nThey can be passwords or notes.");
        nextButton.setText("Got it.");
        break;
      case 2:
        arrow.setVisibility(View.VISIBLE);
        arrow.startAnimation(leftToRightAnimation);
        howToPaste.setVisibility(View.INVISIBLE);
        fadeOutThenIn("By swiping left to right across a secret password you can easily log into web services.");
        break;
      case 3:
        arrow.clearAnimation();
        arrow.setVisibility(View.GONE);
        fadeOutThenIn("When you swipe, your username is copied into your clipboard.");
        nextButton.setText("Got it.");
        howToPaste.setVisibility(View.VISIBLE);
        break;

      case 4:
        arrow.clearAnimation();
        arrow.setVisibility(View.GONE);
        howToPaste.setVisibility(View.INVISIBLE);
        fadeOutThenIn("If you swipe down at the top of your screen a notification bar will scroll down.");
        howToPaste.setVisibility(View.GONE);
        notificationImage.setVisibility(View.GONE);
        goToLogin.setVisibility(View.GONE);
        nextButton.setVisibility(View.VISIBLE);
        howToPaste.setText("How do I paste?");

        break;
      case 5:
        arrow.setVisibility(View.VISIBLE);
        arrow.startAnimation(leftToRightAnimation);
        fadeOutThenIn("When you swipe left to right a notification is put in the notification bar that will copy the password to your clipboard when clicked.");
        howToPaste.setText("What would that look like?");
        howToPaste.setVisibility(View.VISIBLE);
        goToLogin.setVisibility(View.GONE);
        nextButton.setVisibility(View.VISIBLE);
        notificationImage.setVisibility(View.GONE);
        break;
      case 6:
        notificationImage.setVisibility(View.GONE);
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
        break;

      default:
        break;
    }
  }

  public void goToLogin(View v) {
    notificationImage.setVisibility(View.GONE);
    Intent intent = new Intent(this, LoginActivity.class);
    startActivity(intent);
    finish();
  }

  public void pasteInfo(View v) {
    if (step == 5) {
      arrow.clearAnimation();
      arrow.setVisibility(View.GONE);
      rightSwipeTextTitle.setVisibility(View.GONE);
      howToPaste.setVisibility(View.INVISIBLE);
      notificationImage.setVisibility(View.VISIBLE);
      nextButton.setVisibility(View.INVISIBLE);
      goToLogin.setVisibility(View.VISIBLE);
      step++;
    } else {
      fadeOutThenIn("Hold down in a text field until a paste option appears.");
    }
  }

  public void fadeOutThenIn(String newText) {
    Animation fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
    rightSwipeTextTitle.setVisibility(View.INVISIBLE);
    rightSwipeTextTitle.setText(newText);
    rightSwipeTextTitle.startAnimation(fadeInAnimation);
    rightSwipeTextTitle.setVisibility(View.VISIBLE);
  }

  class SwipeDetector extends SimpleOnGestureListener {
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
          next(new View(getBaseContext()));
        }
        return true;
      }
      return false;
    }
  };
}
