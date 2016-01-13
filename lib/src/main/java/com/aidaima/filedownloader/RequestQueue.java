package com.aidaima.filedownloader;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;

public class RequestQueue {
    /**
     * Number of network request dispatcher threads to start.
     */
    public static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /**
     * The set of all requests currently being processed by this RequestQueue. A DownloadRequest
     * will be in this set if it is waiting in any queue or currently being processed by
     * any dispatcher.
     */
    private final Set<DownloadRequest> mCurrentRequests = new HashSet<DownloadRequest>();
    /**
     * The queue of requests that are actually going out to the network.
     */
    private final PriorityBlockingQueue<DownloadRequest> mNetworkQueue =
            new PriorityBlockingQueue<DownloadRequest>();

    /**
     * DownloadRequest delivery mechanism.
     */
    private final RequestDispatcher.Delivery mDelivery;
    /**
     * The network dispatchers.
     */
    private RequestDispatcher[] mDispatchers;

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     * @param threadPoolSize Number of network dispatcher threads to create
     * @param delivery       A Delivery interface for posting responses and errors
     */
    public RequestQueue(int threadPoolSize, RequestDispatcher.Delivery delivery) {
        mDelivery = delivery;
        mDispatchers = new RequestDispatcher[threadPoolSize];
    }

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     * @param threadPoolSize Number of network dispatcher threads to create
     */
    public RequestQueue(int threadPoolSize) {
        this(threadPoolSize, new RequestDispatcher.Delivery(new Handler(Looper.getMainLooper())));
    }

    /**
     * Creates the worker pool. Processing will not begin until {@link #start()} is called.
     *
     */
    public RequestQueue() {
        this(DEFAULT_NETWORK_THREAD_POOL_SIZE, null);
    }

    /**
     * Starts the dispatchers in this queue.
     */
    public void start() {
        stop();  // Make sure any currently running dispatchers are stopped.

        // Create request dispatchers (and corresponding threads) up to the pool size.
        for (int i = 0; i < mDispatchers.length; i++) {
            RequestDispatcher networkDispatcher =
                    new RequestDispatcher(mNetworkQueue, mDelivery);
            mDispatchers[i] = networkDispatcher;
            networkDispatcher.start();
        }
    }

    /**
     * Stops the cache and network dispatchers.
     */
    public void stop() {
        for (RequestDispatcher mDispatcher : mDispatchers) {
            if (mDispatcher != null) mDispatcher.quit();
        }
    }

    /**
     * Gets the thread pool size.
     */
    public int getThreadPoolSize() {
        return mDispatchers.length;
    }

    public void cancelAll() {
        synchronized (mCurrentRequests) {
            for (DownloadRequest request : mCurrentRequests) {
                request.cancel();
            }
        }
    }

    public DownloadRequest add(DownloadRequest request) {
        // Tag the request as belonging to this queue and add it to the set of current requests.
        request.setRequestQueue(this);
        synchronized (mCurrentRequests) {
            mCurrentRequests.add(request);
        }

        mNetworkQueue.add(request);
        return request;
    }

    void finish(DownloadRequest request) {
        // Remove from the set of requests currently being processed.
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(request);
        }
    }
}
