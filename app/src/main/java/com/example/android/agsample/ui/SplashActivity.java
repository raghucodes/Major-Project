package com.example.android.agsample.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.example.android.agsample.R;

public class SplashActivity extends AppCompatActivity {

    private long ms=0,splashTime = 1200;
    private boolean splashActive = true,paused = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        setStatusBarColor(R.color.colorPrimary);

        final ConstraintLayout cl = (ConstraintLayout) findViewById(R.id.cl);

        Thread thread = new Thread(){
            @Override
            public void run() {
                try{
                    while (splashActive && ms<splashTime){
                        if(!paused)
                            ms = ms+100;
                        sleep(100);
                    }
                }catch (Exception e){

                }finally {
                    if(!isOnline()){
                        Snackbar snackbar = Snackbar
                                .make(cl,"No Internet access",Snackbar.LENGTH_INDEFINITE)
                                .setAction("Retry", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        recreate();
                                    }
                                });
                        snackbar.show();
                    }
                    else{
                        goMain();
                    }
                }
            }
        };
        thread.start();
    }

    private boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnectedOrConnecting();
    }

    private void setStatusBarColor(@ColorRes int statusBarColor) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            int color = ContextCompat.getColor(this,statusBarColor);
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(color);
        }
    }

    private void goMain() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
