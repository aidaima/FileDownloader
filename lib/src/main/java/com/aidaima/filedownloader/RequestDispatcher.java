package com.aidaima.filedownloader;

import android.os.Handler;
import android.os.Process;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.protocol.HTTP;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

public class RequestDispatcher extends Thread {
    private static final String TAG = RequestDispatcher.class.getSimpleName();
    /**
     * The queue of requests to service.
     */
    private final BlockingQueue<DownloadRequest> mQueue;

    /**
     * For posting responses and errors.
     */
    private final Delivery mDelivery;
    /**
     * The default charset only use when response doesn't offer the Content-Type header.
     */
    private final String mDefaultCharset;
    /**
     * Used for telling us to die.
     */
    private volatile boolean mQuit = false;

    /**
     * Creates a new network dispatcher thread.  You must call {@link #start()}
     * in order to begin processing.
     *
     * @param queue    Queue of incoming requests for triage
     * @param delivery Delivery interface to use for posting responses
     */
    public RequestDispatcher(BlockingQueue<DownloadRequest> queue, Delivery delivery) {
        mQueue = queue;
        mDelivery = delivery;

        mDefaultCharset = HTTP.UTF_8;
    }

    /**
     * Forces this dispatcher to quit immediately.  If any requests are still in
     * the queue, they are not guaranteed to be processed.
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        DownloadRequest request;
        while (true) {
            try {
                // Take a request from the queue.
                request = mQueue.take();
            } catch (InterruptedException e) {
                // We may have been interrupted because it was time to quit.
                if (mQuit) return;
                continue;
            }

            try {
                mDelivery.postPreExecute(request);

                // Perform the network request.
                request.performRequest(mDelivery);

                // Post the response back.
                mDelivery.postResponse(request);
            } catch (NetworkException.NetworkError networkError) {
                mDelivery.postError(request, networkError);
            } catch (Exception e) {
                Log.e(TAG, "Unhandled exception " + e.toString());
                mDelivery.postError(request, new NetworkException(e));
            }
        }
    }

    /**
     * Returns the charset specified in the Content-Type of this header,
     * or the defaultCharset if none can be found.
     */
    private String parseCharset(HttpResponse response) {
        String charset = HttpUtils.getCharset(response);
        return charset == null ? mDefaultCharset : charset;
    }

    public static class Delivery {
        /**
         * Used for posting responses, typically to the main thread.
         */
        private final Executor mResponsePoster;

        /**
         * Creates a new response delivery interface.
         *
         * @param handler {@link Handler} to post responses on
         */
        public Delivery(final Handler handler) {
            // Make an Executor that just wraps the handler.
            mResponsePoster = new Executor() {
                @Override
                public void execute(Runnable command) {
                    handler.post(command);
                }
            };
        }

        public void postFinish(final DownloadRequest request) {
            mResponsePoster.execute(new Runnable() {
                @Override
                public void run() {
                    request.deliverFinish();
                }
            });
        }

        public void postResponse(DownloadRequest request) {
            postResponse(request, null);
        }

        public void postResponse(DownloadRequest request, Runnable runnable) {
            mResponsePoster.execute(new ResponseDeliveryRunnable(request, runnable));
        }

        public void postError(DownloadRequest request, NetworkException error) {
            request.error = error;
            mResponsePoster.execute(new ResponseDeliveryRunnable(request, null));
        }

        public void postPreExecute(final DownloadRequest request) {
            mResponsePoster.execute(new Runnable() {
                @Override
                public void run() {
                    request.deliverPreExecute();
                }
            });
        }

        public void postDownloadProgress(final DownloadRequest request, final long fileSize, final long downloadedSize) {
            mResponsePoster.execute(new Runnable() {
                @Override
                public void run() {
                    request.deliverDownloadProgress(fileSize, downloadedSize);
                }
            });
        }

        /**
         * A Runnable used for delivering network responses to a listener on the
         * main thread.
         */
        @SuppressWarnings("rawtypes")
        private class ResponseDeliveryRunnable implements Runnable {
            private final DownloadRequest mRequest;
            private final Runnable mRunnable;

            public ResponseDeliveryRunnable(DownloadRequest request, Runnable runnable) {
                mRequest = request;
                mRunnable = runnable;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                // Deliver a normal response or error, depending.
                if (mRequest.isSuccess()) {
                    mRequest.deliverSuccess();
                } else {
                    mRequest.deliverError(mRequest.error);
                }

                // we're done and the request can be finished.
                mRequest.finish("done");

                // If we have been provided a post-delivery runnable, run it.
                if (mRunnable != null) {
                    mRunnable.run();
                }

                if (mRequest.isSuccess() ||
                        !(mRequest.error instanceof NetworkException.NetworkCancel)) {
                    mRequest.deliverFinish();
                }
            }
        }
    }
}