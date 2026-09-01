package com.physiquiz.student;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "physiquiz_native";
    private static final String PREF_SITE = "site_url";
    private static final String PREF_AUTH = "auth_token";
    private static final String PREF_CONFIG_JSON = "wp_app_config_json";
    private static final String PREF_CONFIG_TIME = "wp_app_config_time";

    private SharedPreferences prefs;
    private ApiClient api;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRemoteConfig remoteConfig;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private AppConfig config = new AppConfig();
    private int accent = Color.rgb(37, 88, 221);
    private int background = Color.rgb(246, 248, 252);

    private FrameLayout content;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView topTitle;
    private ProgressBar loading;

    private long currentAttemptId = 0;
    private String currentAttemptToken = "";
    private JSONArray currentQuestions = new JSONArray();
    private JSONObject currentAnswers = new JSONObject();
    private JSONObject currentExam = new JSONObject();
    private int currentQuestionIndex = 0;
    private View answerInputView;
    private boolean currentAntiCheat = false;
    private long remainingSeconds = 0;
    private TextView timerText;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String bundled = getString(R.string.default_site_url).trim();
        String site = prefs.getString(PREF_SITE, bundled);
        String token = prefs.getString(PREF_AUTH, "");
        api = new ApiClient(site, token);
        remoteConfig = new AppRemoteConfig(site);

        showBootSplash();
        remoteConfig.load(result -> ui.post(() -> handleRemoteConfig(result, true)));
    }

    private void handleRemoteConfig(AppRemoteConfig.Result result, boolean firstBoot) {
        if (result.success) {
            config = AppConfig.fromJson(result.json);
            prefs.edit().putString(PREF_CONFIG_JSON, result.json.toString()).putLong(PREF_CONFIG_TIME, System.currentTimeMillis()).apply();
            applyTheme();
            bootWithConfig();
            return;
        }
        String cached = prefs.getString(PREF_CONFIG_JSON, "");
        if (!cached.isEmpty()) {
            try {
                config = AppConfig.fromJson(new JSONObject(cached));
                applyTheme();
                bootWithConfig();
                toast("تنظیمات جدید وردپرس دریافت نشد؛ آخرین تنظیمات ذخیره‌شده نمایش داده شد.");
                return;
            } catch (Exception ignored) { }
        }
        showConfigError(result.error, result.status);
    }

    private void showConfigError(String error, int status) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackgroundColor(background);
        TextView h = text("اتصال به تنظیمات PhysiQuiz برقرار نشد", 22, Color.rgb(15,23,42), true);
        h.setGravity(Gravity.CENTER); box.addView(h, matchWrap());
        String detail = "اپلیکیشن نتوانست تنظیمات را از وردپرس دریافت کند." + (status > 0 ? " (HTTP " + status + ")" : "");
        TextView p = bodyText(detail); p.setGravity(Gravity.CENTER); p.setPadding(0,dp(12),0,dp(20)); box.addView(p, matchWrap());
        Button retry = primaryButton("تلاش دوباره"); retry.setOnClickListener(v -> retryBoot()); box.addView(retry, new LinearLayout.LayoutParams(dp(220), dp(58)));
        FrameLayout wrap = new FrameLayout(this); wrap.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); setContentView(wrap);
    }

    private void showBootSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView logo = text("PHYSI QUIZ", 30, accent, true);
        logo.setGravity(Gravity.CENTER);
        box.addView(logo, matchWrap());
        TextView title = text(config.appName == null || config.appName.trim().isEmpty() ? "فیزیکوییز" : config.appName, 20, Color.rgb(15,23,42), true);
        title.setGravity(Gravity.CENTER); title.setPadding(0,dp(12),0,dp(8));
        box.addView(title, matchWrap());
        TextView sub = text("سامانه هوشمند آزمون و یادگیری", 14, Color.rgb(100,116,139), false);
        sub.setGravity(Gravity.CENTER); box.addView(sub, matchWrap());
        ProgressBar pb = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(44),dp(44)); pp.topMargin=dp(28);
        box.addView(pb, pp);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void applyTheme() {
        try { accent = Color.parseColor(config.accentColor); } catch (Exception ignored) {}
        try { background = Color.parseColor(config.backgroundColor); } catch (Exception ignored) {}
        if (config.forceFullscreen) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (config.blockScreenshots) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void bootWithConfig() {
        if (config.loaded && !config.enabled) {
            showBlockingScreen("اپلیکیشن غیرفعال است", "دسترسی به این اپلیکیشن موقتاً از سمت مدیر غیرفعال شده است.", null);
            return;
        }
        if (config.loaded && config.maintenanceMode) {
            showBlockingScreen("در حال بروزرسانی", config.maintenanceMessage, this::retryBoot);
            return;
        }
        if (config.loaded && config.minimumVersionCode > BuildConfig.VERSION_CODE) {
            String msg = config.updateMessage != null && !config.updateMessage.trim().isEmpty()
                    ? config.updateMessage
                    : "نسخه جدیدتری از اپلیکیشن منتشر شده است. لطفاً بروزرسانی کنید.";
            showBlockingScreen("بروزرسانی لازم است", msg, config.updateUrl == null || config.updateUrl.trim().isEmpty()
                    ? null
                    : () -> openExternal(config.updateUrl));
            return;
        }
        buildShell();
        String token = prefs.getString(PREF_AUTH, "");
        if (token.isEmpty()) showLogin(); else showHome();
    }

    private void retryBoot() {
        showBootSplash();
        remoteConfig.load(result -> ui.post(() -> handleRemoteConfig(result, false)));
    }

    private void showBlockingScreen(String title, String message, Runnable onAction) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackgroundColor(background);
        TextView h = text(title, 22, Color.rgb(15, 23, 42), true);
        h.setGravity(Gravity.CENTER);
        box.addView(h, matchWrap());
        TextView p = bodyText(message == null ? "" : message);
        p.setGravity(Gravity.CENTER);
        p.setPadding(0, dp(10), 0, dp(20));
        box.addView(p, matchWrap());
        if (onAction != null) {
            Button b = primaryButton(config.updateUrl != null && !config.updateUrl.isEmpty() && "بروزرسانی لازم است".equals(title) ? "دریافت نسخه جدید" : "تلاش دوباره");
            b.setOnClickListener(v -> onAction.run());
            box.addView(b, new LinearLayout.LayoutParams(dp(220), dp(58)));
        }
        if (config.supportUrl != null && !config.supportUrl.trim().isEmpty()) {
            Button support = secondaryButton("تماس با پشتیبانی");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(220), dp(50));
            lp.topMargin = dp(10);
            support.setOnClickListener(v -> openExternal(config.supportUrl));
            box.addView(support, lp);
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(wrap);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(background);

        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        topBar.setBackgroundColor(Color.WHITE);
        topBar.setElevation(dp(2));
        topTitle = text(config.appName + "  •", 19, Color.rgb(15, 23, 42), true);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button refresh = smallButton("↻");
        refresh.setOnClickListener(v -> refreshCurrent());
        topBar.addView(refresh, new LinearLayout.LayoutParams(dp(48), dp(44)));
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomBar = new LinearLayout(this);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(6), dp(8), dp(6), dp(10));
        bottomBar.setBackgroundColor(Color.WHITE);
        bottomBar.setElevation(dp(4));
        addNav("⌂", "خانه", this::showHome);
        addNav("✓", "آزمون‌ها", this::showExams);
        addNav("◔", "نتایج", this::showResults);
        addNav("▣", "فایل‌ها", this::showFiles);
        addNav("●", "پروفایل", this::showProfile);
        root.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout overlay = new FrameLayout(this);
        overlay.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(50), dp(50));
        lp.gravity = Gravity.CENTER;
        overlay.addView(loading, lp);
        setContentView(overlay);
    }

    private void addNav(String icon, String label, Runnable action) {
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER);
        TextView iconView = text(icon, 17, Color.rgb(51, 65, 85), false);
        iconView.setGravity(Gravity.CENTER);
        col.addView(iconView, matchWrap());
        TextView labelView = text(label, 11, Color.rgb(51, 65, 85), false);
        labelView.setGravity(Gravity.CENTER);
        col.addView(labelView, matchWrap());
        col.setOnClickListener(v -> action.run());
        col.setPadding(dp(2), dp(4), dp(2), dp(2));
        bottomBar.addView(col, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    private void refreshCurrent() {
        remoteConfig.load(result -> ui.post(() -> {
            if (result.success) {
                config = AppConfig.fromJson(result.json);
                prefs.edit().putString(PREF_CONFIG_JSON, result.json.toString()).putLong(PREF_CONFIG_TIME, System.currentTimeMillis()).apply();
                applyTheme();
                toast("تنظیمات جدید از وردپرس اعمال شد.");
            } else {
                toast("دریافت تنظیمات وردپرس ناموفق بود.");
            }
            refreshSectionOnly();
        }));
    }

    private void refreshSectionOnly() {
        CharSequence t = topTitle.getText();
        if ("آزمون‌ها".contentEquals(t)) showExams();
        else if ("نتایج".contentEquals(t)) showResults();
        else if ("فایل‌ها".contentEquals(t)) showFiles();
        else if ("پروفایل".contentEquals(t)) showProfile();
        else showHome();
    }

    private void showLogin() {
        stopTimer();
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (config.blockScreenshots) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(28), dp(20), dp(28));
        page.setBackgroundColor(background);

        LinearLayout brand = card();
        GradientDrawable brandBg = new GradientDrawable();
        brandBg.setColor(accent); brandBg.setCornerRadius(dp(28));
        brand.setBackground(brandBg); brand.setPadding(dp(22),dp(24),dp(22),dp(24));
        TextView badge = text("PHYSIQUIZ", 24, Color.WHITE, true); badge.setGravity(Gravity.CENTER);
        brand.addView(badge, matchWrap());
        TextView brandSub = text("آموزش • آزمون • پیشرفت", 13, Color.WHITE, false); brandSub.setGravity(Gravity.CENTER); brandSub.setPadding(0,dp(8),0,0);
        brand.addView(brandSub, matchWrap());
        page.addView(brand, matchWrapMargin(0,18));

        TextView h = text("خوش آمدی 👋", 28, Color.rgb(15,23,42), true);
        page.addView(h, matchWrap());
        TextView p = text("برای ورود به داشبورد آموزشی و آزمون‌های خود، اطلاعات حسابت را وارد کن.", 14, Color.rgb(100,116,139), false);
        p.setPadding(0,dp(8),0,dp(18)); page.addView(p, matchWrap());

        LinearLayout form = card(); form.setPadding(dp(18),dp(18),dp(18),dp(18));
        TextView ulabel=text("نام کاربری یا ایمیل",13,Color.rgb(51,65,85),true); form.addView(ulabel,matchWrap());
        EditText username=input("مثلاً student@example.com"); username.setSingleLine(true); form.addView(username,matchWrapMargin(0,8));
        TextView plabel=text("رمز عبور",13,Color.rgb(51,65,85),true); plabel.setPadding(0,dp(14),0,0); form.addView(plabel,matchWrap());
        EditText password=input("رمز عبور خود را وارد کنید"); password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); password.setSingleLine(true); form.addView(password,matchWrapMargin(0,8));
        TextView forgot=text("رمز عبور را فراموش کرده‌ام",13,accent,true); forgot.setGravity(Gravity.RIGHT); forgot.setPadding(0,dp(10),0,dp(10)); forgot.setOnClickListener(v->showForgotPasswordDialog(username.getText().toString().trim())); form.addView(forgot,matchWrap());
        Button login=primaryButton("ورود به حساب ←"); form.addView(login,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));
        page.addView(form,matchWrapMargin(0,18));
        TextView foot=text("حساب کاربری شما مستقیماً از PhysiQuiz مدیریت می‌شود.",12,Color.rgb(100,116,139),false); foot.setGravity(Gravity.CENTER); page.addView(foot,matchWrap());

        login.setOnClickListener(v -> {
            if(username.getText().toString().trim().isEmpty()||password.getText().toString().isEmpty()){toast("نام کاربری و رمز عبور را وارد کنید.");return;}
            login.setEnabled(false); login.setText("در حال ورود...");
            runApi(() -> { JSONObject body=new JSONObject(); body.put("username",username.getText().toString().trim()); body.put("password",password.getText().toString()); body.put("device","Android "+android.os.Build.VERSION.RELEASE+" / "+android.os.Build.MODEL); return api.post("/wp-json/physiquiz/v1/mobile/login",body); }, json -> {
                login.setEnabled(true); login.setText("ورود به حساب ←");
                String token=json.optString("token",""); if(token.isEmpty()){toast("توکن ورود از سرور دریافت نشد.");return;}
                prefs.edit().putString(PREF_SITE,api.getBaseUrl()).putString(PREF_AUTH,token).apply(); api.setAuthToken(token); topBar.setVisibility(View.VISIBLE); bottomBar.setVisibility(View.VISIBLE); showHome();
            });
        });
        scroll.addView(page); setScreen(scroll);
    }

    private void showForgotPasswordDialog(String prefill) {
        EditText field = input("نام کاربری یا ایمیل");
        if (prefill != null && !prefill.isEmpty()) field.setText(prefill);
        field.setSingleLine(true);
        LinearLayout wrap = column();
        wrap.setPadding(dp(20), dp(10), dp(20), dp(0));
        wrap.addView(field, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("بازیابی رمز عبور")
                .setMessage("نام کاربری یا ایمیل حسابت را وارد کن؛ لینک تعیین رمز جدید برایت ایمیل می‌شود.")
                .setView(wrap)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ارسال لینک", (d, w) -> {
                    String loginValue = field.getText().toString().trim();
                    if (loginValue.isEmpty()) { toast("نام کاربری یا ایمیل را وارد کن."); return; }
                    runApi(() -> {
                        JSONObject body = new JSONObject();
                        body.put("login", loginValue);
                        return api.post("/wp-json/physiquiz/v1/mobile/forgot-password", body);
                    }, json -> showMessage("بازیابی رمز عبور", json.optString("message", "اگر این حساب وجود داشته باشد، ایمیل بازیابی ارسال شد.")));
                })
                .show();
    }

    private void showHome() {
        prepareSection("خانه");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/home"), json -> {
            LinearLayout page = pageColumn();
            JSONObject user = json.optJSONObject("user");
            JSONObject stats = json.optJSONObject("stats");
            String name = user == null ? "دانش‌آموز" : user.optString("display_name", "دانش‌آموز");

            if (config.bannerUrl != null && !config.bannerUrl.trim().isEmpty()) {
                page.addView(bannerImage(config.bannerUrl), matchWrapMargin(0, 14));
            }

            page.addView(hero("سلام " + name + " 👋", "امروز یک قدم دیگر به تسلط بر فیزیک نزدیک‌تر شو.", name), matchWrapMargin(0, 14));

            if (config.cards != null && config.cards.length() > 0) {
                for (int i = 0; i < config.cards.length(); i++) {
                    addContentCard(page, config.cards.optJSONObject(i));
                }
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(statCard("تلاش‌ها", stats == null ? "0" : String.valueOf(stats.optInt("attempts"))), weighted());
            row.addView(statCard("بهترین", stats == null ? "0%" : fmt(stats.optDouble("best_percent")) + "%"), weightedMargin(8));
            page.addView(row, matchWrapMargin(0, 8));
            LinearLayout row2 = new LinearLayout(this);
            row2.setOrientation(LinearLayout.HORIZONTAL);
            row2.addView(statCard("سطح", stats == null ? "1" : String.valueOf(stats.optInt("level", 1))), weighted());
            row2.addView(statCard("XP", stats == null ? "0" : String.valueOf(stats.optInt("xp"))), weightedMargin(8));
            page.addView(row2, matchWrapMargin(0, 18));

            page.addView(sectionTitle("آزمون‌های فعال"), matchWrap());
            JSONArray active = json.optJSONArray("active_exams");
            if (active == null || active.length() == 0) page.addView(empty("در حال حاضر آزمون فعالی نداری."), matchWrapMargin(0, 10));
            else for (int i = 0; i < active.length(); i++) addExamCard(page, active.optJSONObject(i));

            page.addView(sectionTitle("آخرین نتایج"), matchWrapMargin(0, 8));
            JSONArray results = json.optJSONArray("recent_results");
            if (results == null || results.length() == 0) page.addView(empty("هنوز نتیجه‌ای ثبت نشده است."), matchWrapMargin(0, 10));
            else for (int i = 0; i < results.length(); i++) addResultCard(page, results.optJSONObject(i));

            Button files = secondaryButton("مشاهده فایل‌ها و منابع");
            files.setOnClickListener(v -> showFiles());
            page.addView(files, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
            setScrollable(page);
        });
    }

    /** Renders one WordPress-managed content card: image (optional) + title + short text, tappable if it has a link. */
    private void addContentCard(LinearLayout page, JSONObject cardJson) {
        if (cardJson == null) return;
        String title = cardJson.optString("title", "");
        String imageUrl = cardJson.optString("image", "");
        String cardText = cardJson.optString("text", "");
        String link = cardJson.optString("link", "");
        if (title.isEmpty() && imageUrl.isEmpty() && cardText.isEmpty()) return;

        LinearLayout card = card();
        if (!imageUrl.isEmpty()) {
            FrameLayout imgWrap = new FrameLayout(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(230, 233, 240));
            bg.setCornerRadius(dp(12));
            imgWrap.setBackground(bg);
            imgWrap.setClipToOutline(true);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgWrap.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            loadImageInto(imageUrl, iv);
            card.addView(imgWrap, matchWrapHeightMargin(110, 0, 10));
        }
        if (!title.isEmpty()) card.addView(text(title, 16, Color.rgb(15, 23, 42), true), matchWrap());
        if (!cardText.isEmpty()) {
            TextView t = bodyText(cardText);
            t.setPadding(0, dp(4), 0, 0);
            card.addView(t, matchWrap());
        }
        if (!link.isEmpty()) {
            card.setOnClickListener(v -> openExternal(link));
        }
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showExams() {
        prepareSection("آزمون‌ها");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/exams"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("آزمون‌های من", "فهرست آزمون‌های مجاز بر اساس پایه، گروه، خرید و محدودیت‌های تعریف‌شده در وردپرس."), matchWrapMargin(0, 16));
            JSONArray exams = json.optJSONArray("exams");
            if (exams == null || exams.length() == 0) page.addView(empty("آزمونی برای حساب شما در دسترس نیست."), matchWrap());
            else for (int i = 0; i < exams.length(); i++) addExamCard(page, exams.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addExamCard(LinearLayout page, JSONObject exam) {
        if (exam == null) return;
        LinearLayout card = card();
        TextView title = text(exam.optString("title", "آزمون"), 17, Color.rgb(15, 23, 42), true);
        card.addView(title, matchWrap());
        String state = exam.optString("state", "active");
        String stateFa = "active".equals(state) ? "آماده شروع" : ("upcoming".equals(state) ? "به‌زودی" : "پایان‌یافته");
        TextView meta = text(stateFa + "  •  " + exam.optInt("duration_minutes", 0) + " دقیقه  •  حدنصاب " + fmt(exam.optDouble("pass_percent")) + "%", 13, Color.rgb(71, 85, 105), false);
        meta.setPadding(0, dp(6), 0, dp(10));
        card.addView(meta, matchWrap());
        if (!exam.optString("available_from", "").isEmpty()) card.addView(text("شروع: " + exam.optString("available_from"), 12, Color.rgb(100, 116, 139), false), matchWrap());
        if (!exam.optString("available_until", "").isEmpty()) card.addView(text("پایان: " + exam.optString("available_until"), 12, Color.rgb(100, 116, 139), false), matchWrap());
        Button open = "ended".equals(state) ? secondaryButton("مشاهده جزئیات") : primaryButton("باز کردن آزمون");
        open.setEnabled(!"ended".equals(state));
        open.setOnClickListener(v -> showExamDetail(exam.optInt("id")));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bp.topMargin = dp(12);
        card.addView(open, bp);
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showExamDetail(int examId) {
        prepareSection("جزئیات آزمون");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/exams/" + examId), json -> {
            JSONObject exam = json.optJSONObject("exam");
            if (exam == null) { showErrorScreen("اطلاعات آزمون دریافت نشد."); return; }
            LinearLayout page = pageColumn();
            page.addView(sectionIntro(exam.optString("title", "آزمون"), exam.optString("description", "")), matchWrapMargin(0, 12));
            page.addView(infoLine("زمان آزمون", exam.optInt("duration_minutes") + " دقیقه"), matchWrapMargin(0, 6));
            page.addView(infoLine("حدنصاب قبولی", fmt(exam.optDouble("pass_percent")) + "%"), matchWrapMargin(0, 6));
            page.addView(infoLine("نوع آزمون", "pdf".equals(exam.optString("mode")) ? "PDF + پاسخ‌برگ" : "بانک سؤال"), matchWrapMargin(0, 12));

            JSONObject warm = exam.optJSONObject("warmup");
            CheckBox ack = new CheckBox(this);
            if (warm != null && warm.optBoolean("enabled")) {
                page.addView(sectionTitle(warm.optString("title", "گرم‌کن آزمون")), matchWrapMargin(0, 6));
                String message = warm.optString("message", "");
                if (!message.isEmpty()) page.addView(bodyText(message), matchWrapMargin(0, 8));
                String advisor = warm.optString("advisor", "");
                if (!advisor.isEmpty()) page.addView(note(warm.optString("advisor_title", "توصیه") + "\n" + advisor), matchWrapMargin(0, 8));
                JSONArray rules = warm.optJSONArray("rules");
                if (rules != null && rules.length() > 0) {
                    StringBuilder rb = new StringBuilder();
                    for (int i = 0; i < rules.length(); i++) rb.append("• ").append(rules.optString(i)).append('\n');
                    page.addView(note(rb.toString().trim()), matchWrapMargin(0, 8));
                }
                JSONArray wf = warm.optJSONArray("files");
                if (wf != null) for (int i = 0; i < wf.length(); i++) addFileRow(page, wf.optJSONObject(i));
                String video = warm.optString("video_url", "");
                if (!video.isEmpty()) {
                    Button vb = secondaryButton("باز کردن ویدیوی آماده‌سازی");
                    vb.setOnClickListener(v -> openExternal(video));
                    page.addView(vb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
                }
                if (warm.optBoolean("require_ack")) {
                    ack.setText(warm.optString("ack_text", "قوانین را خواندم و آماده‌ام."));
                    ack.setTextSize(13);
                    page.addView(ack, matchWrapMargin(0, 10));
                } else ack.setChecked(true);
            } else ack.setChecked(true);

            EditText accessCode = input("کد دسترسی (اگر آزمون کد دارد)");
            if (exam.optBoolean("requires_access_code")) page.addView(accessCode, matchWrapMargin(0, 10));

            Button start = primaryButton("شروع آزمون");
            start.setEnabled("active".equals(exam.optString("state", "active")));
            start.setOnClickListener(v -> {
                if (!ack.isChecked()) { toast("ابتدا تأیید مطالعه قوانین را فعال کنید."); return; }
                startExam(examId, accessCode.getText().toString().trim());
            });
            page.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
            setScrollable(page);
        });
    }

    private void startExam(int examId, String accessCode) {
        runApi(() -> {
            JSONObject body = new JSONObject();
            if (!accessCode.isEmpty()) body.put("access_code", accessCode);
            body.put("surface", "android_native");
            return api.post("/wp-json/physiquiz/v1/exams/" + examId + "/start", body);
        }, json -> {
            currentAttemptId = json.optLong("attempt_id", 0);
            currentAttemptToken = json.optString("token", "");
            currentExam = json.optJSONObject("exam") == null ? new JSONObject() : json.optJSONObject("exam");
            currentQuestions = json.optJSONArray("questions") == null ? new JSONArray() : json.optJSONArray("questions");
            currentAnswers = new JSONObject();
            currentQuestionIndex = 0;
            currentAntiCheat = currentExam.optBoolean("anti_cheat", false);
            remainingSeconds = Math.max(0, currentExam.optInt("duration_minutes", 0) * 60L);
            if (currentAntiCheat || config.blockScreenshots) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            showQuestion();
            startTimer();
        });
    }

    private void showQuestion() {
        prepareSection("در حال آزمون");
        if (currentQuestions.length() == 0) { showErrorScreen("سؤالی برای این آزمون دریافت نشد."); return; }
        currentQuestionIndex = Math.max(0, Math.min(currentQuestionIndex, currentQuestions.length() - 1));
        JSONObject q = currentQuestions.optJSONObject(currentQuestionIndex);
        if (q == null) return;

        LinearLayout page = pageColumn();
        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        TextView pos = text("سؤال " + (currentQuestionIndex + 1) + " از " + currentQuestions.length(), 14, Color.rgb(71, 85, 105), true);
        timerText = text(timeText(remainingSeconds), 14, Color.rgb(220, 38, 38), true);
        timerText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        status.addView(pos, weighted());
        status.addView(timerText, weighted());
        page.addView(status, matchWrapMargin(0, 10));

        if ("pdf".equals(currentExam.optString("mode")) && !currentExam.optString("pdf_url", "").isEmpty()) {
            Button pdf = secondaryButton("مشاهده فایل PDF سؤال‌ها");
            pdf.setOnClickListener(v -> openPdf(currentExam.optString("pdf_url")));
            page.addView(pdf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }

        LinearLayout qCard = card();
        String title = q.optString("title", "");
        if (!title.isEmpty()) qCard.addView(text(title, 16, Color.rgb(15, 23, 42), true), matchWrapMargin(0, 6));
        TextView contentText = bodyText(htmlToText(q.optString("content", "")));
        qCard.addView(contentText, matchWrapMargin(0, 12));
        qCard.addView(text("امتیاز: " + fmt(q.optDouble("points", 0)), 12, Color.rgb(100, 116, 139), false), matchWrapMargin(0, 8));

        String type = q.optString("type", "single");
        Object saved = currentAnswers.opt(String.valueOf(q.optInt("id")));
        if ("single".equals(type) || "true_false".equals(type)) {
            RadioGroup group = new RadioGroup(this);
            group.setOrientation(LinearLayout.VERTICAL);
            JSONArray options = q.optJSONArray("options");
            if (options != null) for (int i = 0; i < options.length(); i++) {
                String value = options.optString(i);
                RadioButton rb = new RadioButton(this);
                rb.setText(value);
                rb.setTextSize(15);
                rb.setTag(value);
                rb.setPadding(dp(4), dp(7), dp(4), dp(7));
                if (saved instanceof String && value.equals(saved)) rb.setChecked(true);
                group.addView(rb, matchWrap());
            }
            answerInputView = group;
            qCard.addView(group, matchWrap());
        } else if ("multiple".equals(type)) {
            LinearLayout group = column();
            JSONArray options = q.optJSONArray("options");
            JSONArray savedArray = saved instanceof JSONArray ? (JSONArray) saved : null;
            if (options != null) for (int i = 0; i < options.length(); i++) {
                String value = options.optString(i);
                CheckBox cb = new CheckBox(this);
                cb.setText(value);
                cb.setTextSize(15);
                cb.setTag(value);
                cb.setChecked(arrayContains(savedArray, value));
                group.addView(cb, matchWrap());
            }
            answerInputView = group;
            qCard.addView(group, matchWrap());
        } else {
            EditText answer = input("پاسخ شما");
            if ("number".equals(type)) answer.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            else if ("essay".equals(type)) { answer.setMinLines(5); answer.setGravity(Gravity.TOP | Gravity.RIGHT); }
            if (saved instanceof String) answer.setText((String) saved);
            answerInputView = answer;
            qCard.addView(answer, matchWrap());
        }
        page.addView(qCard, matchWrapMargin(0, 12));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev = secondaryButton("قبلی");
        prev.setEnabled(currentQuestionIndex > 0);
        prev.setOnClickListener(v -> saveCurrentAnswerThen(() -> { currentQuestionIndex--; showQuestion(); }));
        Button next = primaryButton(currentQuestionIndex == currentQuestions.length() - 1 ? "ثبت نهایی" : "بعدی");
        next.setOnClickListener(v -> saveCurrentAnswerThen(() -> {
            if (currentQuestionIndex == currentQuestions.length() - 1) confirmSubmit();
            else { currentQuestionIndex++; showQuestion(); }
        }));
        nav.addView(prev, weighted());
        nav.addView(next, weightedMargin(8));
        page.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setScrollable(page);
    }

    private void saveCurrentAnswerThen(Runnable done) {
        if (currentAttemptId <= 0 || currentQuestions.length() == 0) { done.run(); return; }
        JSONObject q = currentQuestions.optJSONObject(currentQuestionIndex);
        if (q == null) { done.run(); return; }
        Object value = collectAnswer(q.optString("type", "single"));
        try { currentAnswers.put(String.valueOf(q.optInt("id")), value); } catch (Exception ignored) {}
        runApi(() -> {
            JSONObject body = new JSONObject();
            body.put("question_id", q.optInt("id"));
            body.put("answer", value);
            return api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/answer", body, currentAttemptToken);
        }, json -> done.run());
    }

    private Object collectAnswer(String type) {
        if (answerInputView instanceof RadioGroup) {
            RadioGroup g = (RadioGroup) answerInputView;
            int id = g.getCheckedRadioButtonId();
            if (id == -1) return "";
            View v = g.findViewById(id);
            return v != null && v.getTag() != null ? v.getTag().toString() : "";
        }
        if ("multiple".equals(type) && answerInputView instanceof LinearLayout) {
            JSONArray a = new JSONArray();
            LinearLayout g = (LinearLayout) answerInputView;
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (v instanceof CheckBox && ((CheckBox) v).isChecked() && v.getTag() != null) a.put(v.getTag().toString());
            }
            return a;
        }
        if (answerInputView instanceof EditText) return ((EditText) answerInputView).getText().toString().trim();
        return "";
    }

    private void confirmSubmit() {
        new AlertDialog.Builder(this)
                .setTitle("ثبت نهایی آزمون")
                .setMessage("بعد از ثبت نهایی امکان تغییر پاسخ‌ها وجود ندارد. آزمون ثبت شود؟")
                .setNegativeButton("ادامه آزمون", null)
                .setPositiveButton("ثبت نهایی", (d, w) -> submitAttempt())
                .show();
    }

    private void submitAttempt() {
        stopTimer();
        runApi(() -> api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/submit", new JSONObject(), currentAttemptToken), this::showFinalResult);
    }

    private void showFinalResult(JSONObject result) {
        stopTimer();
        if (!config.blockScreenshots) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        currentAttemptId = 0;
        currentAttemptToken = "";
        prepareSection("نتیجه آزمون");
        LinearLayout page = pageColumn();
        boolean passed = result.optBoolean("passed");
        String percent = fmt(result.optDouble("percent")) + "%";
        page.addView(hero(passed ? "قبول شدی" : "نتیجه ثبت شد", "درصد نهایی: " + percent), matchWrapMargin(0, 14));
        page.addView(infoLine("امتیاز", fmt(result.optDouble("score")) + " از " + fmt(result.optDouble("max_score"))), matchWrapMargin(0, 6));
        page.addView(infoLine("صحیح", String.valueOf(result.optInt("correct_count"))), matchWrapMargin(0, 6));
        page.addView(infoLine("غلط", String.valueOf(result.optInt("wrong_count"))), matchWrapMargin(0, 6));
        page.addView(infoLine("بی‌پاسخ", String.valueOf(result.optInt("unanswered_count"))), matchWrapMargin(0, 12));
        Button results = primaryButton("رفتن به کارنامه‌ها");
        results.setOnClickListener(v -> showResults());
        page.addView(results, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        setScrollable(page);
    }

    private void showResults() {
        prepareSection("نتایج");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/results"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("کارنامه‌های من", "نتایج ثبت‌شده مستقیماً از داده‌های " + config.appName + " دریافت می‌شوند."), matchWrapMargin(0, 16));
            JSONArray rows = json.optJSONArray("results");
            if (rows == null || rows.length() == 0) page.addView(empty("هنوز نتیجه‌ای ثبت نشده است."), matchWrap());
            else for (int i = 0; i < rows.length(); i++) addResultCard(page, rows.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addResultCard(LinearLayout page, JSONObject r) {
        if (r == null) return;
        LinearLayout card = card();
        card.addView(text(r.optString("exam_title", "آزمون"), 16, Color.rgb(15, 23, 42), true), matchWrap());
        String result = (r.optBoolean("passed") ? "قبول" : "ثبت‌شده") + "  •  " + fmt(r.optDouble("percent")) + "%";
        card.addView(text(result, 14, r.optBoolean("passed") ? Color.rgb(22, 163, 74) : Color.rgb(71, 85, 105), true), matchWrapMargin(0, 4));
        card.addView(text(r.optString("submitted_at", ""), 12, Color.rgb(100, 116, 139), false), matchWrap());
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showFiles() {
        prepareSection("فایل‌ها");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/files"), json -> {
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("منابع و فایل‌ها", "فایل‌هایی که مدیر در اتاق آزمون وردپرس قرار داده است."), matchWrapMargin(0, 16));
            JSONArray files = json.optJSONArray("files");
            if (files == null || files.length() == 0) page.addView(empty("هنوز فایلی برای این بخش ثبت نشده است."), matchWrap());
            else for (int i = 0; i < files.length(); i++) addFileRow(page, files.optJSONObject(i));
            setScrollable(page);
        });
    }

    private void addFileRow(LinearLayout page, JSONObject file) {
        if (file == null) return;
        String url = file.optString("url", "");
        LinearLayout card = card();
        card.addView(text(file.optString("label", "فایل"), 15, Color.rgb(15, 23, 42), true), matchWrap());
        card.addView(text(file.optString("type", "LINK"), 12, Color.rgb(100, 116, 139), false), matchWrapMargin(0, 8));
        Button open = secondaryButton("مشاهده / دانلود");
        open.setOnClickListener(v -> { if (url.toLowerCase(Locale.US).contains(".pdf")) openPdf(url); else openExternal(url); });
        card.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        page.addView(card, matchWrapMargin(0, 10));
    }

    private void showProfile() {
        prepareSection("پروفایل");
        runApi(() -> api.get("/wp-json/physiquiz/v1/mobile/me"), json -> {
            JSONObject user = json.optJSONObject("user");
            if (user == null) { showErrorScreen("اطلاعات پروفایل دریافت نشد."); return; }
            LinearLayout page = pageColumn();
            page.addView(sectionIntro("اطلاعات دانش‌آموز", user.optString("email", "")), matchWrapMargin(0, 16));
            EditText first = input("نام"); first.setText(user.optString("first_name", "")); page.addView(first, matchWrapMargin(0, 8));
            EditText last = input("نام خانوادگی"); last.setText(user.optString("last_name", "")); page.addView(last, matchWrapMargin(0, 8));
            EditText display = input("نام نمایشی"); display.setText(user.optString("display_name", "")); page.addView(display, matchWrapMargin(0, 8));
            EditText mobile = input("شماره تماس"); mobile.setText(user.optString("mobile", "")); mobile.setInputType(InputType.TYPE_CLASS_PHONE); page.addView(mobile, matchWrapMargin(0, 12));
            JSONObject ac = user.optJSONObject("academic");
            if (ac != null) {
                page.addView(infoLine("پایه تحصیلی", ac.optString("grade_label", "ثبت نشده")), matchWrapMargin(0, 6));
                page.addView(infoLine("کلاس", ac.optString("class_name", "ثبت نشده")), matchWrapMargin(0, 6));
                page.addView(infoLine("مدرسه", ac.optString("school_name", "ثبت نشده")), matchWrapMargin(0, 12));
            }
            Button save = primaryButton("ذخیره تغییرات");
            save.setOnClickListener(v -> runApi(() -> {
                JSONObject body = new JSONObject();
                body.put("first_name", first.getText().toString().trim());
                body.put("last_name", last.getText().toString().trim());
                body.put("display_name", display.getText().toString().trim());
                body.put("mobile", mobile.getText().toString().trim());
                return api.post("/wp-json/physiquiz/v1/mobile/profile", body);
            }, out -> toast("پروفایل ذخیره شد.")));
            page.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            if (config.supportUrl != null && !config.supportUrl.trim().isEmpty()) {
                Button support = secondaryButton("تماس با پشتیبانی");
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
                slp.topMargin = dp(12);
                support.setOnClickListener(v -> openExternal(config.supportUrl));
                page.addView(support, slp);
            }
            Button logout = secondaryButton("خروج از حساب");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)); lp.topMargin = dp(12);
            page.addView(logout, lp);
            logout.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("خروج از حساب").setMessage("از حساب " + config.appName + " خارج می‌شوی؟").setNegativeButton("خیر", null).setPositiveButton("خروج", (d, w) -> doLogout()).show());
            setScrollable(page);
        });
    }

    private void doLogout() {
        runApi(() -> api.post("/wp-json/physiquiz/v1/mobile/logout", new JSONObject()), json -> clearSessionAndLogin());
    }

    private void clearSessionAndLogin() {
        prefs.edit().remove(PREF_AUTH).apply();
        api.setAuthToken("");
        currentAttemptId = 0;
        currentAttemptToken = "";
        stopTimer();
        if (!config.blockScreenshots) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        showLogin();
    }

    private void prepareSection(String title) {
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        topTitle.setText(title);
        setScreen(empty("در حال دریافت اطلاعات…"));
    }

    private void showErrorScreen(String message) {
        LinearLayout page = pageColumn();
        page.addView(empty(message), matchWrapMargin(0, 12));
        Button retry = secondaryButton("تلاش دوباره");
        retry.setOnClickListener(v -> refreshCurrent());
        page.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        setScrollable(page);
    }

    private interface JsonCall { JSONObject run() throws Exception; }
    private interface JsonSuccess { void run(JSONObject json); }

    private void runApi(JsonCall call, JsonSuccess success) {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                JSONObject json = call.run();
                ui.post(() -> { loading.setVisibility(View.GONE); success.run(json); });
            } catch (ApiClient.ApiException e) {
                ui.post(() -> {
                    loading.setVisibility(View.GONE);
                    if (e.status == 401 && !prefs.getString(PREF_AUTH, "").isEmpty()) {
                        toast("نشست ورود منقضی شده است. دوباره وارد شوید.");
                        clearSessionAndLogin();
                    } else showMessage("خطا", e.getMessage());
                });
            } catch (Exception e) {
                ui.post(() -> { loading.setVisibility(View.GONE); showMessage("خطای اتصال", "ارتباط با REST API برقرار نشد. اینترنت، SSL و فعال بودن افزونه را بررسی کنید."); });
            }
        });
    }

    private void openPdf(String url) {
        if (url == null || !url.startsWith("https://")) { toast("آدرس PDF معتبر نیست."); return; }
        Intent intent = new Intent(this, PdfActivity.class);
        intent.putExtra(PdfActivity.EXTRA_URL, url);
        intent.putExtra(PdfActivity.EXTRA_SECURE, currentAntiCheat || config.blockScreenshots);
        intent.putExtra(PdfActivity.EXTRA_FULLSCREEN, false);
        intent.putExtra(PdfActivity.EXTRA_AUTH, prefs.getString(PREF_AUTH, ""));
        startActivity(intent);
    }

    private void openExternal(String url) {
        if (!config.allowExternalLinks) { toast("باز کردن لینک‌های خارجی توسط مدیر غیرفعال شده است."); return; }
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) { toast("فقط لینک HTTPS مجاز است."); return; }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) { toast("این لینک قابل باز شدن نیست."); }
    }

    private void startTimer() {
        stopTimer();
        if (currentExam.optInt("duration_minutes", 0) <= 0) return;
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (remainingSeconds > 0) remainingSeconds--;
                if (timerText != null) timerText.setText(timeText(remainingSeconds));
                if (remainingSeconds <= 0) {
                    stopTimer();
                    toast("زمان آزمون تمام شد؛ پاسخ‌ها ثبت نهایی می‌شوند.");
                    submitAttempt();
                    return;
                }
                ui.postDelayed(this, 1000);
            }
        };
        ui.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerRunnable != null) ui.removeCallbacks(timerRunnable);
        timerRunnable = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentAttemptId > 0 && currentAntiCheat) {
            io.execute(() -> {
                try {
                    JSONObject b = new JSONObject();
                    b.put("type", "visibility_change");
                    JSONObject data = new JSONObject(); data.put("state", "background"); b.put("data", data);
                    api.postAttempt("/wp-json/physiquiz/v1/attempts/" + currentAttemptId + "/log", b, currentAttemptToken);
                } catch (Exception ignored) {}
            });
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        io.shutdownNow();
        super.onDestroy();
    }

    private void setScrollable(LinearLayout page) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setScreen(scroll);
    }

    private void setScreen(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout pageColumn() {
        LinearLayout page = column();
        page.setPadding(dp(16), dp(16), dp(16), dp(24));
        return page;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(roundRect(Color.WHITE, 18, Color.rgb(226, 232, 240), 1));
        card.setElevation(dp(2));
        return card;
    }

    private View statCard(String label, String value) {
        LinearLayout c = card();
        c.addView(text(value, 23, accent, true), matchWrap());
        c.addView(text(label, 12, Color.rgb(100, 116, 139), false), matchWrapMargin(0, 3));
        return c;
    }

    /** Rounded, cropped banner image loaded from the WordPress-configured URL. Hidden until (and unless) the image loads successfully. */
    private View bannerImage(String url) {
        FrameLayout wrap = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(228, 232, 240));
        bg.setCornerRadius(dp(20));
        wrap.setBackground(bg);
        wrap.setClipToOutline(true);
        wrap.setElevation(dp(3));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wrap.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadImageInto(url, iv);
        FrameLayout outer = new FrameLayout(this);
        outer.addView(wrap, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        return outer;
    }

    private void loadImageInto(String url, ImageView iv) {
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                c.disconnect();
                if (bmp != null) ui.post(() -> iv.setImageBitmap(bmp));
            } catch (Exception ignored) {
                // Image failing to load should never block the app — just leave it blank.
            }
        });
    }

    /** Warm gradient hero card (accent → darker accent) instead of flat gray, optionally with a name-initial avatar badge. */
    private View hero(String title, String subtitle, String avatarName) {
        LinearLayout h = column();
        h.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{accent, darken(accent, 0.55f)});
        g.setCornerRadius(dp(24));
        h.setBackground(g);
        h.setElevation(dp(4));

        if (avatarName != null && !avatarName.trim().isEmpty()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(avatarBadge(avatarName.trim().substring(0, 1).toUpperCase(Locale.getDefault())), new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = text(title, 21, Color.WHITE, true);
            LinearLayout.LayoutParams tlp = matchWrap();
            tlp.leftMargin = dp(10);
            row.addView(t, tlp);
            h.addView(row, matchWrap());
        } else {
            h.addView(text(title, 22, Color.WHITE, true), matchWrap());
        }

        TextView s = text(subtitle, 13, Color.rgb(240, 244, 255), false);
        s.setPadding(0, dp(8), 0, 0);
        h.addView(s, matchWrap());
        return h;
    }

    private View hero(String title, String subtitle) { return hero(title, subtitle, null); }

    private View avatarBadge(String letter) {
        TextView t = new TextView(this);
        t.setText(letter);
        t.setTextColor(accent);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.WHITE);
        t.setBackground(d);
        return t;
    }

    private int darken(int color, float factor) {
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.rgb(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));
    }

    private View sectionIntro(String title, String subtitle) {
        LinearLayout c = card();
        c.addView(text(title, 21, Color.rgb(15, 23, 42), true), matchWrap());
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView p = bodyText(subtitle);
            p.setPadding(0, dp(7), 0, 0);
            c.addView(p, matchWrap());
        }
        return c;
    }

    private TextView sectionTitle(String t) { return text(t, 18, Color.rgb(15, 23, 42), true); }

    private TextView bodyText(String t) {
        TextView v = text(t == null ? "" : t, 14, Color.rgb(51, 65, 85), false);
        v.setLineSpacing(0, 1.25f);
        return v;
    }

    private View note(String t) {
        TextView v = bodyText(t);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setBackground(roundRect(Color.rgb(239, 246, 255), 14, Color.rgb(191, 219, 254), 1));
        return v;
    }

    private View infoLine(String label, String value) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        l.setBackground(roundRect(Color.WHITE, 14, Color.rgb(226, 232, 240), 1));
        TextView a = text(label, 13, Color.rgb(100, 116, 139), false);
        TextView b = text(value == null || value.isEmpty() ? "ثبت نشده" : value, 14, Color.rgb(15, 23, 42), true);
        b.setGravity(Gravity.LEFT);
        l.addView(a, weighted());
        l.addView(b, weighted());
        return l;
    }

    private TextView empty(String t) {
        TextView v = text(t, 14, Color.rgb(100, 116, 139), false);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(20), dp(28), dp(20), dp(28));
        return v;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(Color.rgb(15, 23, 42));
        e.setHintTextColor(Color.rgb(148, 163, 184));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        e.setBackground(roundRect(Color.WHITE, 14, Color.rgb(203, 213, 225), 1));
        e.setSingleLine(false);
        return e;
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect(accent, 18, Color.TRANSPARENT, 0));
        b.setElevation(dp(3));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(accent);
        b.setBackground(roundRect(Color.WHITE, 18, Color.rgb(191, 219, 254), 1));
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(Color.rgb(51, 65, 85));
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(2), 0, dp(2), 0);
        return b;
    }

    private GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams matchWrapMargin(int top, int bottom) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams matchWrapHeightMargin(int heightDp, int top, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)); p.topMargin = dp(top); p.bottomMargin = dp(bottom); return p; }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1); }
    private LinearLayout.LayoutParams weightedMargin(int leftDp) { LinearLayout.LayoutParams p = weighted(); p.leftMargin = dp(leftDp); return p; }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String htmlToText(String html) {
        if (html == null) return "";
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private boolean arrayContains(JSONArray a, String value) {
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) if (value.equals(a.optString(i))) return true;
        return false;
    }

    private String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.0001) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.1f", v);
    }

    private String timeText(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void showMessage(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("باشه", null).show(); }

    @Override
    public void onBackPressed() {
        if (currentAttemptId > 0) {
            new AlertDialog.Builder(this).setTitle("خروج از آزمون").setMessage("آزمون هنوز در حال اجراست. برای جلوگیری از از دست رفتن پاسخ‌ها در همین صفحه بمانید.").setPositiveButton("ادامه آزمون", null).show();
            return;
        }
        super.onBackPressed();
    }
}
