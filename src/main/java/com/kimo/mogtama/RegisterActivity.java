package com.kimo.mogtama;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegisterActivity extends Activity {

    private static final String PREFS_NAME = "mogtama_prefs";

    private static final int COLOR_PRIMARY = Color.parseColor("#E85C5C");
    private static final int COLOR_PRIMARY_LIGHT = Color.parseColor("#FF7B7B");
    private static final int COLOR_BG = Color.parseColor("#FFF8F8");
    private static final int COLOR_BLACK = Color.parseColor("#111111");
    private static final int COLOR_BLACK_SOFT = Color.parseColor("#2E2E2E");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_FIELD = Color.parseColor("#FFF1F1");
    private static final int COLOR_FIELD_STROKE = Color.parseColor("#F3C9C9");
    private static final int COLOR_ID_FIELD = Color.parseColor("#F4F4F4");
    private static final int COLOR_ID_STROKE = Color.parseColor("#E2E2E2");
    private static final int COLOR_HINT = Color.parseColor("#9A8A8A");
    private static final int COLOR_LABEL = Color.parseColor("#5A4A4A");

    private ScrollView rootScroll;
    private LinearLayout content;
    private LinearLayout header;
    private LinearLayout logoTile;
    private LinearLayout card;
    private TextView logoLetter;
    private TextView title;
    private TextView labelId;
    private TextView labelName;
    private TextView note;
    private TextView btnSend;
    private EditText editId;
    private EditText editName;

    private String deviceUid = "";
    private boolean sending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        initViews();
        setupSystemBars();
        deviceUid = generateDeviceUid();
        applyStyles();
        setupInsets();
        setupListeners();
        playEntranceAnimation();
    }

    private void initViews() {
        rootScroll = (ScrollView) findViewById(R.id.root_scroll);
        content = (LinearLayout) findViewById(R.id.content);
        header = (LinearLayout) findViewById(R.id.header);
        logoTile = (LinearLayout) findViewById(R.id.logo_tile);
        card = (LinearLayout) findViewById(R.id.card);
        logoLetter = (TextView) findViewById(R.id.logo_letter);
        title = (TextView) findViewById(R.id.title);
        labelId = (TextView) findViewById(R.id.label_id);
        labelName = (TextView) findViewById(R.id.label_name);
        note = (TextView) findViewById(R.id.note);
        btnSend = (TextView) findViewById(R.id.btn_send);
        editId = (EditText) findViewById(R.id.edit_id);
        editName = (EditText) findViewById(R.id.edit_name);
    }

    private void setupSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        // الهيدر أحمر فاتح والعنوان أسود، فالأيقونات تكون غامقة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
    }

    private void setupInsets() {
        applyInsets(getStatusBarHeight(), 0);
        rootScroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets sys = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    top = sys.top;
                    bottom = Math.max(sys.bottom, ime.bottom);
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                applyInsets(top, bottom);
                return insets;
            }
        });
        rootScroll.requestApplyInsets();
    }

    private void applyInsets(int top, int bottom) {
        header.setPadding(dp(24), top + dp(32), dp(24), dp(64));
        content.setPadding(0, 0, 0, bottom + dp(24));
    }

    private String generateDeviceUid() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString("device_uid", "");
        if (saved.length() > 0) {
            return saved;
        }

        String androidId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
        String uid;
        if (androidId != null && androidId.length() > 0) {
            uid = "u_" + androidId;
        } else {
            uid = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        prefs.edit().putString("device_uid", uid).apply();
        return uid;
    }

    private void applyStyles() {
        rootScroll.setBackgroundColor(COLOR_BG);

        // الهيدر الأحمر بحواف سفلية دائرية
        float r = dp(40);
        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#F27878"), COLOR_PRIMARY});
        headerBg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        header.setBackground(headerBg);
        header.setElevation(dp(6));

        // شعار الحرف
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setShape(GradientDrawable.RECTANGLE);
        tileBg.setColor(COLOR_BLACK);
        tileBg.setCornerRadius(dp(24));
        tileBg.setStroke(dp(2), Color.parseColor("#44FFFFFF"));
        logoTile.setBackground(tileBg);
        logoTile.setElevation(dp(8));
        logoTile.setRotation(-6f);

        logoLetter.setText("A");
        logoLetter.setTextColor(COLOR_PRIMARY_LIGHT);
        logoLetter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        logoLetter.setTypeface(null, Typeface.BOLD);
        logoLetter.setIncludeFontPadding(false);

        // العنوان
        title.setText("Welcome to Ahmed Saad Community");
        title.setTextColor(COLOR_BLACK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        setMargins(title, 0, dp(18), 0, 0);

        // الكارت يتداخل مع الهيدر
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_WHITE);
        cardBg.setCornerRadius(dp(24));
        cardBg.setStroke(dp(1), Color.parseColor("#FFE3E3"));
        card.setBackground(cardBg);
        card.setElevation(dp(10));
        card.setPadding(dp(20), dp(24), dp(20), dp(24));
        setMargins(card, dp(20), -dp(36), dp(20), 0);

        // معرّف المستخدم
        styleLabel(labelId, "User ID");
        styleField(editId, COLOR_ID_FIELD, COLOR_ID_STROKE);
        editId.setText(deviceUid);
        editId.setTextColor(Color.parseColor("#6B6B6B"));
        editId.setTextDirection(View.TEXT_DIRECTION_LTR);
        editId.setFocusable(false);
        editId.setFocusableInTouchMode(false);
        editId.setCursorVisible(false);
        editId.setLongClickable(false);
        setMargins(editId, 0, dp(6), 0, dp(16));

        // اسم المستخدم
        styleLabel(labelName, "Display Name");
        styleField(editName, COLOR_FIELD, COLOR_FIELD_STROKE);
        editName.setHint("Enter your name");
        editName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        editName.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        setMargins(editName, 0, dp(6), 0, dp(8));

        // الملاحظة
        note.setText("Your ID will not be visible to others");
        note.setTextColor(COLOR_HINT);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        note.setGravity(Gravity.RIGHT);
        note.setTextDirection(View.TEXT_DIRECTION_LTR);
        setMargins(note, dp(4), 0, dp(4), dp(24));

        // زر الإرسال
        btnSend.setText("Join");
        btnSend.setTextColor(COLOR_WHITE);
        btnSend.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnSend.setTypeface(null, Typeface.BOLD);
        btnSend.setGravity(Gravity.CENTER);
        btnSend.setClickable(true);
        btnSend.setFocusable(true);
        btnSend.setElevation(dp(4));
        LinearLayout.LayoutParams btnParams =
                (LinearLayout.LayoutParams) btnSend.getLayoutParams();
        btnParams.height = dp(52);
        btnSend.setLayoutParams(btnParams);

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_BLACK_SOFT, COLOR_BLACK});
        btnBg.setCornerRadius(dp(26));
        btnSend.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), btnBg, null));
    }

    private void styleLabel(TextView label, String text) {
        label.setText(text);
        label.setTextColor(COLOR_LABEL);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.LEFT);
        label.setTextDirection(View.TEXT_DIRECTION_LTR);
    }

    private void styleField(EditText field, int fillColor, int strokeColor) {
        field.setBackground(makeFieldBg(fillColor, strokeColor));
        field.setTextColor(COLOR_BLACK);
        field.setHintTextColor(COLOR_HINT);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        field.setSingleLine(true);
        field.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        field.setTextDirection(View.TEXT_DIRECTION_LTR);
        field.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) field.getLayoutParams();
        params.height = dp(52);
        field.setLayoutParams(params);
    }

    private GradientDrawable makeFieldBg(int fillColor, int strokeColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), strokeColor);
        return bg;
    }

    private void setupListeners() {
        editName.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    editName.setBackground(makeFieldBg(COLOR_WHITE, COLOR_PRIMARY));
                } else {
                    editName.setBackground(makeFieldBg(COLOR_FIELD, COLOR_FIELD_STROKE));
                }
            }
        });

        editName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    sendRegistration();
                    return true;
                }
                return false;
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendRegistration();
            }
        });
    }

    private void sendRegistration() {
        if (sending) {
            return;
        }

        final String name = editName.getText().toString().trim();
        if (name.length() == 0) {
            Toast.makeText(this, "Please enter your name first", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref;
        try {
            ref = FirebaseDatabase.getInstance().getReference("users").child(deviceUid);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Firebase Database غير مفعّل في التطبيق، راجع إعدادات المشروع",
                    Toast.LENGTH_LONG).show();
            return;
        }

        hideKeyboard();
        setSendingState(true);

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("name", name);
        data.put("showId", false);
        data.put("createdAt", ServerValue.TIMESTAMP);

        ref.setValue(data).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(Task<Void> task) {
                if (task.isSuccessful()) {
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString("uid", deviceUid)
                            .putString("name", name)
                            .putBoolean("show_id", false)
                            .apply();
                    openChat();
                } else {
                    setSendingState(false);
                    Toast.makeText(RegisterActivity.this,
                            "حصلت مشكلة في الاتصال، حاول تاني",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setSendingState(boolean state) {
        sending = state;
        btnSend.setText(state ? "Joining..." : "Join");
        btnSend.setAlpha(state ? 0.7f : 1f);
        btnSend.setEnabled(!state);
    }

    private void openChat() {
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), ChatActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }
    }

    private void playEntranceAnimation() {
        logoTile.setAlpha(0f);
        logoTile.setScaleX(0.7f);
        logoTile.setScaleY(0.7f);
        logoTile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start();

        card.setAlpha(0f);
        card.setTranslationY(dp(30));
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(150).setDuration(450).start();
    }

    private void setMargins(View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            result = getResources().getDimensionPixelSize(resId);
        }
        return result;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}