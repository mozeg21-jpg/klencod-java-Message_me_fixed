package com.kimo.mogtama;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends Activity {

    private static final String PREFS_NAME = "mogtama_prefs";
    private static final int MESSAGES_LIMIT = 100;
    private static final long GROUP_GAP = 5 * 60 * 1000L;

    private static final int COLOR_PRIMARY = Color.parseColor("#E85C5C");
    private static final int COLOR_BG = Color.parseColor("#FFF8F8");
    private static final int COLOR_BLACK = Color.parseColor("#111111");
    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_SOFT = Color.parseColor("#FFF1F1");
    private static final int COLOR_AVATAR_SOFT = Color.parseColor("#FFE3E3");
    private static final int COLOR_STROKE = Color.parseColor("#F3C9C9");
    private static final int COLOR_HINT = Color.parseColor("#9A8A8A");
    private static final int COLOR_LABEL = Color.parseColor("#5A4A4A");
    private static final int COLOR_DIVIDER = Color.parseColor("#F1E1E1");

    private LinearLayout root;
    private LinearLayout toolbar;
    private LinearLayout replyBar;
    private LinearLayout replyInfo;
    private LinearLayout inputBar;
    private TextView avatarBtn;
    private TextView title;
    private TextView replyName;
    private TextView replyText;
    private TextView btnCancelReply;
    private ListView listMessages;
    private EditText editMessage;
    private ImageView btnSend;

    private SharedPreferences prefs;
    private String myUid = "";
    private String myName = "";
    private boolean showIdPublic = false;

    private DatabaseReference messagesRef;
    private DatabaseReference usersRef;
    private Query messagesQuery;
    private ValueEventListener messagesListener;
    private boolean dbReady = true;
    // ── Voice ──
    private VoiceRecorderHelper voiceHelper;
    private WaveformView waveformViewRecording;
    private android.widget.TextView recordingTimer;
    private android.view.View recordingBar;
    private boolean isRecording = false;
    private boolean hasText = false;
    private final android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long recordStartMs;
    private static final int PERM_MIC = 201;
    private android.media.AudioTrack activeTrack = null;
    private int playingVoicePos = -1;

    private final ArrayList<ChatMessage> messages = new ArrayList<ChatMessage>();
    private MessageAdapter adapter;
    private ChatMessage replyTarget = null;
    private boolean firstLoad = true;
    private boolean scrollNext = false;
    private int lastBottomInset = 0;

    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm", Locale.getDefault());

    // ================= Model =================
    private static class ChatMessage {
        String id = "";
        String senderId = "";
        String senderName = "";
        String text = "";
        long time = 0;
        String replyId = "";
        String replyName = "";
        String replyText = "";

        boolean hasReply() {
            return replyId.length() > 0 || replyText.length() > 0;
        }
    }

    // ================= Lifecycle =================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        myUid = prefs.getString("uid", "");
        myName = prefs.getString("name", "");
        showIdPublic = prefs.getBoolean("show_id", false);

        if (myUid.length() == 0 || myName.length() == 0) {
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), RegisterActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        initViews();
        setupSystemBars();
        applyStyles();
        setupList();
        setupInsets();
        setupListeners();
        updateSendState();

        if (dbReady) {
            listenToMessages();
        } else {
            Toast.makeText(this,
                    "Firebase Database غير مفعّل في التطبيق، راجع إعدادات المشروع",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (messagesQuery != null && messagesListener != null) {
            messagesQuery.removeEventListener(messagesListener);
        }
        super.onDestroy();
    }

    // ================= Init =================
    private void initViews() {
        root = (LinearLayout) findViewById(R.id.root);
        toolbar = (LinearLayout) findViewById(R.id.toolbar);
        replyBar = (LinearLayout) findViewById(R.id.reply_bar);
        replyInfo = (LinearLayout) findViewById(R.id.reply_info);
        inputBar = (LinearLayout) findViewById(R.id.input_bar);
        avatarBtn = (TextView) findViewById(R.id.avatar_btn);
        title = (TextView) findViewById(R.id.title);
        replyName = (TextView) findViewById(R.id.reply_name);
        replyText = (TextView) findViewById(R.id.reply_text);
        btnCancelReply = (TextView) findViewById(R.id.btn_cancel_reply);
        listMessages = (ListView) findViewById(R.id.list_messages);
        editMessage = (EditText) findViewById(R.id.edit_message);
        btnSend = (ImageView) findViewById(R.id.btn_send);

        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            messagesRef = db.getReference("messages");
            usersRef = db.getReference("users");
        } catch (Exception e) {
            dbReady = false;
        // ── Voice init ──
        waveformViewRecording = (WaveformView) findViewById(R.id.waveform_view);
        recordingTimer = (android.widget.TextView) findViewById(R.id.recording_timer);
        recordingBar = (android.view.View) findViewById(R.id.recording_bar);
        initVoiceHelper();
        }
    }

    private void setupSystemBars() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        // التول بار أحمر فاتح والعنوان أسود، فالأيقونات تكون غامقة
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

    // ================= Insets (شريط الحالة + الكيبورد) =================
    private void setupInsets() {
        applyInsets(getStatusBarHeight(), 0);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
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
        root.requestApplyInsets();
    }

    private void applyInsets(int top, int bottom) {
        ViewGroup.LayoutParams tb = toolbar.getLayoutParams();
        tb.height = dp(56) + top;
        toolbar.setLayoutParams(tb);
        toolbar.setPadding(dp(16), top, dp(16), 0);

        root.setPadding(0, 0, 0, bottom);

        if (bottom > lastBottomInset && adapter != null) {
            scrollToBottom();
        }
        lastBottomInset = bottom;
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            result = getResources().getDimensionPixelSize(resId);
        }
        return result;
    }

    // ================= Styles =================
    private void applyStyles() {
        root.setBackgroundColor(COLOR_BG);

        // ---- Toolbar (LTR صريح: الاسم يسار والأفاتار يمين) ----
        toolbar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        float r = dp(24);
        GradientDrawable tbBg = new GradientDrawable();
        tbBg.setColor(COLOR_PRIMARY);
        tbBg.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        toolbar.setBackground(tbBg);
        toolbar.setElevation(dp(6));

        styleAvatarCircle(avatarBtn, myName, 18, COLOR_WHITE, COLOR_BLACK);
        avatarBtn.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        avatarBtn.setElevation(dp(3));
        avatarBtn.setClickable(true);

        title.setText("مجتمع");
        title.setTextColor(COLOR_BLACK);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        title.setTextDirection(View.TEXT_DIRECTION_RTL);

        // ---- Reply bar (LTR صريح: إلغاء يسار، الاقتباس يمين) ----
        replyBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        replyBar.setBackgroundColor(COLOR_BG);
        replyBar.setPadding(dp(12), dp(8), dp(12), dp(0));

        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(COLOR_WHITE);
        infoBg.setCornerRadius(dp(14));
        infoBg.setStroke(dp(1), COLOR_STROKE);
        replyInfo.setBackground(infoBg);
        replyInfo.setPadding(dp(14), dp(8), dp(14), dp(8));

        replyName.setTextColor(COLOR_PRIMARY);
        replyName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        replyName.setTypeface(null, Typeface.BOLD);
        replyName.setGravity(Gravity.RIGHT);
        replyName.setTextDirection(View.TEXT_DIRECTION_RTL);

        replyText.setTextColor(COLOR_LABEL);
        replyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        replyText.setSingleLine(true);
        replyText.setEllipsize(TextUtils.TruncateAt.END);
        replyText.setGravity(Gravity.RIGHT);
        replyText.setTextDirection(View.TEXT_DIRECTION_RTL);

        btnCancelReply.setText("إلغاء");
        btnCancelReply.setTextColor(COLOR_BLACK);
        btnCancelReply.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnCancelReply.setTypeface(null, Typeface.BOLD);
        btnCancelReply.setPadding(dp(12), dp(10), dp(12), dp(10));
        btnCancelReply.setClickable(true);
        btnCancelReply.setBackground(makeRippleNoContent(dp(12)));

        // ---- Input bar (LTR صريح: زر الإرسال يسار والحقل يمين) ----
        inputBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        inputBar.setBackgroundColor(COLOR_BG);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(10));

        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(COLOR_WHITE);
        fieldBg.setCornerRadius(dp(26));
        fieldBg.setStroke(dp(1), COLOR_STROKE);
        editMessage.setBackground(fieldBg);
        editMessage.setElevation(dp(3));
        editMessage.setHint("اكتب رسالتك...");
        editMessage.setHintTextColor(COLOR_HINT);
        editMessage.setTextColor(COLOR_BLACK);
        editMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        editMessage.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editMessage.setMaxLines(4);
        editMessage.setMinHeight(dp(48));
        editMessage.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        editMessage.setTextDirection(View.TEXT_DIRECTION_RTL);
        editMessage.setPadding(dp(18), dp(10), dp(18), dp(10));
        editMessage.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});

        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColor(COLOR_BLACK);
        btnSend.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), sendBg, null));
        btnSend.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btnSend.setPadding(dp(13), dp(13), dp(13), dp(13));
        btnSend.setElevation(dp(4));
        btnSend.setClickable(true);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        sendParams.rightMargin = dp(8);
        btnSend.setLayoutParams(sendParams);
    }

    private void setupList() {
        listMessages.setBackgroundColor(COLOR_BG);
        listMessages.setDivider(null);
        listMessages.setDividerHeight(0);
        listMessages.setCacheColorHint(Color.TRANSPARENT);
        listMessages.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listMessages.setVerticalScrollBarEnabled(false);
        listMessages.setClipToPadding(false);
        listMessages.setPadding(0, dp(14), 0, dp(8));

        adapter = new MessageAdapter();
        listMessages.setAdapter(adapter);
    }

    private void setupListeners() {
        avatarBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditProfileDialog();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasText) {
                    sendMessage();
                } else if (isRecording) {
                    voiceHelper.stopRecording();
                } else {
                    startRecordingIfPermitted();
                }
            }
        });

        btnCancelReply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearReply();
            }
        });

        editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean nowHas = s.toString().trim().length() > 0;
                if (nowHas != hasText) {
                    hasText = nowHas;
                    updateSendState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateSendState() {
        if (hasText) {
            btnSend.setImageResource(R.drawable.icon_send_round);
            btnSend.setAlpha(1f);
        } else {
            btnSend.setImageResource(R.drawable.icon_mic);
            btnSend.setAlpha(1f);
        }
    }

    // ================= Firebase =================
    private void listenToMessages() {
        messagesQuery = messagesRef.limitToLast(MESSAGES_LIMIT);
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                boolean nearBottom = isNearBottom();

                messages.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage m = parseMessage(child);
                    if (m != null) {
                        messages.add(m);
                    }
                }
                adapter.notifyDataSetChanged();

                if (firstLoad || nearBottom || scrollNext) {
                    scrollToBottom();
                }
                firstLoad = false;
                scrollNext = false;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ChatActivity.this,
                        "تعذر تحميل الرسائل", Toast.LENGTH_SHORT).show();
            }
        };
        messagesQuery.addValueEventListener(messagesListener);
    }

    private ChatMessage parseMessage(DataSnapshot child) {
        String text = child.child("text").getValue(String.class);
        if (text == null) {
            return null;
        }
        ChatMessage m = new ChatMessage();
        m.id = child.getKey();
        m.text = text;
        m.senderId = readString(child.child("senderId"));
        m.senderName = readString(child.child("senderName"));

        Long t = child.child("time").getValue(Long.class);
        m.time = (t == null) ? System.currentTimeMillis() : t.longValue();

        if (child.hasChild("replyTo")) {
            DataSnapshot r = child.child("replyTo");
            m.replyId = readString(r.child("id"));
            m.replyName = readString(r.child("name"));
            m.replyText = readString(r.child("text"));
        }
        return m;
    }

    private String readString(DataSnapshot snap) {
        String v = snap.getValue(String.class);
        return (v == null) ? "" : v;
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.length() == 0) {
            return; // لا ترسل رسالة فارغة
        }
        if (!dbReady) {
            Toast.makeText(this, "Firebase Database غير مفعّل", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference newRef = messagesRef.push();

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("senderId", myUid);
        data.put("senderName", myName);
        data.put("text", text);
        data.put("time", ServerValue.TIMESTAMP);

        if (replyTarget != null) {
            Map<String, Object> reply = new HashMap<String, Object>();
            reply.put("id", replyTarget.id);
            reply.put("name", replyTarget.senderName);
            reply.put("text", shorten(replyTarget.text, 100));
            data.put("replyTo", reply);
        }

        scrollNext = true;
        newRef.setValue(data);

        editMessage.setText("");
        clearReply();
    }

    private void deleteMessage(ChatMessage msg) {
        if (!dbReady || msg.id.length() == 0 || !msg.senderId.equals(myUid)) {
            return;
        }
        messagesRef.child(msg.id).removeValue();
    }

    // ================= Reply =================
    private void startReply(ChatMessage msg) {
        replyTarget = msg;
        replyName.setText("الرد على " + msg.senderName);
        replyText.setText(msg.text);
        replyBar.setVisibility(View.VISIBLE);
        editMessage.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editMessage, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void clearReply() {
        replyTarget = null;
        replyBar.setVisibility(View.GONE);
    }

    // ================= List helpers =================
    private boolean isNearBottom() {
        int count = listMessages.getCount();
        if (count == 0) {
            return true;
        }
        return listMessages.getLastVisiblePosition() >= count - 2;
    }

    private void scrollToBottom() {
        listMessages.post(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getCount();
                if (count > 0) {
                    listMessages.setSelection(count - 1);
                }
            }
        });
    }

    // ================= Adapter =================
    private class MessageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return messages.size();
        }

        @Override
        public Object getItem(int position) {
            return messages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return buildMessageView(messages.get(position), position);
        }
    }

    private View buildMessageView(final ChatMessage msg, int position) {
        final boolean mine = msg.senderId.equals(myUid);

        ChatMessage prev = (position > 0) ? messages.get(position - 1) : null;
        boolean first = prev == null
                || !prev.senderId.equals(msg.senderId)
                || (msg.time - prev.time) > GROUP_GAP;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int textMax = (int) (screenW * 0.70f) - dp(24);

        // الصف (LTR صريح: رسائلي يسار، رسائل الآخرين يمين)
        LinearLayout row = new LinearLayout(this);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity((mine ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        row.setPadding(dp(10), first ? dp(8) : dp(2), dp(10), dp(2));

        // Avatar (يظهر أول رسالة في المجموعة فقط)
        TextView avatar = new TextView(this);
        if (mine) {
            styleAvatarCircle(avatar, msg.senderName, 14, COLOR_BLACK, COLOR_WHITE);
        } else {
            styleAvatarCircle(avatar, msg.senderName, 14, COLOR_AVATAR_SOFT, COLOR_BLACK);
        }
        LinearLayout.LayoutParams avParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        avParams.topMargin = dp(2);
        if (mine) {
            avParams.rightMargin = dp(6);
        } else {
            avParams.leftMargin = dp(6);
        }
        avatar.setLayoutParams(avParams);

        final String senderId = msg.senderId;
        final String senderName = msg.senderName;
        View.OnClickListener infoClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUserInfo(senderId, senderName);
            }
        };

        if (first) {
            avatar.setClickable(true);
            avatar.setOnClickListener(infoClick);
        } else {
            avatar.setVisibility(View.INVISIBLE);
        }

        // Bubble
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(6));

        float big = dp(18);
        float small = first ? dp(4) : dp(18);
        float[] radii;
        if (mine) {
            radii = new float[]{small, small, big, big, big, big, big, big};
        } else {
            radii = new float[]{big, big, small, small, big, big, big, big};
        }
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadii(radii);
        if (mine) {
            bubbleBg.setColor(COLOR_PRIMARY);
        } else {
            bubbleBg.setColor(COLOR_WHITE);
            bubbleBg.setStroke(dp(1), COLOR_STROKE);
        }
        bubble.setBackground(bubbleBg);
        bubble.setElevation(dp(1));

        int mainTextColor = mine ? COLOR_WHITE : COLOR_BLACK;

        // اسم المرسل (أول رسالة في المجموعة فقط)
        if (first) {
            TextView nameView = new TextView(this);
            nameView.setText(msg.senderName);
            nameView.setTextColor(mine ? COLOR_BLACK : COLOR_PRIMARY);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setMaxWidth(textMax);
            nameView.setGravity(Gravity.RIGHT);
            nameView.setTextDirection(View.TEXT_DIRECTION_RTL);
            nameView.setOnClickListener(infoClick);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.gravity = Gravity.RIGHT;
            nameParams.bottomMargin = dp(3);
            bubble.addView(nameView, nameParams);
        }

        // اقتباس الرد
        if (msg.hasReply()) {
            LinearLayout quote = new LinearLayout(this);
            quote.setOrientation(LinearLayout.VERTICAL);
            quote.setPadding(dp(10), dp(6), dp(10), dp(6));
            GradientDrawable quoteBg = new GradientDrawable();
            quoteBg.setColor(mine ? Color.parseColor("#33FFFFFF") : COLOR_SOFT);
            quoteBg.setCornerRadius(dp(10));
            quote.setBackground(quoteBg);

            TextView qName = new TextView(this);
            qName.setText(msg.replyName);
            qName.setTextColor(mine ? COLOR_WHITE : COLOR_PRIMARY);
            qName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            qName.setTypeface(null, Typeface.BOLD);
            qName.setSingleLine(true);
            qName.setEllipsize(TextUtils.TruncateAt.END);
            qName.setMaxWidth(textMax - dp(20));
            qName.setGravity(Gravity.RIGHT);
            qName.setTextDirection(View.TEXT_DIRECTION_RTL);
            LinearLayout.LayoutParams qnParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qnParams.gravity = Gravity.RIGHT;
            quote.addView(qName, qnParams);

            TextView qText = new TextView(this);
            qText.setText(msg.replyText);
            qText.setTextColor(mainTextColor);
            qText.setAlpha(0.85f);
            qText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            qText.setMaxLines(2);
            qText.setEllipsize(TextUtils.TruncateAt.END);
            qText.setMaxWidth(textMax - dp(20));
            qText.setGravity(Gravity.RIGHT);
            qText.setTextDirection(View.TEXT_DIRECTION_RTL);
            LinearLayout.LayoutParams qtParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qtParams.gravity = Gravity.RIGHT;
            quote.addView(qText, qtParams);

            LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            quoteParams.gravity = Gravity.RIGHT;
            quoteParams.bottomMargin = dp(6);
            bubble.addView(quote, quoteParams);
        }

        // نص الرسالة
        TextView textView = new TextView(this);
        textView.setText(msg.text);
        textView.setTextColor(mainTextColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setMaxWidth(textMax);
        textView.setGravity(Gravity.RIGHT);
        textView.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.RIGHT;
        bubble.addView(textView, textParams);

        // الوقت
        TextView timeView = new TextView(this);
        timeView.setText(timeFormat.format(new Date(msg.time)));
        timeView.setTextColor(mine ? Color.parseColor("#CCFFFFFF") : COLOR_HINT);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        timeView.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.LEFT;
        timeParams.topMargin = dp(2);
        bubble.addView(timeView, timeParams);

        // الضغط على الرسالة نفسها: رد / حذف
        bubble.setClickable(true);
        bubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMessageOptions(msg, mine);
            }
        });

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (mine) {
            row.addView(avatar);
            row.addView(bubble, bubbleParams);
        } else {
            row.addView(bubble, bubbleParams);
            row.addView(avatar);
        }
        return row;
    }

    // ================= Dialogs =================
    private void showMessageOptions(final ChatMessage msg, boolean mine) {
        LinearLayout card = makeCard();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        final Dialog dialog = buildDialog(card, Gravity.BOTTOM);

        card.addView(makeOptionRow("رد", COLOR_BLACK, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                startReply(msg);
            }
        }));

        if (mine) {
            View divider = new View(this);
            divider.setBackgroundColor(COLOR_DIVIDER);
            LinearLayout.LayoutParams dParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dParams.leftMargin = dp(12);
            dParams.rightMargin = dp(12);
            card.addView(divider, dParams);

            card.addView(makeOptionRow("حذف", COLOR_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    deleteMessage(msg);
                }
            }));
        }
        dialog.show();
    }

    private void showUserInfo(final String uid, String name) {
        LinearLayout card = makeCard();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        final Dialog dialog = buildDialog(card, Gravity.BOTTOM);

        TextView bigAvatar = new TextView(this);
        styleAvatarCircle(bigAvatar, name, 30, COLOR_BLACK, COLOR_WHITE);
        card.addView(bigAvatar, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(COLOR_BLACK);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(12);
        card.addView(nameView, nameParams);

        TextView idLabel = new TextView(this);
        idLabel.setText("معرّف المستخدم");
        idLabel.setTextColor(COLOR_HINT);
        idLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        idLabel.setGravity(Gravity.CENTER);
        idLabel.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(14);
        card.addView(idLabel, labelParams);

        final TextView idView = new TextView(this);
        idView.setText("مخفي");
        idView.setTextColor(Color.parseColor("#6B6B6B"));
        idView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        idView.setGravity(Gravity.CENTER);
        idView.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable idBg = new GradientDrawable();
        idBg.setColor(Color.parseColor("#F4F4F4"));
        idBg.setCornerRadius(dp(12));
        idView.setBackground(idBg);
        LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        idParams.topMargin = dp(6);
        card.addView(idView, idParams);

        if (dbReady && uid.length() > 0) {
            usersRef.child(uid).child("showId")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            Boolean show = snapshot.getValue(Boolean.class);
                            if (show != null && show.booleanValue()) {
                                idView.setTextDirection(View.TEXT_DIRECTION_LTR);
                                idView.setText(uid);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                        }
                    });
        }
        dialog.show();
    }

    private void showEditProfileDialog() {
        LinearLayout card = makeCard();
        final Dialog dialog = buildDialog(card, Gravity.CENTER);

        // الصلاة على النبي
        TextView salawat = new TextView(this);
        salawat.setText("اللهم صل وسلم على سيدنا محمد");
        salawat.setTextColor(COLOR_BLACK);
        salawat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        salawat.setTypeface(null, Typeface.BOLD);
        salawat.setGravity(Gravity.CENTER);
        salawat.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams salParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        salParams.bottomMargin = dp(20);
        card.addView(salawat, salParams);

        // الاسم
        card.addView(makeLabel("الاسم"));
        final EditText nameEdit = new EditText(this);
        nameEdit.setText(myName);
        nameEdit.setSelection(nameEdit.getText().length());
        nameEdit.setSingleLine(true);
        nameEdit.setTextColor(COLOR_BLACK);
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameEdit.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nameEdit.setTextDirection(View.TEXT_DIRECTION_RTL);
        nameEdit.setPadding(dp(16), 0, dp(16), 0);
        nameEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20)});
        GradientDrawable nameBg = new GradientDrawable();
        nameBg.setColor(COLOR_SOFT);
        nameBg.setCornerRadius(dp(14));
        nameBg.setStroke(dp(1), COLOR_STROKE);
        nameEdit.setBackground(nameBg);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        nameParams.topMargin = dp(6);
        nameParams.bottomMargin = dp(16);
        card.addView(nameEdit, nameParams);

        // المعرّف
        card.addView(makeLabel("معرّف المستخدم"));
        LinearLayout idRow = new LinearLayout(this);
        idRow.setOrientation(LinearLayout.HORIZONTAL);
        idRow.setGravity(Gravity.CENTER_VERTICAL);
        idRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        idRow.setPadding(dp(6), 0, dp(16), 0);
        GradientDrawable idBg = new GradientDrawable();
        idBg.setColor(Color.parseColor("#F4F4F4"));
        idBg.setCornerRadius(dp(14));
        idBg.setStroke(dp(1), Color.parseColor("#E2E2E2"));
        idRow.setBackground(idBg);

        final boolean[] publicId = new boolean[]{showIdPublic};
        final String maskedId = "••••••••••••";

        final TextView toggle = new TextView(this);
        toggle.setTextColor(COLOR_PRIMARY);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        toggle.setTypeface(null, Typeface.BOLD);
        toggle.setGravity(Gravity.CENTER);
        toggle.setPadding(dp(14), 0, dp(14), 0);
        toggle.setClickable(true);
        toggle.setBackground(makeRippleNoContent(dp(10)));
        idRow.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));

        final TextView idText = new TextView(this);
        idText.setTextColor(Color.parseColor("#6B6B6B"));
        idText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        idText.setSingleLine(true);
        idText.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        idText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        idText.setTextDirection(View.TEXT_DIRECTION_LTR);
        idRow.addView(idText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Runnable refreshId = new Runnable() {
            @Override
            public void run() {
                idText.setText(publicId[0] ? myUid : maskedId);
                toggle.setText(publicId[0] ? "إخفاء" : "إظهار");
            }
        };
        refreshId.run();

        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publicId[0] = !publicId[0];
                refreshId.run();
            }
        });

        LinearLayout.LayoutParams idRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        idRowParams.topMargin = dp(6);
        card.addView(idRow, idRowParams);

        // ملاحظة المعرّف
        TextView idNote = new TextView(this);
        idNote.setText("عند اختيار إظهار، يظهر المعرّف لأي مستخدم يضغط على حرفك");
        idNote.setTextColor(COLOR_HINT);
        idNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        idNote.setGravity(Gravity.RIGHT);
        idNote.setTextDirection(View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(8);
        noteParams.bottomMargin = dp(22);
        card.addView(idNote, noteParams);

        // زر الحفظ
        TextView saveBtn = makeButton("حفظ التعديلات", COLOR_BLACK, COLOR_WHITE);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = nameEdit.getText().toString().trim();
                if (newName.length() == 0) {
                    Toast.makeText(ChatActivity.this,
                            "الاسم لا يمكن أن يكون فارغًا", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveProfile(newName, publicId[0]);
                hideKeyboard(nameEdit);
                dialog.dismiss();
            }
        });
        card.addView(saveBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        dialog.show();
    }

    private void saveProfile(String newName, boolean showId) {
        myName = newName;
        showIdPublic = showId;
        prefs.edit().putString("name", newName).putBoolean("show_id", showId).apply();

        if (dbReady) {
            Map<String, Object> updates = new HashMap<String, Object>();
            updates.put("name", newName);
            updates.put("showId", showId);
            usersRef.child(myUid).updateChildren(updates);
        }

        avatarBtn.setText(firstLetter(newName));
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "تم حفظ التعديلات", Toast.LENGTH_SHORT).show();
    }

    // ================= UI builders =================
    private Dialog buildDialog(View content, int gravity) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), dp(16), dp(16), dp(16));
        wrap.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrap);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(gravity);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dialog;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_WHITE);
        bg.setCornerRadius(dp(24));
        card.setBackground(bg);
        return card;
    }

    private TextView makeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_LABEL);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.RIGHT);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    private TextView makeButton(String text, int bgColor, int textColor) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setClickable(true);
        btn.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(25));
        btn.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), bg, null));
        return btn;
    }

    private TextView makeOptionRow(String text, int color, View.OnClickListener click) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(color);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setTypeface(null, Typeface.BOLD);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setTextDirection(View.TEXT_DIRECTION_RTL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(makeRippleNoContent(dp(12)));
        row.setOnClickListener(click);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return row;
    }

    private RippleDrawable makeRippleNoContent(int radius) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radius);
        return new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#22000000")), null, mask);
    }

    private void styleAvatarCircle(TextView view, String name, int textSp,
                                   int bgColor, int textColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        view.setBackground(bg);
        view.setText(firstLetter(name));
        view.setTextColor(textColor);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
    }

    // ================= Utils =================
    private String firstLetter(String name) {
        if (name == null) {
            return "م";
        }
        String n = name.trim();
        if (n.length() == 0) {
            return "م";
        }
        return n.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    // ================= Voice =================
    private void initVoiceHelper() {
        voiceHelper = new VoiceRecorderHelper(getCacheDir(), new VoiceRecorderHelper.RecordCallback() {
            @Override
            public void onAmplitude(float rms) {
                if (waveformViewRecording != null) waveformViewRecording.pushAmplitude(rms);
            }
            @Override
            public void onFinished(java.io.File rawFile, String base64, float[] waveSnapshot) {
                stopRecordingUI();
                sendVoiceMessage(base64, waveSnapshot, rawFile);
            }
            @Override
            public void onError(String msg) {
                stopRecordingUI();
                Toast.makeText(ChatActivity.this, "Recording error: " + msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startRecordingIfPermitted() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_MIC);
            return;
        }
        startRecordingUI();
        voiceHelper.startRecording();
    }

    private void startRecordingUI() {
        isRecording = true;
        if (recordingBar != null) recordingBar.setVisibility(View.VISIBLE);
        editMessage.setVisibility(View.GONE);
        if (waveformViewRecording != null) waveformViewRecording.reset();
        btnSend.setImageResource(android.R.drawable.ic_media_pause);
        recordStartMs = System.currentTimeMillis();
        timerHandler.post(timerTick);
    }

    private void stopRecordingUI() {
        isRecording = false;
        timerHandler.removeCallbacks(timerTick);
        if (recordingBar != null) recordingBar.setVisibility(View.GONE);
        editMessage.setVisibility(View.VISIBLE);
        if (recordingTimer != null) recordingTimer.setText("0:00");
        if (waveformViewRecording != null) waveformViewRecording.reset();
        btnSend.setImageResource(R.drawable.icon_mic);
    }

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            long e = System.currentTimeMillis() - recordStartMs;
            if (recordingTimer != null)
                recordingTimer.setText(String.format(Locale.getDefault(), "%d:%02d", e/60000, (e/1000)%60));
            timerHandler.postDelayed(this, 500);
        }
    };

    private void sendVoiceMessage(String base64, float[] wave, java.io.File rawFile) {
        if (!dbReady) return;
        DatabaseReference newRef = messagesRef.push();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("senderId", myUid);
        data.put("senderName", myName);
        data.put("text", "[voice]");
        data.put("voiceBase64", base64);
        data.put("time", ServerValue.TIMESTAMP);
        scrollNext = true;
        newRef.setValue(data);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == PERM_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRecordingUI();
            voiceHelper.startRecording();
        }
    }

}
