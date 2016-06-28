package com.harasoft.relaunch;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;

public class Home extends Activity {
	ReLaunchApp app;
	 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = ((ReLaunchApp) getApplicationContext());
        if (app != null) {
            app.RestartIntent = PendingIntent.getActivity(this, 0, getIntent(),
                    			getIntent().getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);

            Intent intent = new Intent(Home.this, ReLaunch.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivityForResult(intent, 0);
        }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		finish();
	}
}
