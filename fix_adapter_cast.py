import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'r') as f:
    content = f.read()

content = content.replace("holder.upvoteButton.setColorFilter", "((android.widget.ImageView) holder.upvoteButton).setColorFilter")
content = content.replace("holder.downvoteButton.setColorFilter", "((android.widget.ImageView) holder.downvoteButton).setColorFilter")
content = content.replace("holder.saveButton.setColorFilter", "((android.widget.ImageView) holder.saveButton).setColorFilter")

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/adapters/ReelsAdapter.java', 'w') as f:
    f.write(content)

