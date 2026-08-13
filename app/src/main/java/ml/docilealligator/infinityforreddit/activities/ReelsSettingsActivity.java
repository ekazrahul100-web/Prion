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

import android.widget.EditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.utils.NsfwCategoryManager;


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
    public static final String PREF_SHOW_SEEKBAR      = "reels_show_seekbar";
    public static final String PREF_SPEED_UP_MULTIPLIER = "reels_speed_up_multiplier";
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

        // ── Show Seekbar ──────────────────────────────────────────────────
        SwitchCompat showSeekbarSwitch = findViewById(R.id.show_seekbar_switch);
        showSeekbarSwitch.setChecked(mSharedPreferences.getBoolean(PREF_SHOW_SEEKBAR, true));
        showSeekbarSwitch.setOnCheckedChangeListener((btn, checked) ->
                mSharedPreferences.edit().putBoolean(PREF_SHOW_SEEKBAR, checked).apply());

        // ── Speed Up Multiplier ───────────────────────────────────────────
        LinearLayout speedUpContainer = findViewById(R.id.speed_up_container);
        android.widget.TextView speedUpTextView = findViewById(R.id.speed_up_text_view);
        String currentSpeed = mSharedPreferences.getString(PREF_SPEED_UP_MULTIPLIER, "2.0");
        speedUpTextView.setText(currentSpeed + "x");
        speedUpContainer.setOnClickListener(v -> {
            String[] options = {"1.25", "1.5", "1.75", "2.0"};
            int checkedItem = -1;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(mSharedPreferences.getString(PREF_SPEED_UP_MULTIPLIER, "2.0"))) {
                    checkedItem = i;
                    break;
                }
            }
            new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle("Select Speed Multiplier")
                    .setSingleChoiceItems(new String[]{"1.25x", "1.5x", "1.75x", "2.0x"}, checkedItem, (dialog, which) -> {
                        String newSpeed = options[which];
                        mSharedPreferences.edit().putString(PREF_SPEED_UP_MULTIPLIER, newSpeed).apply();
                        speedUpTextView.setText(newSpeed + "x");
                        dialog.dismiss();
                    })
                    .show();
        });

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

        // ── Manage Categories ─────────────────────────────────────────────
        LinearLayout manageCategoriesContainer = findViewById(R.id.manage_categories_container);
        manageCategoriesContainer.setOnClickListener(v -> showCategoryManagerMenu());
    }

    private void showCategoryManagerMenu() {
        String[] options = {"Create New Custom Category", "Edit Subreddits in Category", "Delete Custom Category"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Manage NSFW Categories")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreateCustomCategoryDialog();
                    } else if (which == 1) {
                        showSelectCategoryToEditDialog();
                    } else if (which == 2) {
                        showDeleteCustomCategoryDialog();
                    }
                })
                .show();
    }

    private void showCreateCustomCategoryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Category Name (e.g. My Favorites)");
        layout.addView(nameInput);

        final EditText subsInput = new EditText(this);
        subsInput.setHint("Subreddits (comma-separated, e.g. gonewild, RealGirls)");
        layout.addView(subsInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create Custom Category")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String subsStr = subsInput.getText().toString().trim();
                    if (!name.isEmpty() && !subsStr.isEmpty()) {
                        String[] split = subsStr.split(",");
                        List<String> list = new ArrayList<>();
                        for (String s : split) {
                            String trimmed = s.trim();
                            if (!trimmed.isEmpty()) list.add(trimmed);
                        }
                        NsfwCategoryManager.saveCustomCategory(mSharedPreferences, name, list);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSelectCategoryToEditDialog() {
        Map<String, List<String>> map = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
        List<String> names = new ArrayList<>(map.keySet());
        String[] items = names.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Category to Edit")
                .setItems(items, (dialog, which) -> {
                    String selectedName = items[which];
                    List<String> currentSubs = map.get(selectedName);
                    showEditSubredditsDialog(selectedName, currentSubs != null ? currentSubs : new ArrayList<>());
                })
                .show();
    }

    private void showEditSubredditsDialog(String categoryName, List<String> currentSubs) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        final EditText subsInput = new EditText(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < currentSubs.size(); i++) {
            sb.append(currentSubs.get(i));
            if (i < currentSubs.size() - 1) sb.append(", ");
        }
        subsInput.setText(sb.toString());
        subsInput.setHint("Subreddits (comma-separated)");
        layout.addView(subsInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Subreddits: " + categoryName)
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String subsStr = subsInput.getText().toString().trim();
                    String[] split = subsStr.split(",");
                    List<String> list = new ArrayList<>();
                    for (String s : split) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) list.add(trimmed);
                    }
                    if (categoryName.startsWith("⭐ ")) {
                        NsfwCategoryManager.saveCustomCategory(mSharedPreferences, categoryName.substring(2), list);
                    } else {
                        NsfwCategoryManager.saveCategoryOverride(mSharedPreferences, categoryName, list);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteCustomCategoryDialog() {
        Map<String, List<String>> map = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
        List<String> customNames = new ArrayList<>();
        for (String k : map.keySet()) {
            if (k.startsWith("⭐ ")) customNames.add(k);
        }
        if (customNames.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Custom Category")
                    .setMessage("No custom categories created yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] items = customNames.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Custom Category to Delete")
                .setItems(items, (dialog, which) -> {
                    NsfwCategoryManager.deleteCustomCategory(mSharedPreferences, items[which]);
                })
                .show();
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
