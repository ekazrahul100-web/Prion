import re

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'r') as f:
    content = f.read()

# Fix ReadPostsList constructor
bad_call = "new ReadPostsList(ml.docilealligator.infinityforreddit.database.RedditDataRoomDatabase.getDatabase(ReelsActivity.this).readPostDao(), finalAccountName, ml.docilealligator.infinityforreddit.readpost.ReadPostType.INVALID);"
good_call = "new ReadPostsList(ml.docilealligator.infinityforreddit.database.RedditDataRoomDatabase.getDatabase(ReelsActivity.this).readPostDao(), finalAccountName, false);"

content = content.replace(bad_call, good_call)

with open('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/ReelsActivity.java', 'w') as f:
    f.write(content)
