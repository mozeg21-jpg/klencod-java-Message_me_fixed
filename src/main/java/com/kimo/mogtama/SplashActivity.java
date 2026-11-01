package com.kimo.mogtama;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final long SPLASH_DELAY = 2200;
    private static final String PREFS_NAME = "mogtama_prefs";

    private static final int COLOR_PRIMARY = Color.parseColor("#E85C5C");
    private static final int COLOR_PRIMARY_LIGHT = Color.parseColor("#FF7B7B");
    private static final int COLOR_BLACK = Color.parseColor("#111111");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");

    private FrameLayout root;
    private View decoTop;
    private View decoBottom;
    private View nameBar;
    private LinearLayout logoTile;
    private TextView logoLetter;
    private TextView appName;

    private final Handler handler = new Handler();
    private final Runnable goNext = new Runnable() {
        @Override
        public void run() {
            openNextScreen();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        initViews();
        setupSystemBars();
        applyStyles();
        playEntranceAnimation();

        handler.postDelayed(goNext, SPLASH_DELAY);
    }

    private void initViews() {
        root = (FrameLayout) findViewById(R.id.root);
        decoTop = findViewById(R.id.deco_top);
        decoBottom = findViewById(R.id.deco_bottom);
        nameBar = findViewById(R.id.name_bar);
        logoTile = (LinearLayout) findViewById(R.id.logo_tile);
        logoLetter = (TextView) findViewById(R.id.logo_letter);
        appName = (TextView) findViewById(R.id.app_name);
    }

    private void setupSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        // الخلفية حمراء فنسيب أيقونات شريط الحالة فاتحة
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void applyStyles() {
        // الخلفية
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#F27878"), COLOR_PRIMARY, Color.parseColor("#C94444")});
        root.setBackground(bg);

        // دوائر الزينة
        GradientDrawable d1 = new GradientDrawable();
        d1.setShape(GradientDrawable.OVAL);
        d1.setColor(Color.parseColor("#22FFFFFF"));
        decoTop.setBackground(d1);
        FrameLayout.LayoutParams p1 = new FrameLayout.LayoutParams(
                dp(260), dp(260), Gravity.TOP | Gravity.LEFT);
        p1.setMargins(-dp(90), -dp(90), 0, 0);
        decoTop.setLayoutParams(p1);

        GradientDrawable d2 = new GradientDrawable();
        d2.setShape(GradientDrawable.OVAL);
        d2.setColor(Color.parseColor("#1AFFFFFF"));
        decoBottom.setBackground(d2);
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(
                dp(320), dp(320), Gravity.BOTTOM | Gravity.RIGHT);
        p2.setMargins(0, 0, -dp(110), -dp(110));
        decoBottom.setLayoutParams(p2);

        // مربع الشعار الأسود
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setShape(GradientDrawable.RECTANGLE);
        tileBg.setColor(COLOR_BLACK);
        tileBg.setCornerRadius(dp(32));
        tileBg.setStroke(dp(3), Color.parseColor("#44FFFFFF"));
        logoTile.setBackground(tileBg);
        logoTile.setElevation(dp(14));

        // الحرف داخل الشعار
        logoLetter.setText("A");
        logoLetter.setTextColor(COLOR_PRIMARY_LIGHT);
        logoLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 66);
        logoLetter.setTypeface(null, Typeface.BOLD);
        logoLetter.setIncludeFontPadding(false);

        // اسم التطبيق
        appName.setText("Ahmed Saad Community");
        appName.setTextColor(COLOR_WHITE);
        appName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        appName.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams =
                (LinearLayout.LayoutParams) appName.getLayoutParams();
        nameParams.topMargin = dp(26);
        appName.setLayoutParams(nameParams);

        // شريط أسود صغير تحت الاسم
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(COLOR_BLACK);
        barBg.setCornerRadius(dp(3));
        nameBar.setBackground(barBg);
        LinearLayout.LayoutParams barParams =
                (LinearLayout.LayoutParams) nameBar.getLayoutParams();
        barParams.topMargin = dp(14);
        nameBar.setLayoutParams(barParams);
    }

    private void playEntranceAnimation() {
        logoTile.setAlpha(0f);
        logoTile.setScaleX(0.5f);
        logoTile.setScaleY(0.5f);
        logoTile.setRotation(-30f);
        logoTile.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotation(-6f)
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator())
                .start();

        appName.setAlpha(0f);
        appName.setTranslationY(dp(24));
        appName.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(350)
                .setDuration(500)
                .start();

        nameBar.setScaleX(0f);
        nameBar.animate()
                .scaleX(1f)
                .setStartDelay(800)
                .setDuration(450)
                .start();
    }

    private void openNextScreen() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uid = prefs.getString("uid", "");
        String name = prefs.getString("name", "");
        boolean registered = uid.length() > 0 && name.length() > 0;

        Intent intent = new Intent();
        if (registered) {
            intent.setClass(getApplicationContext(), ChatActivity.class);
        } else {
            intent.setClass(getApplicationContext(), RegisterActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(goNext);
        super.onDestroy();
    }
}