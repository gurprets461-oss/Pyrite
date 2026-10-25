package dev.gurpret.pyrite.runner;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import dev.gurpret.pyrite.runner.IPythonCallback;

/** The :py process. Each process runs exactly one request, then is killed. */
interface IPythonService {
    int getPid();

    /**
     * Starts the run described by [request] (see RunRequest). The descriptors are
     * the child's ends of three pipes; they become its fds 0, 1 and 2.
     */
    void start(in Bundle request, in ParcelFileDescriptor stdin, in ParcelFileDescriptor stdout,
               in ParcelFileDescriptor stderr, IPythonCallback callback);

    /** KeyboardInterrupt in the running code. */
    void interrupt();
}
