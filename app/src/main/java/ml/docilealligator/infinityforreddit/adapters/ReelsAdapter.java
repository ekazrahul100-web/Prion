package ml.docilealligator.infinityforreddit.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.post.Post;


public class ReelsAdapter extends RecyclerView.Adapter<ReelsAdapter.ReelViewHolder> {

    public interface InteractionListener {
        void onUpvote(Post post, int position);
        void onDownvote(Post post, int position);
        void onComments(Post post);
        void onSave(Post post);
        void onShare(Post post);
        /** Called when the user taps "View post" or swipes left. */
        void onOpenPost(Post post);
    }

    private static final int COLOR_UPVOTED   = 0xFFFF8B60;
    private static final int COLOR_DOWNVOTED  = 0xFF9494FF;
    private static final int COLOR_SAVED      = 0xFFFFEB3B;
    private static final int COLOR_DEFAULT    = 0xFFFFFFFF;

    /** Minimum horizontal swipe velocity (px/s) to trigger open-post. */
    private static final int SWIPE_VELOCITY_THRESHOLD = 500;
    /** Minimum horizontal distance (px) to be considered a swipe. */
    private static final int SWIPE_DISTANCE_THRESHOLD = 80;

    private final Context context;
    private final InteractionListener listener;
    private final List<Post> posts = new ArrayList<>();

    private int currentPlayingPosition = -1;
    private final Map<Integer, ExoPlayer> players = new HashMap<>();
    private boolean isMuted = false;

    /** Per-position resize mode (AspectRatioFrameLayout.RESIZE_MODE_*). Default: FIXED_WIDTH. */
    private final Map<Integer, Integer> resizeModes = new HashMap<>();
    /** Per-position auto-advance callbacks (called when video ends). */
    private final Map<Integer, Runnable> autoAdvanceListeners = new HashMap<>();

    public ReelsAdapter(Context context, InteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /**
     * Apply a resize mode to the PlayerView at [position] immediately.
     * The mode is stored so it survives rebinds.
     */
    public void setResizeMode(int resizeMode, int position) {
        resizeModes.put(position, resizeMode);
        notifyItemChanged(position);
    }

    /**
     * Register a callback that fires when the video at [position] finishes playing.
     * Used for auto-advance.
     */
    public void setAutoAdvanceListener(int position, @Nullable Runnable callback) {
        if (callback == null) {
            autoAdvanceListeners.remove(position);
        } else {
            autoAdvanceListeners.put(position, callback);
        }
    }

    public void addPosts(List<Post> newPosts) {
        int start = posts.size();
        posts.addAll(newPosts);
        notifyItemRangeInserted(start, newPosts.size());
    }

    @Nullable
    public Post getPostAt(int position) {
        if (position >= 0 && position < posts.size()) {
            return posts.get(position);
        }
        return null;
    }

    public void releasePlayers() {
        for (ExoPlayer player : players.values()) {
            if (player != null) {
                player.stop();
                player.release();
            }
        }
        players.clear();
    }

    public void clear() {
        releasePlayers();
        posts.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_reel, parent, false);
        return new ReelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
        Post post = posts.get(position);

        holder.titleText.setText(post.getTitle());
        holder.subredditText.setText("r/" + post.getSubredditName());
        holder.scoreText.setText(String.valueOf(post.getScore()));

        updateVoteUI(holder, post);
        updateSaveUI(holder, post);

        ExoPlayer existingPlayer = players.get(position);
        holder.playerView.setPlayer(existingPlayer);

        // Apply stored resize mode (set by ReelsActivity based on landscape mode setting)
        int resizeMode = resizeModes.containsKey(position)
                ? resizeModes.get(position)
                : AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
        holder.playerView.setResizeModeRaw(resizeMode);

        // Attach auto-advance listener if present
        if (existingPlayer != null) {
            Runnable advanceCallback = autoAdvanceListeners.get(position);
            if (advanceCallback != null) {
                existingPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_ENDED) {
                            existingPlayer.removeListener(this);
                            advanceCallback.run();
                        }
                    }
                });
            }
        }

        holder.pauseIndicator.setVisibility(View.GONE);
        holder.muteButton.setImageResource(isMuted ? R.drawable.ic_mute_24dp : R.drawable.ic_unmute_24dp);
    }


    private void updateVoteUI(ReelViewHolder holder, Post post) {
        int voteType = post.getVoteType();
        holder.upvoteButton.setColorFilter(voteType == 1 ? COLOR_UPVOTED : COLOR_DEFAULT,
                android.graphics.PorterDuff.Mode.SRC_IN);
        holder.downvoteButton.setColorFilter(voteType == -1 ? COLOR_DOWNVOTED : COLOR_DEFAULT,
                android.graphics.PorterDuff.Mode.SRC_IN);
        holder.scoreText.setTextColor(voteType == 1 ? COLOR_UPVOTED
                : (voteType == -1 ? COLOR_DOWNVOTED : COLOR_DEFAULT));
    }

    private void updateSaveUI(ReelViewHolder holder, Post post) {
        holder.saveButton.setColorFilter(post.isSaved() ? COLOR_SAVED : COLOR_DEFAULT,
                android.graphics.PorterDuff.Mode.SRC_IN);
    }

    @Override
    public void onViewRecycled(@NonNull ReelViewHolder holder) {
        super.onViewRecycled(holder);
        holder.playerView.setPlayer(null);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ReelViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.startUpdatingProgress();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ReelViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.stopUpdatingProgress();
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void playVideoAt(int position) {
        currentPlayingPosition = position;

        // Release players outside the [-1, +1] window
        List<Integer> toRemove = new ArrayList<>();
        for (Integer pos : players.keySet()) {
            if (pos < position - 1 || pos > position + 1) {
                toRemove.add(pos);
            }
        }
        for (Integer pos : toRemove) {
            ExoPlayer p = players.remove(pos);
            if (p != null) {
                p.stop();
                p.release();
            }
        }

        // Create or update players for [position-1, position, position+1]
        for (int i = position - 1; i <= position + 1; i++) {
            if (i >= 0 && i < posts.size()) {
                if (!players.containsKey(i)) {
                    Post post = posts.get(i);
                    String url = post.getVideoUrl();
                    if (url == null || url.isEmpty()) {
                        url = post.getUrl();
                    }
                    if (url == null || url.isEmpty()) continue;

                    ExoPlayer player = new ExoPlayer.Builder(context).build();
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);

                    DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);

                    MediaSource mediaSource;
                    if (url.contains(".m3u8") || url.contains("v.redd.it")) {
                        mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(Uri.parse(url)));
                    } else {
                        mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(Uri.parse(url)));
                    }

                    player.setMediaSource(mediaSource);
                    player.prepare();
                    player.setVolume(isMuted ? 0f : 1f);
                    player.setPlayWhenReady(i == position);
                    players.put(i, player);
                    notifyItemChanged(i);
                } else {
                    ExoPlayer p = players.get(i);
                    if (p != null) {
                        p.setVolume(isMuted ? 0f : 1f);
                        p.setPlayWhenReady(i == position);
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────────────────

    class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView titleText;
        TextView subredditText;
        TextView scoreText;
        ImageView likeAnimation;
        ImageView upvoteButton;
        ImageView downvoteButton;
        ImageView commentsButton;
        ImageView saveButton;
        ImageView shareButton;
        ImageView pauseIndicator;
        ImageView muteButton;
        LinearLayout openPostHint;
        SeekBar seekBar;

        private GestureDetector gestureDetector;
        private final Handler uiHandler = new Handler(Looper.getMainLooper());
        private final Runnable updateProgressAction = new Runnable() {
            @Override
            public void run() {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    ExoPlayer player = players.get(pos);
                    if (player != null && player.isPlaying()) {
                        long duration = player.getDuration();
                        if (duration > 0) {
                            seekBar.setMax((int) duration);
                            seekBar.setProgress((int) player.getCurrentPosition());
                        }
                    }
                }
                uiHandler.postDelayed(this, 100);
            }
        };

        public void startUpdatingProgress() {
            uiHandler.removeCallbacks(updateProgressAction);
            uiHandler.post(updateProgressAction);
        }

        public void stopUpdatingProgress() {
            uiHandler.removeCallbacks(updateProgressAction);
        }

        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView    = itemView.findViewById(R.id.player_view);
            titleText     = itemView.findViewById(R.id.title_text);
            subredditText = itemView.findViewById(R.id.subreddit_text);
            scoreText     = itemView.findViewById(R.id.score_text);
            likeAnimation = itemView.findViewById(R.id.like_animation);
            upvoteButton  = itemView.findViewById(R.id.upvote_button);
            downvoteButton = itemView.findViewById(R.id.downvote_button);
            commentsButton = itemView.findViewById(R.id.comments_button);
            saveButton     = itemView.findViewById(R.id.save_button);
            shareButton    = itemView.findViewById(R.id.share_button);
            pauseIndicator = itemView.findViewById(R.id.pause_indicator);
            muteButton     = itemView.findViewById(R.id.mute_button);
            openPostHint   = itemView.findViewById(R.id.open_post_hint);
            seekBar        = itemView.findViewById(R.id.seek_bar);

            // ── Mute toggle ──────────────────────────────────
            muteButton.setOnClickListener(v -> {
                isMuted = !isMuted;
                for (ExoPlayer p : players.values()) {
                    if (p != null) p.setVolume(isMuted ? 0f : 1f);
                }
                muteButton.setImageResource(isMuted ? R.drawable.ic_mute_24dp : R.drawable.ic_unmute_24dp);
            });

            // ── Seek bar ─────────────────────────────────────
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int pos = getBindingAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            ExoPlayer player = players.get(pos);
                            if (player != null) player.seekTo(progress);
                        }
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            // ── Vote buttons ─────────────────────────────────
            upvoteButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= posts.size()) return;
                Post post = posts.get(pos);
                if (post.getVoteType() == 1) {
                    post.setVoteType(0);
                    post.setScore(post.getScore() - 1);
                } else {
                    post.setScore(post.getScore() + (post.getVoteType() == -1 ? 2 : 1));
                    post.setVoteType(1);
                }
                updateVoteUI(this, post);
                listener.onUpvote(post, pos);
            });

            downvoteButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= posts.size()) return;
                Post post = posts.get(pos);
                if (post.getVoteType() == -1) {
                    post.setVoteType(0);
                    post.setScore(post.getScore() + 1);
                } else {
                    post.setScore(post.getScore() - (post.getVoteType() == 1 ? 2 : 1));
                    post.setVoteType(-1);
                }
                updateVoteUI(this, post);
                listener.onDownvote(post, pos);
            });

            commentsButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < posts.size()) {
                    listener.onComments(posts.get(pos));
                }
            });

            saveButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos >= posts.size()) return;
                Post post = posts.get(pos);
                post.setSaved(!post.isSaved());
                updateSaveUI(this, post);
                listener.onSave(post);
            });

            shareButton.setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < posts.size()) {
                    listener.onShare(posts.get(pos));
                }
            });

            // ── "View post" tap target ────────────────────────
            openPostHint.setOnClickListener(v -> openCurrentPost());

            // ── Gesture detector: double-tap to like, single-tap to pause/play,
            //    swipe-left to open post ────────────────────────────────────────
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION || pos >= posts.size()) return true;
                    Post post = posts.get(pos);
                    showLikeAnimation();
                    if (post.getVoteType() != 1) {
                        post.setScore(post.getScore() + (post.getVoteType() == -1 ? 2 : 1));
                        post.setVoteType(1);
                        updateVoteUI(ReelViewHolder.this, post);
                        listener.onUpvote(post, pos);
                    }
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    int pos = getBindingAdapterPosition();
                    ExoPlayer player = players.get(pos);
                    if (player != null && pos == currentPlayingPosition) {
                        if (player.isPlaying()) {
                            player.pause();
                            pauseIndicator.setVisibility(View.VISIBLE);
                        } else {
                            player.play();
                            pauseIndicator.setVisibility(View.GONE);
                        }
                    }
                    return true;
                }

                /**
                 * Swipe LEFT (negative velocityX, larger than threshold) → open post.
                 * The ViewPager2 only consumes VERTICAL flings, so horizontal flings
                 * reach us here.
                 */
                @Override
                public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                       float velocityX, float velocityY) {
                    if (e1 == null) return false;
                    float deltaX = e2.getX() - e1.getX();
                    float deltaY = e2.getY() - e1.getY();
                    // Must be more horizontal than vertical, fast enough, and left-ward
                    if (Math.abs(deltaX) > Math.abs(deltaY)
                            && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                            && deltaX < -SWIPE_DISTANCE_THRESHOLD) {
                        openCurrentPost();
                        return true;
                    }
                    return false;
                }
            });

            playerView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }

        private void openCurrentPost() {
            int pos = getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < posts.size()) {
                listener.onOpenPost(posts.get(pos));
            }
        }

        private void showLikeAnimation() {
            likeAnimation.setVisibility(View.VISIBLE);
            likeAnimation.setScaleX(0f);
            likeAnimation.setScaleY(0f);
            likeAnimation.animate()
                    .scaleX(2f).scaleY(2f)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        likeAnimation.setVisibility(View.GONE);
                        likeAnimation.setAlpha(1f);
                        likeAnimation.setScaleX(1f);
                        likeAnimation.setScaleY(1f);
                    })
                    .start();
        }
    }
}
