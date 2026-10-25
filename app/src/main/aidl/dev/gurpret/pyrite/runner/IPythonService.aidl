package dev.gurpret.pyrite.runner;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import dev.gurpret.pyrite.runner.IPythonCallback;

/** The :py process. Each process runs exactly one request, then is killed. */
interface IPythonService {
    int getPid();

    /**
     * False if this process already ran Python. Android can reuse a cached process
     * for a new binding (e.g. after the client process died); CPython can't be
     * initialized twice, so the client must kill it and bind again.
     */
    boolean isFresh();

    /**
     * Starts the run described by [request] (see RunRequest). The descriptors are
     * the child's ends of three pipes; they become its fds 0, 1 and 2.
     */
    void start(in Bundle request, in ParcelFileDescriptor stdin, in ParcelFileDescriptor stdout,
               in ParcelFileDescriptor stderr, IPythonCallback callback);

    /** KeyboardInterrupt in the running code. */
    void interrupt();
}
