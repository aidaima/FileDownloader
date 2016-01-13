package com.aidaima.filedownloader;

import android.os.Looper;
import android.util.Log;

import java.util.LinkedList;

public class FileDownloader {

    private final RequestQueue mRequestQueue;

    private final int mParallelTaskCount;

    private final LinkedList<DownloadController> mTaskQueue;


    private FileDownloader(RequestQueue queue, int parallelTaskCount) {
        if (queue == null) {
            queue = new RequestQueue(RequestQueue.DEFAULT_NETWORK_THREAD_POOL_SIZE);
        }

        if (parallelTaskCount >= queue.getThreadPoolSize()) {
            throw new IllegalArgumentException("parallelTaskCount[" + parallelTaskCount
                    + "] must less than threadPoolSize[" + queue.getThreadPoolSize() + "] of the RequestQueue.");
        }

        mTaskQueue = new LinkedList<DownloadController>();
        mParallelTaskCount = parallelTaskCount;
        mRequestQueue = queue;

        mRequestQueue.start();
    }

    public FileDownloader(int parallelTaskCount) {
        this(null, parallelTaskCount);
    }

    public DownloadController add(String storeFilePath, String url, Listener listener) {
        // only fulfill requests that were initiated from the main thread.(reason for the Delivery?)
        throwIfNotOnMainThread();

        DownloadController controller = new DownloadController(storeFilePath, url, listener);
        synchronized (mTaskQueue) {
            mTaskQueue.add(controller);
        }
        schedule();
        return controller;
    }

    /**
     * Scanning the Task Queue, fetch a {@link DownloadController} who match the two parameters.
     *
     * @param storeFilePath The storeFilePath to compare.
     * @param url           The url to compare.
     * @return The matched {@link DownloadController}.
     */
    public DownloadController get(String storeFilePath, String url) {
        synchronized (mTaskQueue) {
            for (DownloadController controller : mTaskQueue) {
                if (controller.mStoreFilePath.equals(storeFilePath) &&
                        controller.mUrl.equals(url)) return controller;
            }
        }
        return null;
    }

    /**
     * Traverse the Task Queue, count the running task then deploy more if it can be.
     */
    private void schedule() {
        // make sure only one thread can manipulate the Task Queue.
        synchronized (mTaskQueue) {
            // counting ran task.
            int parallelTaskCount = 0;
            for (DownloadController controller : mTaskQueue) {
                Log.d("LIF", "controller.isDownloading(): " + controller.isDownloading());
                if (controller.isDownloading()) parallelTaskCount++;
            }
            Log.d("LIF", "parallelTaskCount: " + parallelTaskCount);
            Log.d("LIF", "mParallelTaskCount: " + mParallelTaskCount);
            if (parallelTaskCount >= mParallelTaskCount) return;

            // try to deploy all Task if they're await.
            for (DownloadController controller : mTaskQueue) {
                if (controller.deploy() && ++parallelTaskCount == mParallelTaskCount) return;
            }
        }
    }

    /**
     * Remove the controller from the Task Queue, re-schedule to make those waiting task deploys.
     *
     * @param controller The controller which will be remove.
     */
    private void remove(DownloadController controller) {
        // also make sure one thread operation
        synchronized (mTaskQueue) {
            mTaskQueue.remove(controller);
        }
        schedule();
    }

    /**
     * Clear all tasks, make the Task Queue empty.
     */
    public void clearAll() {
        // make sure only one thread can manipulate the Task Queue.
        synchronized (mTaskQueue) {
            while (mTaskQueue.size() > 0) {
                mTaskQueue.get(0).discard();
            }
        }
    }

    private void throwIfNotOnMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("FileDownloader must be invoked from the main thread.");
        }
    }

    /**
     * This class included all such as PAUSE, RESUME, DISCARD to manipulating download task,
     * it created by {@link FileDownloader#add(String, String, com.aidaima.filedownloader.FileDownloader.Listener)},
     * offer three params to constructing {@link DownloadRequest} then perform http downloading,
     * you can check the download status whenever you want to know.
     */
    public class DownloadController {
        private final String TAG = DownloadController.class.getSimpleName();
        public static final int STATUS_WAITING = 0;
        public static final int STATUS_DOWNLOADING = 1;
        public static final int STATUS_PAUSE = 2;
        public static final int STATUS_SUCCESS = 3;
        public static final int STATUS_DISCARD = 4;
        // Persist the Request createing params for re-create it when pause operation gone.
        private Listener mListener;
        private String mStoreFilePath;
        private String mUrl;
        // The download request.
        private DownloadRequest mRequest;
        private int mStatus;

        private DownloadController(String storeFilePath, String url, Listener listener) {
            mStoreFilePath = storeFilePath;
            mListener = listener;
            mUrl = url;
        }

        /**
         * For the parallel reason, only the {@link FileDownloader#schedule()} can call this method.
         *
         * @return true if deploy is successed.
         */
        private boolean deploy() {
            Log.d("LIF", "mStatus: " + mStatus);
            if (mStatus != STATUS_WAITING) return false;

            mRequest = new DownloadRequest(mStoreFilePath, mUrl);

            // we create a Listener to wrapping that Listener which developer specified,
            // for the onFinish(), onSuccess(), onError()
            mRequest.setListener(new DownloadRequest.Listener() {

                public void finish(String tag) {
                    Log.d("LIF", "finish");
                    // when request was FINISH, remove the task and re-schedule Task Queue.
                    mRequest.finish(tag);
                    remove(DownloadController.this);
                }

                @Override
                public void onPreExecute() {
                    Log.d("LIF", "onPreExecute");
                    mListener.onStart();
                    Log.d(TAG, this + ": onPreExecute");
                }

                @Override
                public void onFinish() {
                    finish("");
                }

                @Override
                public void onSuccess() {
                    mStatus = STATUS_SUCCESS;
                    mListener.onFinish();
                }

                @Override
                public void onError(NetworkException error) {
                    Log.d("LIF", "onError");
                    if (!(error instanceof NetworkException.NetworkCancel)) {
                        mStatus = STATUS_DISCARD;
                        mListener.onError(error.getMessage());
                    }
                }

                @Override
                public void onProgressChange(long fileSize, long downloadedSize) {
                    mListener.onProgressChange(fileSize, downloadedSize);
                }
            });

            mStatus = STATUS_DOWNLOADING;
            mRequestQueue.add(mRequest);
            return true;
        }

        public int getStatus() {
            return mStatus;
        }

        public boolean isDownloading() {
            return mStatus == STATUS_DOWNLOADING;
        }

        /**
         * Pause this task when it status was DOWNLOADING, in fact, we just marked the request should be cancel,
         * http request cannot stop immediately, we assume it will finish soon, thus we set the status as PAUSE,
         * let Task Queue deploy a new Request, that will cause parallel tasks growing beyond maximum task count,
         * but it doesn't matter, we believe that situation never longer.
         *
         * @return true if did the pause operation.
         */
        public boolean pause() {
            if (mStatus == STATUS_DOWNLOADING) {
                mStatus = STATUS_PAUSE;
                mRequest.cancel();
                schedule();
                return true;
            }
            return false;
        }

        /**
         * Resume this task when it status was PAUSE, we will turn the status as WAITING, then re-schedule the Task Queue,
         * if parallel counter take an idle place, this task will re-deploy instantly,
         * if not, the status will stay WAITING till idle occur.
         *
         * @return true if did the resume operation.
         */
        public boolean resume() {
            if (mStatus == STATUS_PAUSE) {
                mStatus = STATUS_WAITING;
                schedule();
                return true;
            }
            return false;
        }

        /**
         * We will discard this task from the Task Queue, if the status was DOWNLOADING,
         * we first cancel the Request, then remove task from the Task Queue,
         * also re-schedule the Task Queue at last.
         *
         * @return true if did the discard operation.
         */
        public boolean discard() {
            if (mStatus == STATUS_DISCARD) return false;
            if (mStatus == STATUS_SUCCESS) return false;
            if (mStatus == STATUS_DOWNLOADING) mRequest.cancel();
            mStatus = STATUS_DISCARD;
            remove(this);
            return true;
        }
    }

    public abstract class Listener {
        public void onStart() {}

        public void onFinish() {}

        public void onError(String msg) {}

        public void onProgressChange(long fileSize, long downloadedSize) {}
    }
}