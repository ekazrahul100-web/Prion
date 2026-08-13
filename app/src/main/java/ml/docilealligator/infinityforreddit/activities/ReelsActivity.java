package ml.docilealligator.infinityforreddit.activities;

import java.util.Map;

import android.content.Intent;

import ml.docilealligator.infinityforreddit.customviews.NavigationWrapper;
import ml.docilealligator.infinityforreddit.activities.InboxActivity;
import ml.docilealligator.infinityforreddit.activities.SearchActivity;
import ml.docilealligator.infinityforreddit.activities.ViewUserDetailActivity;
import ml.docilealligator.infinityforreddit.activities.SubscribedThingListingActivity;
import ml.docilealligator.infinityforreddit.activities.ViewSubredditDetailActivity;
import ml.docilealligator.infinityforreddit.activities.AccountPostsActivity;
import ml.docilealligator.infinityforreddit.activities.AccountSavedThingActivity;
import ml.docilealligator.infinityforreddit.events.ShowThumbnailOnTheLeftInCompactLayoutEvent;
import org.greenrobot.eventbus.EventBus;
import ml.docilealligator.infinityforreddit.post.PostPagingSource;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.VideoSize;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.viewpager2.widget.ViewPager2;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.ImageView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import ml.docilealligator.infinityforreddit.adapters.CategoryAdapter;
import ml.docilealligator.infinityforreddit.utils.NsfwCategoryManager;




import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;


import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.ReelsAdapter;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;
import ml.docilealligator.infinityforreddit.readpost.ReadPostsListInterface;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;

import androidx.media3.ui.AspectRatioFrameLayout;
import retrofit2.Retrofit;

public class ReelsActivity extends BaseActivity {

    // ── Intent extras ──────────────────────────────────────────────────────
    /** Optional: pass a subreddit name to lock Reels to that subreddit only. */
    public static final String EXTRA_SUBREDDIT_NAME = "reels_subreddit_name";

    // ── SharedPreferences keys (delegated to ReelsSettingsActivity) ─────
    private static final String PREF_HIDE_SEEN_REELS = ReelsSettingsActivity.PREF_HIDE_SEEN_REELS;
    private static final String REELS_NAMESPACE = "reels";

    // ── Dwell time to mark a video as "seen" (ms) ────────────────────────
    private static final int DWELL_TIME_MS = 5000;


    // ── Modes ─────────────────────────────────────────────────────────────
    private static final int MODE_SFW        = 0;
    private static final int MODE_SUBSCRIBED = 1;
    private static final int MODE_NSFW       = 2;

    // ── Injected dependencies ─────────────────────────────────────────────
    @Inject @Named("default")
    SharedPreferences mSharedPreferences;

    @Inject @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    @Inject @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject @Named("no_oauth")
    Retrofit mRetrofit;

    @Inject
    Executor mExecutor;

    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;

    // ── Views ─────────────────────────────────────────────────────────────
    private ViewPager2 viewPager;
    private ProgressBar progressBar;
    private LinearLayout modeSelectorContainer;
    private LinearLayout sortSelectorContainer;
    private TextView currentModeTextView;
    private TextView sortTypeTextView;
    private ImageView refreshButton;
    private ImageView reelsSettingsButton;
    private LinearLayout categorySelectorContainer;
    private TextView categoryTextView;


    public static final String PREF_NSFW_CATEGORY = "pref_reels_nsfw_category";



    // ── Adapters ──────────────────────────────────────────────────────────

    private ReelsAdapter sfwAdapter;
    private ReelsAdapter nsfwAdapter;
    private ReelsAdapter subscribedAdapter;

    // ── State ─────────────────────────────────────────────────────────────
    private int currentMode = MODE_SFW;

    @Nullable private String sfwAfter        = null;
    @Nullable private String nsfwAfter       = null;
    @Nullable private String subscribedAfter = null;
    private int sfwPosition        = 0;
    private int nsfwPosition       = 0;
    private int subscribedPosition = 0;
    private boolean isLoading = false;

    /** Current sort type. Default: HOT. */
    @NonNull private SortType.Type currentSortType = SortType.Type.HOT;
    /** Current time filter (only relevant for TOP and CONTROVERSIAL). */
    @Nullable private SortType.Time currentSortTime = null;

    /** If non-null, Reels is locked to this single subreddit (subreddit immersive mode). */
    @Nullable private String lockedSubreddit = null;

    /** Current landscape mode — one of ReelsSettingsActivity.LANDSCAPE_* constants. */
    private int landscapeMode = ReelsSettingsActivity.LANDSCAPE_AUTOROTATE;
    /** Whether HD quality is preferred (affects track selector cap). */
    private boolean preferHd = true;
    /** Whether to auto-advance to the next video when current ends. */
    private boolean autoAdvance = false;

    // ── Dwell tracking ────────────────────────────────────────────────────
    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable dwellRunnable;
    private int currentPosition = -1;

    // ── Subreddit pools ───────────────────────────────────────────────────
    private final String[] NSFW_POOL = {
            "nsfw_gif", "gonewild", "RealGirls", "asiansgonewild",
            "latinas", "thick", "petite", "curvy", "collegesluts",
            "nsfw_video", "holdthemoan", "pussy", "boobs", "ass",
            "thong", "milf", "amature", "tits", "nsfw",
            "legalteens", "barelylegal", "BustyPetite", "dirtypenpals",
            "nsfwcosplay", "nsfwoutfits", "PublicFlashing", "FlashingGirls",
            "gwpublic", "BigBoobsGW", "TittyDrop", "TinyTits",
            "PornGifs", "60fpsporn", "highresNSFW", "NSFW_HTML5",
            "gifsgonewild", "JizzToThis", "CumHaters", "FacialFun",
            "AmateurPorn", "Amateur", "AmateurArchives", "HomemadePorn"
    };

    private final String[] SFW_POOL = {
            "videos", "gifs", "TikTokCringe", "funny", "aww", "nextfuckinglevel",
            "PublicFreakout", "IdiotsInCars", "Unexpected", "AbruptChaos",
            "Whatcouldgowrong", "holdmybeer", "CatGifs", "dogpictures",
            "BeAmazed", "interestingasfuck", "nonononoyes", "yesyesyesyesno",
            "MadeMeSmile", "dankvideos", "CrazyFuckingVideos", "woahdude",
            "WatchPeopleDieInside", "AnimalsBeingDerps", "oddlysatisfying"
    };

    // ── Category Deck & Cooldown Memory (Option A) ────────────────────────
    private final List<String> currentCategoryDeck = new ArrayList<>();
    private final LinkedHashSet<String> categoryCooldownSet = new LinkedHashSet<>();
    @Nullable private String activeCategoryKey = null;

    private NavigationWrapper navigationWrapper;
    private boolean showBottomAppBar;

    // ─────────────────────────────────────────────────────────────────────
    // onCreate
    // ─────────────────────────────────────────────────────────────────────

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentMode", currentMode);
        outState.putInt("sfwPosition", sfwPosition);
        outState.putInt("nsfwPosition", nsfwPosition);
        outState.putInt("subscribedPosition", subscribedPosition);
        outState.putString("sfwAfter", sfwAfter);
        outState.putString("nsfwAfter", nsfwAfter);
        outState.putString("subscribedAfter", subscribedAfter);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentMode = savedInstanceState.getInt("currentMode", MODE_SFW);
        sfwPosition = savedInstanceState.getInt("sfwPosition", 0);
        nsfwPosition = savedInstanceState.getInt("nsfwPosition", 0);
        subscribedPosition = savedInstanceState.getInt("subscribedPosition", 0);
        sfwAfter = savedInstanceState.getString("sfwAfter");
        nsfwAfter = savedInstanceState.getString("nsfwAfter");
        subscribedAfter = savedInstanceState.getString("subscribedAfter");
        
        updateModeUI();
        ReelsAdapter currentAdapter = getCurrentAdapter();
        if (currentAdapter != null) {
            viewPager.setAdapter(currentAdapter);
            int pos = getCurrentPosition();
            if (pos >= 0 && pos < currentAdapter.getItemCount()) {
                viewPager.setCurrentItem(pos, false);
            }
        }
        
        // If the activity was killed, the adapter will be empty. Fetch videos again.
        if (currentAdapter != null && currentAdapter.getItemCount() == 0) {
            fetchVideos();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);

        // Force status and navigation bars to black since Reels is inherently a dark mode experience
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        
        // Ensure status bar icons are light (since background is now black)
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (windowInsetsController != null) {
            windowInsetsController.setAppearanceLightStatusBars(false);
            windowInsetsController.setAppearanceLightNavigationBars(false);
        }

        // Bind views
        viewPager               = findViewById(R.id.view_pager_reels);
        progressBar             = findViewById(R.id.reels_progress_bar);
        modeSelectorContainer   = findViewById(R.id.mode_selector_container);
        sortSelectorContainer   = findViewById(R.id.sort_selector_container);
        currentModeTextView     = findViewById(R.id.current_mode_text_view);
        sortTypeTextView        = findViewById(R.id.sort_type_text_view);
        refreshButton           = findViewById(R.id.refresh_button);
        reelsSettingsButton     = findViewById(R.id.reels_settings_button);
        categorySelectorContainer = findViewById(R.id.category_selector_container);
        categoryTextView          = findViewById(R.id.category_text_view);

        View topOverlay = findViewById(R.id.top_overlay_container);
        if (topOverlay != null) {
            int statusBarHeight = 0;
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(resourceId);
            } else {
                statusBarHeight = (int) (32 * getResources().getDisplayMetrics().density);
            }
            // Just use the exact status bar height plus a tiny 6dp margin so it's not totally flush,
            // but doesn't float disconnected like before.
            int topPadding = statusBarHeight + (int) (6 * getResources().getDisplayMetrics().density);
            topOverlay.setPadding(topOverlay.getPaddingLeft(), topPadding, topOverlay.getPaddingRight(), topOverlay.getPaddingBottom());
        }

        categorySelectorContainer.setOnClickListener(v -> showCategoryPopup());



        // Read settings from ReelsSettingsActivity prefs
        applySettingsFromPrefs();

        // Subreddit-locked mode from Intent
        lockedSubreddit = getIntent().getStringExtra(EXTRA_SUBREDDIT_NAME);
        if (lockedSubreddit != null) {
            // Hide mode selector and sort center chip – subreddit mode uses its own label
            modeSelectorContainer.setVisibility(View.INVISIBLE);
            sortSelectorContainer.setVisibility(View.VISIBLE);
        }


        // Refresh button
        refreshButton.setOnClickListener(v -> {
            clearCurrentAdapter();
            fetchVideos();
        });

        // Settings button → ReelsSettingsActivity
        reelsSettingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, ReelsSettingsActivity.class)));

        // Build the shared InteractionListener
        ReelsAdapter.InteractionListener listener = buildInteractionListener();

        sfwAdapter        = new ReelsAdapter(this, listener);
        nsfwAdapter       = new ReelsAdapter(this, listener);
        subscribedAdapter = new ReelsAdapter(this, listener);

        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setAdapter(sfwAdapter);

        // Mode selector dropdown
        modeSelectorContainer.setOnClickListener(v -> showModePopup());

        // Sort selector bottom sheet
        sortSelectorContainer.setOnClickListener(v -> showSortPopup());

        // Page-change callback
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                ReelsAdapter currentAdapter = getCurrentAdapter();

                if (currentMode == MODE_NSFW)             nsfwPosition = position;
                else if (currentMode == MODE_SUBSCRIBED)  subscribedPosition = position;
                else                                       sfwPosition = position;

                currentAdapter.playVideoAt(position);

                // Apply landscape mode for current video
                detectAndApplyLandscapeMode(currentAdapter, position);

                // Auto-advance: attach listener to jump when video ends
                if (autoAdvance) attachAutoAdvanceListener(currentAdapter, position);

                if (position >= currentAdapter.getItemCount() - 5 && !isLoading) {
                    fetchVideos();
                }

                // Dwell timer → mark as seen
                if (dwellRunnable != null) dwellHandler.removeCallbacks(dwellRunnable);
                currentPosition = position;
                dwellRunnable = () -> {
                    if (currentPosition == position) {
                        Post p = currentAdapter.getPostAt(position);
                        if (p != null) {
                            SeenPostsManager.markSeen(mSharedPreferences, p.getId(), REELS_NAMESPACE);
                        }
                    }
                };
                dwellHandler.postDelayed(dwellRunnable, DWELL_TIME_MS);
            }
        });

        // Initialize bottom app bar
        showBottomAppBar = mSharedPreferences.getBoolean(SharedPreferencesUtils.BOTTOM_APP_BAR_KEY, false);
        navigationWrapper = new NavigationWrapper(findViewById(R.id.bottom_app_bar_bottom_app_bar), findViewById(R.id.linear_layout_bottom_app_bar),
                findViewById(R.id.option_1_bottom_app_bar), findViewById(R.id.option_2_bottom_app_bar),
                findViewById(R.id.option_3_bottom_app_bar), findViewById(R.id.option_4_bottom_app_bar),
                findViewById(R.id.fab_main_activity), findViewById(R.id.navigation_rail), mCustomThemeWrapper, showBottomAppBar);

        if (showBottomAppBar) {
            View navCard = findViewById(R.id.reels_bottom_nav_card);
            if (navCard != null) navCard.setVisibility(View.VISIBLE);

            if (navigationWrapper.bottomAppBar != null) {
                navigationWrapper.bottomAppBar.setBackgroundTint(android.content.res.ColorStateList.valueOf(android.graphics.Color.BLACK));
            }

            sfwAdapter.setShowBottomNav(true);
            nsfwAdapter.setShowBottomNav(true);
            subscribedAdapter.setShowBottomNav(true);

            String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "");
            if (accountName == null) accountName = "";
            int option1 = mSharedPreferences.getInt((accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT : "") + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_1, SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SUBSCRIPTIONS);
            int option2 = mSharedPreferences.getInt((accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT : "") + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_2, accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SEARCH : SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_MULTIREDDITS);
            int option3 = mSharedPreferences.getInt((accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT : "") + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_3, accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_REFRESH : SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_INBOX);
            int option4 = mSharedPreferences.getInt((accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT : "") + SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_4, accountName.equals(ml.docilealligator.infinityforreddit.account.Account.ANONYMOUS_ACCOUNT) ? SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_CHANGE_SORT_TYPE : SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_PROFILE);

            navigationWrapper.bindOptionDrawableResource(getBottomAppBarOptionDrawableResource(option1),
                    getBottomAppBarOptionDrawableResource(option2), getBottomAppBarOptionDrawableResource(option3),
                    getBottomAppBarOptionDrawableResource(option4));
            
            navigationWrapper.option1BottomAppBar.setOnClickListener(view -> bottomAppBarOptionAction(option1));
            navigationWrapper.option2BottomAppBar.setOnClickListener(view -> bottomAppBarOptionAction(option2));
            navigationWrapper.option3BottomAppBar.setOnClickListener(view -> bottomAppBarOptionAction(option3));
            navigationWrapper.option4BottomAppBar.setOnClickListener(view -> bottomAppBarOptionAction(option4));

            ImageView optionCenter = findViewById(R.id.option_center_bottom_app_bar);
            if (optionCenter != null) {
                optionCenter.setVisibility(View.VISIBLE);
                optionCenter.setImageResource(R.drawable.ic_home_day_night_24dp);
                optionCenter.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                optionCenter.setOnClickListener(v -> {
                    Intent intent = new Intent(ReelsActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }
        }

        fetchVideos();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Settings helpers
    // ─────────────────────────────────────────────────────────────────────

    private void applySettingsFromPrefs() {
        landscapeMode = mSharedPreferences.getInt(
                ReelsSettingsActivity.PREF_LANDSCAPE_MODE,
                ReelsSettingsActivity.LANDSCAPE_AUTOROTATE);
        preferHd    = mSharedPreferences.getBoolean(ReelsSettingsActivity.PREF_QUALITY_HD, true);
        autoAdvance = mSharedPreferences.getBoolean(ReelsSettingsActivity.PREF_AUTO_ADVANCE, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-read settings in case the user changed them in ReelsSettingsActivity
        applySettingsFromPrefs();
        // Re-apply landscape mode to the currently visible video
        ReelsAdapter currentAdapter = getCurrentAdapter();
        int pos = getCurrentPosition();
        if (currentAdapter != null) {
            if (currentAdapter.getItemCount() > 0) {
                detectAndApplyLandscapeMode(currentAdapter, pos);
            }
            currentAdapter.resumeCurrentPlayer();
        }
    }



    /**
     * Called whenever a new video page is shown. Applies the user's chosen
     * landscape mode: Default (nothing), Autorotate (seamless rotation),
     * or Fill (zoom-crop in portrait).
     */
    private void detectAndApplyLandscapeMode(ReelsAdapter adapter, int position) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Post post = adapter.getPostAt(position);
            if (post == null) return;

            int width = 0, height = 0;
            java.util.ArrayList<Post.Preview> previews = post.getPreviews();
            if (previews != null && !previews.isEmpty()) {
                width  = previews.get(0).getPreviewWidth();
                height = previews.get(0).getPreviewHeight();
            }
            boolean isLandscapeVideo = (width > 0 && height > 0 && width > height);

            switch (landscapeMode) {
                case ReelsSettingsActivity.LANDSCAPE_AUTOROTATE:
                    // Reset fill mode on adapter (use fit, not zoom)
                    adapter.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT, position);
                    if (isLandscapeVideo) {
                        rotateToOrientation(true);
                    } else {
                        rotateToOrientation(false);
                    }
                    break;

                case ReelsSettingsActivity.LANDSCAPE_FILLIN:
                    // Stay portrait; zoom-crop to fill the screen
                    rotateToOrientation(false);
                    adapter.setResizeMode(
                            isLandscapeVideo
                                    ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    : AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                            position);
                    break;

                case ReelsSettingsActivity.LANDSCAPE_DEFAULT:
                default:
                    // Portrait, no changes — video shows with black bars
                    rotateToOrientation(false);
                    adapter.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, position);
                    break;
            }
        }, 350);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        View navCard = findViewById(R.id.reels_bottom_nav_card);
        if (navCard != null) {
            navCard.setVisibility((isLandscape || !showBottomAppBar) ? View.GONE : View.VISIBLE);
        } else if (navigationWrapper != null && navigationWrapper.bottomAppBar != null) {
            navigationWrapper.bottomAppBar.setVisibility((isLandscape || !showBottomAppBar) ? View.GONE : View.VISIBLE);
        }
        ReelsAdapter currentAdapter = getCurrentAdapter();
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Rotates the screen instantly (no animation) to the given orientation.
     */
    private void rotateToOrientation(boolean landscape) {
        View navCard = findViewById(R.id.reels_bottom_nav_card);
        if (navCard != null) {
            navCard.setVisibility((landscape || !showBottomAppBar) ? View.GONE : View.VISIBLE);
        } else if (navigationWrapper != null && navigationWrapper.bottomAppBar != null) {
            navigationWrapper.bottomAppBar.setVisibility((landscape || !showBottomAppBar) ? View.GONE : View.VISIBLE);
        }

        int target = landscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

        int currentOrientation = getResources().getConfiguration().orientation;
        boolean alreadyLandscape = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE);
        if (landscape == alreadyLandscape) return;

        // Suppress rotation animation
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        overridePendingTransition(0, 0);
        setRequestedOrientation(target);

        ReelsAdapter currentAdapter = getCurrentAdapter();
        if (currentAdapter != null) {
            currentAdapter.notifyDataSetChanged();
        }

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE), 400);
    }

    /**
     * Attach an auto-advance listener: when the video at [position] ends,
     * smoothly scroll to the next page.
     */
    private void attachAutoAdvanceListener(ReelsAdapter adapter, int position) {
        adapter.setAutoAdvanceListener(position, () -> {
            if (position + 1 < adapter.getItemCount()) {
                viewPager.setCurrentItem(position + 1, true);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Mode selector popup
    // ─────────────────────────────────────────────────────────────────────

    private void showModePopup() {
        Context wrapper = new androidx.appcompat.view.ContextThemeWrapper(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark);
        PopupMenu popup = new PopupMenu(wrapper, modeSelectorContainer);
        popup.getMenu().add(0, MODE_SFW,        0, "SFW");
        popup.getMenu().add(0, MODE_SUBSCRIBED, 1, "Subscribed");
        popup.getMenu().add(0, MODE_NSFW,       2, "NSFW");
        popup.setOnMenuItemClickListener(item -> {
            int newMode = item.getItemId();
            if (currentMode == newMode) return true;
            getCurrentAdapter().releasePlayers();
            currentMode = newMode;
            updateModeUI();
            // Reset sort to HOT when switching modes (keeps it simple)
            currentSortType = SortType.Type.HOT;
            currentSortTime = null;
            updateSortUI();
            ReelsAdapter newAdapter = getCurrentAdapter();
            int newPos = getCurrentPosition();
            viewPager.setAdapter(newAdapter);
            viewPager.setCurrentItem(newPos, false);
            if (newAdapter.getItemCount() == 0) {
                fetchVideos();
            } else {
                newAdapter.playVideoAt(newPos);
            }
            return true;
        });
        popup.show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sort selector popup (two-step: type → time if needed)
    // ─────────────────────────────────────────────────────────────────────

    private void showSortPopup() {
        Context wrapper = new androidx.appcompat.view.ContextThemeWrapper(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark);
        PopupMenu popup = new PopupMenu(wrapper, sortSelectorContainer);
        popup.getMenu().add(0, 0, 0, "Hot");
        popup.getMenu().add(0, 1, 1, "New");
        popup.getMenu().add(0, 2, 2, "Rising");
        popup.getMenu().add(0, 3, 3, "Top…");
        popup.getMenu().add(0, 4, 4, "Controversial…");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: applySortType(SortType.Type.HOT,          null);         return true;
                case 1: applySortType(SortType.Type.NEW,          null);         return true;
                case 2: applySortType(SortType.Type.RISING,       null);         return true;
                case 3: showTimePopup(SortType.Type.TOP);                        return true;
                case 4: showTimePopup(SortType.Type.CONTROVERSIAL);              return true;
                default: return false;
            }
        });
        popup.show();
    }

    private void showTimePopup(SortType.Type sortType) {
        Context wrapper = new androidx.appcompat.view.ContextThemeWrapper(this, androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark);
        PopupMenu popup = new PopupMenu(wrapper, sortSelectorContainer);
        popup.getMenu().add(0, 0, 0, "Past hour");
        popup.getMenu().add(0, 1, 1, "Past 24 hours");
        popup.getMenu().add(0, 2, 2, "Past week");
        popup.getMenu().add(0, 3, 3, "Past month");
        popup.getMenu().add(0, 4, 4, "Past year");
        popup.getMenu().add(0, 5, 5, "All time");
        popup.setOnMenuItemClickListener(item -> {
            SortType.Time time;
            switch (item.getItemId()) {
                case 0:  time = SortType.Time.HOUR;  break;
                case 1:  time = SortType.Time.DAY;   break;
                case 2:  time = SortType.Time.WEEK;  break;
                case 3:  time = SortType.Time.MONTH; break;
                case 4:  time = SortType.Time.YEAR;  break;
                default: time = SortType.Time.ALL;   break;
            }
            applySortType(sortType, time);
            return true;
        });
        popup.show();
    }

    private void applySortType(SortType.Type type, @Nullable SortType.Time time) {
        currentSortType = type;
        currentSortTime = time;
        updateSortUI();
        // Clear current adapter and re-fetch with new sort
        clearCurrentAdapter();
        fetchVideos();
    }

    // ─────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────

    private void showCategoryPopup() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category_picker, null);
        dialog.setContentView(dialogView);

        EditText searchEditText = dialogView.findViewById(R.id.search_category_edit_text);
        RecyclerView recyclerView = dialogView.findViewById(R.id.categories_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Map<String, List<String>> map = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
        List<CategoryAdapter.CategoryItem> items = new ArrayList<>();
        items.add(new CategoryAdapter.CategoryItem("All NSFW", NsfwCategoryManager.getAllSubreddits(map).size()));

        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            items.add(new CategoryAdapter.CategoryItem(entry.getKey(), entry.getValue().size()));
        }

        CategoryAdapter adapter = new CategoryAdapter(items, categoryName -> {
            dialog.dismiss();
            mSharedPreferences.edit().putString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, categoryName).apply();
            updateModeUI();
            clearCurrentAdapter();
            fetchVideos();
        });

        recyclerView.setAdapter(adapter);

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private void updateModeUI() {
        if (currentMode == MODE_NSFW) {
            currentModeTextView.setText("NSFW");
            if (lockedSubreddit == null) {
                categorySelectorContainer.setVisibility(View.VISIBLE);
                String selectedCategory = mSharedPreferences.getString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, "All NSFW");
                categoryTextView.setText(selectedCategory);
            }
        } else {
            if (currentMode == MODE_SUBSCRIBED) currentModeTextView.setText("Subscribed");
            else currentModeTextView.setText("SFW");
            categorySelectorContainer.setVisibility(View.GONE);
        }
    }



    private void updateSortUI() {
        String label = currentSortType.fullName;
        if (currentSortTime != null) label += " · " + currentSortTime.fullName;
        sortTypeTextView.setText(label);
    }

    private ReelsAdapter getCurrentAdapter() {
        if (currentMode == MODE_NSFW)            return nsfwAdapter;
        if (currentMode == MODE_SUBSCRIBED)      return subscribedAdapter;
        return sfwAdapter;
    }

    private int getCurrentPosition() {
        if (currentMode == MODE_NSFW)            return nsfwPosition;
        if (currentMode == MODE_SUBSCRIBED)      return subscribedPosition;
        return sfwPosition;
    }

    private void clearCurrentAdapter() {
        getCurrentAdapter().clear();
        if (currentMode == MODE_NSFW)            nsfwAfter = null;
        else if (currentMode == MODE_SUBSCRIBED) subscribedAfter = null;
        else                                     sfwAfter = null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interaction listener
    // ─────────────────────────────────────────────────────────────────────

    private ReelsAdapter.InteractionListener buildInteractionListener() {
        return new ReelsAdapter.InteractionListener() {
            @Override
            public void onUpvote(Post post, int position) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, accessToken,
                        new VoteThing.VoteThingListener() {
                            @Override public void onVoteThingSuccess(int p) {}
                            @Override public void onVoteThingFail(int p) {}
                        }, post.getFullName(), APIUtils.DIR_UPVOTE, position);
            }

            @Override
            public void onDownvote(Post post, int position) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, accessToken,
                        new VoteThing.VoteThingListener() {
                            @Override public void onVoteThingSuccess(int p) {}
                            @Override public void onVoteThingFail(int p) {}
                        }, post.getFullName(), APIUtils.DIR_DOWNVOTE, position);
            }

            @Override
            public void onComments(Post post) {
                openPostDetail(post);
            }

            @Override
            public void onSave(Post post) {
                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                if (accessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                SaveThing.saveThing(mOauthRetrofit, accessToken, post.getFullName(),
                        new SaveThing.SaveThingListener() {
                            @Override public void success() {}
                            @Override public void failed() {}
                        });
            }

            @Override
            public void onShare(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, post.getTitle());
                shareIntent.putExtra(Intent.EXTRA_TEXT, post.getPermalink());
                startActivity(Intent.createChooser(shareIntent, "Share"));
            }

            @Override
            public void onOpenPost(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId(), REELS_NAMESPACE);
                openPostDetail(post);
            }

            @Override
            public void onSubredditClick(String subredditName) {
                Intent intent = new Intent(ReelsActivity.this, ViewSubredditDetailActivity.class);
                intent.putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, subredditName);
                startActivity(intent);
            }
        };
    }

    private void openPostDetail(Post post) {
        Intent intent = new Intent(this, ViewPostDetailActivity.class);
        intent.putExtra(ViewPostDetailActivity.EXTRA_POST_DATA, post);
        startActivity(intent);
        // Slide in from the right — natural "swipe left → open post" feel
        overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fetch videos
    // ─────────────────────────────────────────────────────────────────────

    private void fetchVideos() {
        isLoading = true;
        new Handler(Looper.getMainLooper()).post(() -> progressBar.setVisibility(View.VISIBLE));

        final boolean fetchSubscribed = (currentMode == MODE_SUBSCRIBED);
        final String subreddit;

        if (lockedSubreddit != null) {
            subreddit = lockedSubreddit;
        } else if (!fetchSubscribed) {
            List<String> pool = new ArrayList<>();
            if (currentMode == MODE_NSFW) {
                Map<String, List<String>> categoriesMap = NsfwCategoryManager.loadCategories(this, mSharedPreferences);
                String selectedCategory = mSharedPreferences.getString(NsfwCategoryManager.PREF_SELECTED_CATEGORY_NAME, "All NSFW");
                if (selectedCategory == null) selectedCategory = "All NSFW";

                // Re-initialize category deck if category changed or deck is empty
                if (!selectedCategory.equals(activeCategoryKey) || currentCategoryDeck.isEmpty()) {
                    activeCategoryKey = selectedCategory;
                    currentCategoryDeck.clear();
                    categoryCooldownSet.clear();

                    List<String> subs;
                    if (selectedCategory.equals("All NSFW") || !categoriesMap.containsKey(selectedCategory)) {
                        subs = NsfwCategoryManager.getAllSubreddits(categoriesMap);
                    } else {
                        subs = categoriesMap.get(selectedCategory);
                    }
                    if (subs != null && !subs.isEmpty()) {
                        currentCategoryDeck.addAll(subs);
                    } else {
                        Collections.addAll(currentCategoryDeck, NSFW_POOL);
                    }
                    Collections.shuffle(currentCategoryDeck);
                }

                // Pick subreddits not in cooldown
                List<String> available = new ArrayList<>();
                for (String sub : currentCategoryDeck) {
                    if (!categoryCooldownSet.contains(sub)) {
                        available.add(sub);
                    }
                }

                // If cooldown set has consumed most subreddits, reset cooldown for a new round
                if (available.size() < 20 && currentCategoryDeck.size() >= 20) {
                    categoryCooldownSet.clear();
                    available.clear();
                    available.addAll(currentCategoryDeck);
                    Collections.shuffle(available);
                } else if (available.isEmpty()) {
                    available.addAll(currentCategoryDeck);
                    Collections.shuffle(available);
                }

                int batchSize = Math.min(20, available.size());
                List<String> chosenBatch = available.subList(0, batchSize);
                categoryCooldownSet.addAll(chosenBatch);
                pool.addAll(chosenBatch);
            } else {
                Collections.addAll(pool, SFW_POOL);
                Collections.shuffle(pool);
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pool.size(); i++) {
                sb.append(pool.get(i));
                if (i < pool.size() - 1) sb.append("+");
            }
            subreddit = sb.toString();
        } else {
            subreddit = "popular"; // Fallback for anonymous subscribed feed
        }

        String currentAfter = currentMode == MODE_NSFW ? nsfwAfter
                : (currentMode == MODE_SUBSCRIBED ? subscribedAfter : sfwAfter);

        String accountName = mCurrentAccountSharedPreferences.getString(
                SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        if (accountName == null) accountName = Account.ANONYMOUS_ACCOUNT;

        RedditAPI api = Account.ANONYMOUS_ACCOUNT.equals(accountName)
                ? mRetrofit.create(RedditAPI.class)
                : mOauthRetrofit.create(RedditAPI.class);

        final String finalAccountName  = accountName;
        final String finalSubreddit    = subreddit;
        final boolean hideSeenEnabled  = mSharedPreferences.getBoolean(PREF_HIDE_SEEN_REELS, false);
        final int capturedMode         = currentMode;
        final SortType.Type sortType   = currentSortType;
        final SortType.Time sortTime   = currentSortTime;
        final int countBeforeAdd       = getCurrentAdapter().getItemCount();

        mExecutor.execute(() -> {
            try {
                retrofit2.Response<String> response;

                String accessToken = mCurrentAccountSharedPreferences.getString(
                        SharedPreferencesUtils.ACCESS_TOKEN, null);
                Map<String, String> oauthHeaders = APIUtils.getOAuthHeader(accessToken);

                if (fetchSubscribed && !Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                    response = api.getBestPostsListenableFuture(
                            sortType, sortTime, currentAfter, oauthHeaders).get();
                } else if (!Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                    response = api.getSubredditBestPostsOauthListenableFuture(
                            finalSubreddit, sortType, sortTime, currentAfter, 100,
                            APIUtils.getOAuthHeader(accessToken)).get();
                } else {
                    response = api.getSubredditBestPostsListenableFuture(
                            finalSubreddit, sortType, sortTime, currentAfter, 100).get();
                }

                if (response != null && response.isSuccessful() && response.body() != null) {
                    PostFilter filter = new PostFilter();
                    filter.allowNSFW        = true;
                    filter.containVideoType = true;
                    filter.containGifType   = true;
                    filter.containTextType  = false;
                    filter.containImageType = false;
                    filter.containLinkType  = false;
                    filter.containGalleryType = false;

                    ReadPostsListInterface readList = NullReadPostsList.getInstance();
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, readList);
                    String newAfter = ParsePost.getLastItem(response.body());

                    // Store pagination cursor
                    if (capturedMode == MODE_NSFW)            nsfwAfter = newAfter;
                    else if (capturedMode == MODE_SUBSCRIBED) subscribedAfter = newAfter;
                    else                                       sfwAfter = newAfter;

                    List<Post> finalVideos = new ArrayList<>();
                    if (posts != null) {
                        Map<String, Integer> subCounts = new HashMap<>();
                        List<Post> validVideos = new ArrayList<>();
                        for (Post p : posts) {
                            if (p.getPostType() != Post.VIDEO_TYPE && p.getPostType() != Post.GIF_TYPE) continue;

                            // In subreddit-locked (immersive) mode, skip the SFW/NSFW filter
                            if (lockedSubreddit == null) {
                                // NSFW mode: only NSFW posts
                                if (capturedMode == MODE_NSFW && !p.isNSFW()) continue;
                                // SFW mode: no NSFW posts
                                if (capturedMode == MODE_SFW && p.isNSFW()) continue;
                                // Subscribed mode: respect per-account NSFW setting
                                if (capturedMode == MODE_SUBSCRIBED && p.isNSFW()) {
                                    SharedPreferences nsfwPrefs = getSharedPreferences(
                                            SharedPreferencesUtils.NSFW_AND_SPOILER_SHARED_PREFERENCES_FILE,
                                            MODE_PRIVATE);
                                    boolean nsfwAllowed = nsfwPrefs.getBoolean(
                                            finalAccountName + SharedPreferencesUtils.NSFW_BASE, false);
                                    if (!nsfwAllowed) continue;
                                }
                            }

                            // Hide-seen filter
                            if (hideSeenEnabled &&
                                    SeenPostsManager.hasSeen(mSharedPreferences, p.getId(), REELS_NAMESPACE)) continue;
                                    
                            // Subreddit dominance limiter
                            String sub = p.getSubredditName() != null ? p.getSubredditName().toLowerCase() : "unknown";
                            int count = subCounts.containsKey(sub) ? subCounts.get(sub) : 0;
                            if (count < 5) {
                                subCounts.put(sub, count + 1);
                                validVideos.add(p);
                            }
                        }
                        
                        // TRUE RANDOM SHUFFLE
                        Collections.shuffle(validVideos);
                        finalVideos.addAll(validVideos);
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        ReelsAdapter currentAdapter = getCurrentAdapter();
                        currentAdapter.addPosts(finalVideos);
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);

                        if (finalVideos.isEmpty() && newAfter != null) {
                            // No matching videos on this page — fetch the next page
                            fetchVideos();
                        } else if (countBeforeAdd == 0 && !finalVideos.isEmpty()) {
                            // First batch — kick off playback at position 0
                            currentAdapter.playVideoAt(0);
                        }
                    });

                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    isLoading = false;
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }



    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────


    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Activity handles config changes itself — no restart needed.
        // This is intentionally a no-op; the activity continues normally.
    }

    @Override
    protected void onPause() {
        super.onPause();
        ReelsAdapter current = getCurrentAdapter();
        if (current != null) current.pauseCurrentPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dwellRunnable != null) dwellHandler.removeCallbacks(dwellRunnable);
        if (sfwAdapter        != null) sfwAdapter.releasePlayers();
        if (subscribedAdapter != null) subscribedAdapter.releasePlayers();
        if (nsfwAdapter       != null) nsfwAdapter.releasePlayers();
    }

    // ─────────────────────────────────────────────────────────────────────
    // BaseActivity abstract implementations
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        // Fullscreen immersive — no theme chrome needed
    }
    private int getBottomAppBarOptionDrawableResource(int option) {
        switch (option) {
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SUBSCRIPTIONS: return R.drawable.ic_subscriptions_bottom_app_bar_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_MULTIREDDITS: return R.drawable.ic_multi_reddit_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_REELS: return R.drawable.ic_video_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_INBOX: return R.drawable.ic_inbox_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_PROFILE: return R.drawable.ic_account_circle_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SUBMIT_POSTS: return R.drawable.ic_add_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_REFRESH: return R.drawable.ic_refresh_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_CHANGE_SORT_TYPE: return R.drawable.ic_sort_toolbar_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_CHANGE_POST_LAYOUT: return R.drawable.ic_post_layout_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SEARCH: return R.drawable.ic_search_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_GO_TO_SUBREDDIT: return R.drawable.ic_subreddit_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_GO_TO_USER: return R.drawable.ic_user_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_HIDE_READ_POSTS: return R.drawable.ic_hide_read_posts_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_FILTER_POSTS: return R.drawable.ic_filter_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_UPVOTED: return R.drawable.ic_arrow_upward_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_DOWNVOTED: return R.drawable.ic_arrow_downward_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_HIDDEN: return R.drawable.ic_lock_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SAVED: return R.drawable.ic_bookmarks_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SHOW_THUMBNAIL_ON_THE_LEFT: return R.drawable.ic_thumbnail_left_day_night_24dp;
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_GO_TO_TOP:
            default: return R.drawable.ic_keyboard_double_arrow_up_day_night_24dp;
        }
    }

    private void bottomAppBarOptionAction(int option) {
        switch (option) {
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SUBSCRIPTIONS: {
                Intent intent = new Intent(this, SubscribedThingListingActivity.class);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_MULTIREDDITS: {
                Intent intent = new Intent(this, SubscribedThingListingActivity.class);
                intent.putExtra(SubscribedThingListingActivity.EXTRA_SHOW_MULTIREDDITS, true);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_REELS: {
                // Already in Reels
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_INBOX: {
                Intent intent = new Intent(this, InboxActivity.class);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_PROFILE: {
                Intent intent = new Intent(this, ViewUserDetailActivity.class);
                intent.putExtra(ViewUserDetailActivity.EXTRA_USER_NAME_KEY, accountName);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_REFRESH: {
                fetchVideos();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SEARCH: {
                Intent intent = new Intent(this, SearchActivity.class);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_UPVOTED: {
                Intent intent = new Intent(this, AccountPostsActivity.class);
                intent.putExtra(AccountPostsActivity.EXTRA_USER_WHERE, PostPagingSource.USER_WHERE_UPVOTED);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_DOWNVOTED: {
                Intent intent = new Intent(this, AccountPostsActivity.class);
                intent.putExtra(AccountPostsActivity.EXTRA_USER_WHERE, PostPagingSource.USER_WHERE_DOWNVOTED);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_HIDDEN: {
                Intent intent = new Intent(this, AccountPostsActivity.class);
                intent.putExtra(AccountPostsActivity.EXTRA_USER_WHERE, PostPagingSource.USER_WHERE_HIDDEN);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SAVED: {
                Intent intent = new Intent(ReelsActivity.this, AccountSavedThingActivity.class);
                startActivity(intent);
                finish();
                break;
            }
            case SharedPreferencesUtils.MAIN_ACTIVITY_BOTTOM_APP_BAR_OPTION_SHOW_THUMBNAIL_ON_THE_LEFT: {
                boolean newValue = !mSharedPreferences.getBoolean(SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_IN_COMPACT_LAYOUT, false);
                mSharedPreferences.edit().putBoolean(SharedPreferencesUtils.SHOW_THUMBNAIL_ON_THE_LEFT_IN_COMPACT_LAYOUT, newValue).apply();
                EventBus.getDefault().post(new ShowThumbnailOnTheLeftInCompactLayoutEvent(newValue));
                break;
            }
            default: {
                finish();
                break;
            }
        }
    }

}
