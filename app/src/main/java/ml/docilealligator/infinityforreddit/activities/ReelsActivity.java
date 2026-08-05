package ml.docilealligator.infinityforreddit.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.content.Intent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.adapters.ReelsAdapter;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.postfilter.PostFilter;
import ml.docilealligator.infinityforreddit.utils.SeenPostsManager;
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.thing.SaveThing;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.readpost.NullReadPostsList;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class ReelsActivity extends BaseActivity {

    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;

    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;

    @Inject
    CustomThemeWrapper mCustomThemeWrapper;

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;

    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;

    @Inject
    Executor mExecutor;

    private ViewPager2 viewPager;
    private ReelsAdapter sfwAdapter;
    private ReelsAdapter nsfwAdapter;
    private TextView sfwTextView;
    private TextView nsfwTextView;

    private boolean isNsfwMode = false;
    @Nullable
    private String sfwAfter = null;
    @Nullable
    private String nsfwAfter = null;
    private int sfwPosition = 0;
    private int nsfwPosition = 0;
    private boolean isLoading = false;
    
    private String mAccountName;
    private String mAccessToken;

    private final Handler dwellHandler = new Handler(Looper.getMainLooper());
    private Runnable dwellRunnable;
    private int currentPosition = -1;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);

        viewPager = findViewById(R.id.view_pager_reels);
        sfwTextView = findViewById(R.id.sfw_text_view);
        nsfwTextView = findViewById(R.id.nsfw_text_view);

        mAccountName = mSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        mAccessToken = mSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);

        ReelsAdapter.InteractionListener listener = new ReelsAdapter.InteractionListener() {
            @Override
            public void onUpvote(Post post, int position) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId());
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, mAccessToken, new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {}
                    @Override
                    public void onVoteThingFail(int position1) {}
                }, post.getFullName(), APIUtils.DIR_UPVOTE, position);
            }

            @Override
            public void onDownvote(Post post, int position) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId());
                VoteThing.voteThing(ReelsActivity.this, mOauthRetrofit, mAccessToken, new VoteThing.VoteThingListener() {
                    @Override
                    public void onVoteThingSuccess(int position1) {}
                    @Override
                    public void onVoteThingFail(int position1) {}
                }, post.getFullName(), APIUtils.DIR_DOWNVOTE, position);
            }

            @Override
            public void onComments(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId());
                Intent intent = new Intent(ReelsActivity.this, ViewPostDetailActivity.class);
                intent.putExtra(ViewPostDetailActivity.EXTRA_POST, post);
                startActivity(intent);
            }

            @Override
            public void onSave(Post post) {
                if (mAccessToken == null) return;
                SeenPostsManager.markSeen(mSharedPreferences, post.getId());
                SaveThing.saveThing(mOauthRetrofit, mAccessToken, post.getFullName(), new SaveThing.SaveThingListener() {
                    @Override
                    public void onSaveThingSuccess() {}
                    @Override
                    public void onSaveThingFail() {}
                });
            }

            @Override
            public void onShare(Post post) {
                SeenPostsManager.markSeen(mSharedPreferences, post.getId());
                APIUtils.sharePost(ReelsActivity.this, post.getTitle(), post.getPermalink());
            }
        };

        sfwAdapter = new ReelsAdapter(this, listener);
        nsfwAdapter = new ReelsAdapter(this, listener);
        
        viewPager.setAdapter(sfwAdapter);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);

        sfwTextView.setOnClickListener(v -> {
            if (isNsfwMode) {
                nsfwAdapter.releasePlayers();
                isNsfwMode = false;
                updateModeUI();
                viewPager.setAdapter(sfwAdapter);
                viewPager.setCurrentItem(sfwPosition, false);
                if (sfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    sfwAdapter.playVideoAt(sfwPosition);
                }
            }
        });

        nsfwTextView.setOnClickListener(v -> {
            if (!isNsfwMode) {
                sfwAdapter.releasePlayers();
                isNsfwMode = true;
                updateModeUI();
                viewPager.setAdapter(nsfwAdapter);
                viewPager.setCurrentItem(nsfwPosition, false);
                if (nsfwAdapter.getItemCount() == 0) {
                    fetchVideos();
                } else {
                    nsfwAdapter.playVideoAt(nsfwPosition);
                }
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                ReelsAdapter currentAdapter = isNsfwMode ? nsfwAdapter : sfwAdapter;
                if (isNsfwMode) nsfwPosition = position;
                else sfwPosition = position;

                currentAdapter.playVideoAt(position);
                if (position >= currentAdapter.getItemCount() - 5 && !isLoading) {
                    fetchVideos();
                }

                if (dwellRunnable != null) {
                    dwellHandler.removeCallbacks(dwellRunnable);
                }
                currentPosition = position;
                dwellRunnable = () -> {
                    if (currentPosition == position) {
                        Post p = currentAdapter.getPostAt(position);
                        if (p != null && mSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SUBREDDITS_BASE, false)) {
                            SeenPostsManager.markSeen(mSharedPreferences, p.getId());
                        }
                    }
                };
                dwellHandler.postDelayed(dwellRunnable, 3000);
            }
        });

        if (isNsfwMode) {
            viewPager.setAdapter(nsfwAdapter);
        } else {
            viewPager.setAdapter(sfwAdapter);
        }
        fetchVideos();
    }

    private void updateModeUI() {
        if (isNsfwMode) {
            sfwTextView.setTextColor(0x88ffffff);
            sfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
            nsfwTextView.setTextColor(0xffffffff);
            nsfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            sfwTextView.setTextColor(0xffffffff);
            sfwTextView.setTypeface(null, android.graphics.Typeface.BOLD);
            nsfwTextView.setTextColor(0x88ffffff);
            nsfwTextView.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    // resetAndFetch is intentionally removed

    private void fetchVideos() {
        isLoading = true;
        String subreddit;
        List<String> pool = new ArrayList<>();
        if (isNsfwMode) {
            Collections.addAll(pool, NSFW_POOL);
        } else {
            Collections.addAll(pool, SFW_POOL);
        }
        Collections.shuffle(pool);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(pool.get(i));
            if (i < 19) sb.append("+");
        }
        subreddit = sb.toString();
        
        String currentAfter = isNsfwMode ? nsfwAfter : sfwAfter;

        String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        if (accountName == null) accountName = Account.ANONYMOUS_ACCOUNT;
        RedditAPI api = Account.ANONYMOUS_ACCOUNT.equals(accountName) ? mRetrofit.create(RedditAPI.class) : mOauthRetrofit.create(RedditAPI.class);
        
        final String finalAccountName = accountName;
        mExecutor.execute(() -> {
            try {
                retrofit2.Response<String> response;
                if (Account.ANONYMOUS_ACCOUNT.equals(finalAccountName)) {
                    response = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(subreddit, SortType.Type.HOT, null, currentAfter, 100, APIUtils.getUserAgent(this)).get();
                } else {
                    String accessToken = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
                    response = api.getSubredditBestPostsOauthListenableFuture(subreddit, SortType.Type.HOT, null, currentAfter, 100, APIUtils.getOAuthHeader(accessToken)).get();
                }
                
                if (response != null && response.isSuccessful() && response.body() != null) {
                    PostFilter filter = new PostFilter();
                    filter.allowNSFW = true;
                    filter.containVideoType = true;
                    filter.containGifType = true;
                    filter.containTextType = true;
                    filter.containImageType = true;
                    filter.containLinkType = true;
                    filter.containGalleryType = true;
                    
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, filter, NullReadPostsList.getInstance());
                    String newAfter = ParsePost.getLastItem(response.body());
                    if (isNsfwMode) nsfwAfter = newAfter;
                    else sfwAfter = newAfter;
                    
                    List<Post> videos = new ArrayList<>();
                    if (posts != null) {
                        for (Post p : posts) {
                            if (p.getPostType() == Post.VIDEO_TYPE || p.getPostType() == Post.GIF_TYPE) {
                                if (isNsfwMode && !p.isNSFW()) continue;
                                if (mSharedPreferences.getBoolean(SharedPreferencesUtils.HIDE_READ_POSTS_AUTOMATICALLY_IN_SUBREDDITS_BASE, false)) {
                                    if (SeenPostsManager.hasSeen(mSharedPreferences, p.getId())) continue;
                                }
                                
                                videos.add(p);
                            }
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> {
                        ReelsAdapter currentAdapter = isNsfwMode ? nsfwAdapter : sfwAdapter;
                        currentAdapter.addPosts(videos);
                        isLoading = false;
                        if (videos.isEmpty() && newAfter != null) {
                            fetchVideos(); // Fetch more if none were videos
                        } else if (currentAdapter.getItemCount() == videos.size() && videos.size() > 0) {
                            // If this was the first batch, play the first video automatically
                            currentAdapter.playVideoAt(0);
                        }
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> isLoading = false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> isLoading = false);
            }
        });
    }

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
        // Fullscreen immersive mode for Reels
    }
}
