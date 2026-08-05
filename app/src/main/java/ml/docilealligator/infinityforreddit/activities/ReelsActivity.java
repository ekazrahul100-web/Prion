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
import ml.docilealligator.infinityforreddit.thing.SortType;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.NullReadPostsList;
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
    Retrofit mRetrofit;

    @Inject
    Executor mExecutor;

    private ViewPager2 viewPager;
    private ReelsAdapter adapter;
    private TextView sfwTextView;
    private TextView nsfwTextView;

    private boolean isNsfwMode = false;
    private String after = null;
    private boolean isLoading = false;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reels);

        viewPager = findViewById(R.id.view_pager_reels);
        sfwTextView = findViewById(R.id.sfw_text_view);
        nsfwTextView = findViewById(R.id.nsfw_text_view);

        adapter = new ReelsAdapter(this);
        viewPager.setAdapter(adapter);

        sfwTextView.setOnClickListener(v -> {
            if (isNsfwMode) {
                isNsfwMode = false;
                updateModeUI();
                resetAndFetch();
            }
        });

        nsfwTextView.setOnClickListener(v -> {
            if (!isNsfwMode) {
                isNsfwMode = true;
                updateModeUI();
                resetAndFetch();
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                adapter.playVideoAt(position);
                if (position >= adapter.getItemCount() - 5 && !isLoading) {
                    fetchVideos();
                }
            }
        });

        resetAndFetch();
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

    private void resetAndFetch() {
        adapter.clear();
        after = null;
        fetchVideos();
    }

    private void fetchVideos() {
        isLoading = true;
        String subreddit;
        if (isNsfwMode) {
            List<String> pool = new ArrayList<>();
            Collections.addAll(pool, NSFW_POOL);
            Collections.shuffle(pool);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append(pool.get(i));
                if (i < 19) sb.append("+");
            }
            subreddit = sb.toString();
        } else {
            subreddit = "popular";
        }

        String accountName = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        RedditAPI api = accountName.equals(Account.ANONYMOUS_ACCOUNT) ? mRetrofit.create(RedditAPI.class) : mOauthRetrofit.create(RedditAPI.class);
        
        mExecutor.execute(() -> {
            try {
                retrofit2.Response<String> response;
                if (accountName.equals(Account.ANONYMOUS_ACCOUNT)) {
                    response = api.getAnonymousFrontPageOrMultiredditPostsListenableFuture(subreddit, SortType.Type.HOT, null, after, 100, APIUtils.getUserAgent(this)).get();
                } else {
                    response = api.getSubredditBestPostsOauthListenableFuture(subreddit, SortType.Type.HOT, null, after, 100, APIUtils.getOAuthHeader(this)).get();
                }
                
                if (response != null && response.isSuccessful() && response.body() != null) {
                    LinkedHashSet<Post> posts = ParsePost.parsePostsSync(response.body(), -1, null, NullReadPostsList.getInstance());
                    after = ParsePost.getLastItem(response.body());
                    
                    List<Post> videos = new ArrayList<>();
                    for (Post p : posts) {
                        if (p.getPostType() == Post.VIDEO_TYPE || p.getPostType() == Post.GIF_TYPE) {
                            if (isNsfwMode && !p.isNSFW()) continue;
                            videos.add(p);
                        }
                    }
                    
                    new Handler(Looper.getMainLooper()).post(() -> {
                        adapter.addPosts(videos);
                        isLoading = false;
                        if (videos.isEmpty() && after != null) {
                            fetchVideos(); // Fetch more if none were videos
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
