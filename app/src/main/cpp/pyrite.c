// JNI bridge between the app and libpython.
//
// Threading: initialize() and every run*() call must happen on the same thread
// (the app's dedicated "python-main" thread). That thread holds the GIL for the
// life of the process. interrupt() may be called from any thread.

// Python.h must come before any standard header.
#define PY_SSIZE_T_CLEAN
#include <Python.h>

#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define TAG "pyrite"

static pthread_t python_thread;
static volatile int python_thread_valid = 0;

static void throw_new(JNIEnv *env, const char *class_name, const char *fmt, ...) {
    char message[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof message, fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message);
    jclass cls = (*env)->FindClass(env, class_name);
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

// Copies a Java byte[] (UTF-8 text, not JNI's "modified UTF-8") into a
// NUL-terminated C string. Returns NULL with an exception pending on failure.
static char *bytes_to_cstr(JNIEnv *env, jbyteArray array) {
    jsize len = (*env)->GetArrayLength(env, array);
    char *s = malloc((size_t)len + 1);
    if (s == NULL) {
        throw_new(env, "java/lang/OutOfMemoryError", "malloc failed");
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, array, 0, len, (jbyte *)s);
    s[len] = '\0';
    return s;
}

// --- stdio ------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_redirectStdio(
    JNIEnv *env, jclass cls, jint stdin_fd, jint stdout_fd, jint stderr_fd
) {
    const int fds[3] = {stdin_fd, stdout_fd, stderr_fd};
    for (int target = 0; target < 3; target++) {
        if (fds[target] < 0) {
            continue;
        }
        if (dup2(fds[target], target) < 0) {
            throw_new(env, "java/io/IOException", "dup2(%d, %d): %s",
                      fds[target], target, strerror(errno));
            return;
        }
    }
    // C-level stdio must not hold output back either.
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
}

// --- initialization -----------------------------------------------------------

static int check_status(JNIEnv *env, PyStatus status, const char *what) {
    if (PyStatus_Exception(status)) {
        throw_new(env, "java/lang/RuntimeException", "%s failed: %s", what,
                  status.err_msg ? status.err_msg : "(no message)");
        return 0;
    }
    return 1;
}

JNIEXPORT void JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_initialize(
    JNIEnv *env, jclass cls, jstring jhome, jstring jexecutable, jobjectArray jargv
) {
    if (Py_IsInitialized()) {
        throw_new(env, "java/lang/IllegalStateException", "Python is already initialized");
        return;
    }

    // Signals Python relies on may be blocked on threads Android creates.
    sigset_t set;
    sigemptyset(&set);
    sigaddset(&set, SIGINT);
    sigaddset(&set, SIGUSR1);
    pthread_sigmask(SIG_UNBLOCK, &set, NULL);

    PyPreConfig preconfig;
    PyPreConfig_InitPythonConfig(&preconfig);
    preconfig.utf8_mode = 1;
    if (!check_status(env, Py_PreInitialize(&preconfig), "Py_PreInitialize")) {
        return;
    }

    PyConfig config;
    PyConfig_InitPythonConfig(&config);
    config.parse_argv = 0;
    config.install_signal_handlers = 1;  // SIGINT -> KeyboardInterrupt
    config.buffered_stdio = 0;           // like -u: output reaches the console at once
    config.faulthandler = 1;             // dump Python tracebacks on fatal signals

    const char *home = (*env)->GetStringUTFChars(env, jhome, NULL);
    const char *executable = (*env)->GetStringUTFChars(env, jexecutable, NULL);
    jsize argc = (*env)->GetArrayLength(env, jargv);
    char **argv = calloc((size_t)argc + 1, sizeof(char *));
    for (jsize i = 0; i < argc; i++) {
        jstring arg = (jstring)(*env)->GetObjectArrayElement(env, jargv, i);
        const char *utf = (*env)->GetStringUTFChars(env, arg, NULL);
        argv[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, arg, utf);
        (*env)->DeleteLocalRef(env, arg);
    }

    int ok = check_status(env, PyConfig_SetBytesArgv(&config, argc, argv), "set argv")
        && check_status(env, PyConfig_SetBytesString(&config, &config.home, home), "set home")
        // A non-empty sys.executable stops CPython's Android startup code from
        // redirecting sys.stdout/stderr to logcat, so they stay on fds 1/2.
        && check_status(env, PyConfig_SetBytesString(&config, &config.executable, executable),
                        "set executable");

    for (jsize i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);
    (*env)->ReleaseStringUTFChars(env, jhome, home);
    (*env)->ReleaseStringUTFChars(env, jexecutable, executable);

    if (ok) {
        python_thread = pthread_self();
        python_thread_valid = 1;
        ok = check_status(env, Py_InitializeFromConfig(&config), "Py_InitializeFromConfig");
        if (!ok) {
            python_thread_valid = 0;
        }
    }
    PyConfig_Clear(&config);
}

// --- running code ----------------------------------------------------------------

static void flush_std_streams(void) {
    PyObject *exc = PyErr_GetRaisedException();
    const char *names[] = {"stdout", "stderr"};
    for (int i = 0; i < 2; i++) {
        PyObject *stream = PySys_GetObject(names[i]);  // borrowed
        if (stream != NULL && stream != Py_None) {
            PyObject *r = PyObject_CallMethod(stream, "flush", NULL);
            if (r == NULL) {
                PyErr_Clear();
            } else {
                Py_DECREF(r);
            }
        }
    }
    PyErr_SetRaisedException(exc);
}

// Converts the pending exception into a process-style exit status. SystemExit
// gives its code (printing a non-integer code, as Python does); anything else
// prints the traceback. Clears the exception.
static int handle_exception(void) {
    if (PyErr_ExceptionMatches(PyExc_SystemExit)) {
        PyObject *exc = PyErr_GetRaisedException();
        PyObject *code = PyObject_GetAttrString(exc, "code");
        int status;
        if (code == NULL) {
            PyErr_Clear();
            status = 1;
        } else if (code == Py_None) {
            status = 0;
        } else if (PyLong_Check(code)) {
            status = (int)PyLong_AsLong(code);
            if (status == -1 && PyErr_Occurred()) {
                PyErr_Clear();
                status = 1;
            }
        } else {
            PyObject *err = PySys_GetObject("stderr");
            if (err != NULL && err != Py_None) {
                if (PyFile_WriteObject(code, err, Py_PRINT_RAW) == 0) {
                    PyFile_WriteString("\n", err);
                }
            }
            PyErr_Clear();
            status = 1;
        }
        Py_XDECREF(code);
        Py_DECREF(exc);
        return status;
    }
    int interrupted = PyErr_ExceptionMatches(PyExc_KeyboardInterrupt);
    PyErr_Print();
    return interrupted ? 130 : 1;
}

JNIEXPORT jint JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_runString(
    JNIEnv *env, jclass cls, jbyteArray jcode, jstring jfilename
) {
    if (!Py_IsInitialized()) {
        throw_new(env, "java/lang/IllegalStateException", "Python is not initialized");
        return -1;
    }
    char *code = bytes_to_cstr(env, jcode);
    if (code == NULL) {
        return -1;
    }
    const char *filename = (*env)->GetStringUTFChars(env, jfilename, NULL);

    int status = 0;
    PyObject *main_module = PyImport_AddModule("__main__");  // borrowed
    if (main_module == NULL) {
        status = handle_exception();
    } else {
        PyObject *globals = PyModule_GetDict(main_module);  // borrowed
        PyObject *compiled = Py_CompileString(code, filename, Py_file_input);
        if (compiled == NULL) {
            status = handle_exception();
        } else {
            PyObject *result = PyEval_EvalCode(compiled, globals, globals);
            Py_DECREF(compiled);
            if (result == NULL) {
                status = handle_exception();
            } else {
                Py_DECREF(result);
            }
        }
    }
    flush_std_streams();

    (*env)->ReleaseStringUTFChars(env, jfilename, filename);
    free(code);
    return status;
}

// Runs a script file as __main__. The caller sets sys.argv, sys.path[0] and the
// working directory first.
JNIEXPORT jint JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_runFile(JNIEnv *env, jclass cls, jbyteArray jpath) {
    if (!Py_IsInitialized()) {
        throw_new(env, "java/lang/IllegalStateException", "Python is not initialized");
        return -1;
    }
    char *path = bytes_to_cstr(env, jpath);
    if (path == NULL) {
        return -1;
    }

    int status = 0;
    FILE *fp = fopen(path, "rb");
    if (fp == NULL) {
        fprintf(stderr, "pyrite: can't open file '%s': [Errno %d] %s\n", path, errno, strerror(errno));
        free(path);
        return 2;
    }

    PyObject *main_module = PyImport_AddModule("__main__");  // borrowed
    PyObject *globals = main_module ? PyModule_GetDict(main_module) : NULL;
    PyObject *filename = PyUnicode_DecodeFSDefault(path);
    if (globals == NULL || filename == NULL
        || PyDict_SetItemString(globals, "__file__", filename) < 0
        || PyDict_SetItemString(globals, "__cached__", Py_None) < 0) {
        fclose(fp);
        status = handle_exception();
    } else {
        PyObject *result = PyRun_FileExFlags(fp, path, Py_file_input, globals, globals, 1, NULL);
        if (result == NULL) {
            status = handle_exception();
        } else {
            Py_DECREF(result);
        }
    }
    Py_XDECREF(filename);
    flush_std_streams();
    free(path);
    return status;
}

// --- control ----------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_interrupt(JNIEnv *env, jclass cls) {
    if (!python_thread_valid) {
        return;
    }
    // A real SIGINT aimed at the Python thread, like Ctrl+C in a terminal. Python's
    // handler raises KeyboardInterrupt at the next bytecode boundary, and if the
    // thread is blocked in a system call (input(), time.sleep(), socket reads) the
    // call returns EINTR first. (PyErr_SetInterrupt() alone can't wake a blocked
    // thread, and using both could raise KeyboardInterrupt twice.)
    pthread_kill(python_thread, SIGINT);
}

JNIEXPORT jboolean JNICALL
Java_dev_gurpret_pyrite_python_NativeBridge_isInitialized(JNIEnv *env, jclass cls) {
    return Py_IsInitialized() ? JNI_TRUE : JNI_FALSE;
}
