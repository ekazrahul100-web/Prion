import os, glob

files = glob.glob('/data/data/com.termux/files/home/Prion/app/src/main/java/ml/docilealligator/infinityforreddit/activities/*.java')

for f in files:
    with open(f, 'r') as file:
        content = file.read()
    
    if "public void markPostAsRead(Post post) {" in content:
        replacement = """public void markPostAsRead(Post post) {
        ml.docilealligator.infinityforreddit.utils.SeenPostsManager.markSeen(getDefaultSharedPreferences(), post.getId());"""
        
        # Need to fix getDefaultSharedPreferences() since not all activities have it directly, wait, they all implement MarkPostAsReadInterface but do they have mSharedPreferences?
        # Actually it's mSharedPreferences in MainActivity.
