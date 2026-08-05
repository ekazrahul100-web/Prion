import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'r') as f:
    content = f.read()

# Fix the vote type comparisons
content = content.replace("post.getVoteType() == APIUtils.DIR_UPVOTE", "String.valueOf(post.getVoteType()).equals(APIUtils.DIR_UPVOTE)")
content = content.replace("post.getVoteType() == APIUtils.DIR_DOWNVOTE", "String.valueOf(post.getVoteType()).equals(APIUtils.DIR_DOWNVOTE)")
content = content.replace("post.getVoteType() != APIUtils.DIR_UPVOTE", "!String.valueOf(post.getVoteType()).equals(APIUtils.DIR_UPVOTE)")
content = content.replace("post.getVoteType() != APIUtils.DIR_DOWNVOTE", "!String.valueOf(post.getVoteType()).equals(APIUtils.DIR_DOWNVOTE)")

# Fix the post.setVoteType calls
content = content.replace("post.setVoteType(APIUtils.DIR_UPVOTE)", "post.setVoteType(1)")
content = content.replace("post.setVoteType(APIUtils.DIR_DOWNVOTE)", "post.setVoteType(-1)")
content = content.replace("post.setVoteType(APIUtils.DIR_UNVOTE)", "post.setVoteType(0)")

# Fix the colors
content = content.replace("ContextCompat.getColor(context, R.color.upvoted)", "android.graphics.Color.parseColor(\"#FF8B60\")")
content = content.replace("ContextCompat.getColor(context, R.color.downvoted)", "android.graphics.Color.parseColor(\"#9494FF\")")
content = content.replace("ContextCompat.getColor(context, R.color.saved)", "android.graphics.Color.parseColor(\"#FFEB3B\")")

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'w') as f:
    f.write(content)

