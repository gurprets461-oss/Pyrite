package dev.gurpret.pyrite.runner;

oneway interface IPythonCallback {
    /** Python is initialized and about to run the request. */
    void onStarted();

    /** The request finished with this exit status (SystemExit code, 1 for an exception). */
    void onExit(int status);
}
