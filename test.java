class Outer {
    void outerMethod() {}
    class Inner {
        void innerMethod() {}
        Runnable r = new Runnable() {
            public void run() {
                innerMethod();
                outerMethod();
            }
        };
    }
}
