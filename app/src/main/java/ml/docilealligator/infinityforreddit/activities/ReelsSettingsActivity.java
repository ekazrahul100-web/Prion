package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;

/**
 * Settings screen for Reels mode.
 *
 * SharedPreferences keys (stored in the default SharedPreferences):
 *   reels_landscape_mode  int  0=Default  1=Autorotate  2=Fill
 *   reels_auto_advance    bool false=loop  true=auto-next
 *   reels_quality_hd      bool true=HD    false=SD
 *   hide_seen_posts_in_reels bool
 */
public class ReelsSettingsActivity extends BaseActivity {

    public static final String PREF_LANDSCAPE_MODE   = "reels_landscape_mode";
    public static final String PREF_AUTO_ADVANCE      = "reels_auto_advance";
    public static final String PREF_QUALITY_HD        = "reels_quality_hd";
    public static final String PREF_HIDE_SEEN_REELS   = "hide_seen_posts_in_reels";

    public static final int LANDSCAPE_DEFAULT    = 0;
    public static final int LANDSCAPE_AUTOROTATE = 1;
    public static final int LANDSCAPE_FILLIN     = 2;

    @Inject @Named("default")
    SharedPreferences mSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels_settings);

        // ── Toolbar ──────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar_reels_settings);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // ── Landscape mode radio buttons ──────────────────────────────────
        LinearLayout defaultContainer     = findViewById(R.id.landscape_mode_default_container);
        LinearLayout autoRotateContainer  = findViewById(R.id.landscape_mode_autorotate_container);
        LinearLayout fillInContainer      = findViewById(R.id.landscape_mode_fillin_container);

        RadioButton defaultRadio    = findViewById(R.id.landscape_mode_default_radio);
        RadioButton autoRotateRadio = findViewById(R.id.landscape_mode_autorotate_radio);
        RadioButton fillInRadio     = findViewById(R.id.landscape_mode_fillin_radio);

        int savedLandscapeMode = mSharedPreferences.getInt(PREF_LANDSCAPE_MODE, LANDSCAPE_AUTOROTATE);
        updateLandscapeRadios(defaultRadio, autoRotateRadio, fillInRadio, savedLandscapeMode);

        defaultContainer.setOnClickListener(v -> {
            mSharedPreferences.edit().putInt(PREF_LANDSCAPE_MODE, LANDSCAPE_DEFAULT).apply();
            updateLandscapeRadios(defaultRadio, autoRotateRadio, fillInRadio, LANDSCAPE_DEFAULT);
        });
        autoRotateContainer.setOnClickListener(v -> {
            mSharedPreferences.edit().putInt(PREF_LANDSCAPE_MODE, LANDSCAPE_AUTOROTATE).apply();
            updateLandscapeRadios(defaultRadio, autoRotateRadio, fillInRadio, LANDSCAPE_AUTOROTATE);
        });
        fillInContainer.setOnClickListener(v -> {
            mSharedPreferences.edit().putInt(PREF_LANDSCAPE_MODE, LANDSCAPE_FILLIN).apply();
            updateLandscapeRadios(defaultRadio, autoRotateRadio, fillInRadio, LANDSCAPE_FILLIN);
        });

        // ── Auto-advance ──────────────────────────────────────────────────
        SwitchCompat autoAdvanceSwitch = findViewById(R.id.auto_advance_switch);
        autoAdvanceSwitch.setChecked(mSharedPreferences.getBoolean(PREF_AUTO_ADVANCE, false));
        autoAdvanceSwitch.setOnCheckedChangeListener((btn, checked) ->
                mSharedPreferences.edit().putBoolean(PREF_AUTO_ADVANCE, checked).apply());

        // ── HD quality ────────────────────────────────────────────────────
        SwitchCompat hdSwitch = findViewById(R.id.hd_quality_switch);
        hdSwitch.setChecked(mSharedPreferences.getBoolean(PREF_QUALITY_HD, true));
        hdSwitch.setOnCheckedChangeListener((btn, checked) ->
                mSharedPreferences.edit().putBoolean(PREF_QUALITY_HD, checked).apply());

        // ── Hide seen ─────────────────────────────────────────────────────
        SwitchCompat hideSeenSwitch = findViewById(R.id.hide_seen_switch);
        hideSeenSwitch.setChecked(mSharedPreferences.getBoolean(PREF_HIDE_SEEN_REELS, false));
        hideSeenSwitch.setOnCheckedChangeListener((btn, checked) ->
                mSharedPreferences.edit().putBoolean(PREF_HIDE_SEEN_REELS, checked).apply());
    }

    private void updateLandscapeRadios(RadioButton defaultR, RadioButton autoRotateR,
                                        RadioButton fillInR, int mode) {
        defaultR.setChecked(mode == LANDSCAPE_DEFAULT);
        autoRotateR.setChecked(mode == LANDSCAPE_AUTOROTATE);
        fillInR.setChecked(mode == LANDSCAPE_FILLIN);
    }

    // ── BaseActivity ─────────────────────────────────────────────────────

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        // Inherits app theme automatically
    }
}
