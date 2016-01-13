package com.aidaima.filedownloader;

import android.text.TextUtils;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class DownloadRequest implements Comparable<DownloadRequest>{
    private static final String TAG = DownloadRequest.class.getSimpleName();
    private File mStoreFile;
    private File mTemporaryFile;

    /**
     * URL of this request.
     */
    private final String mUrl;

    /**
     * The additional headers.
     */
    private HashMap<String, String> mHashHeaders;

    /**
     * Listener interface for response and error.
     */
    private Listener mListener;

    /**
     * The request queue this request is associated with.
     */
    private RequestQueue mRequestQueue;

    /**
     * Whether or not this request has been canceled.
     */
    private boolean mCanceled = false;

    /**
     * Detailed error information if <code>errorCode != OK</code>.
     */
    public NetworkException error;

    /**
     * Indicates DeliverPreExecute operation is done or not,
     * because the {@link RequestDispatcher}
     * will call this deliver, and we must ensure just invoke once.
     */
    private boolean mIsDeliverPreExecute;

    private int mTimeout;

    public DownloadRequest(String storeFilePath, String url) {
        mUrl = url;
        mListener = null;
        mTimeout = 2500;

        mHashHeaders = new HashMap<String, String>();
        mStoreFile = new File(storeFilePath);
        mTemporaryFile = new File(storeFilePath + ".tmp");
    }

    @Override
    public int compareTo(DownloadRequest another) {
        return 0;
    }

    /**
     * Set the response listener.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Notifies the request queue that this request has finished (successfully or with error).
     * <p/>
     * <p>Also dumps all events from this request's event log; for debugging.</p>
     */
    public void finish(final String tag) {
        if (mRequestQueue != null) {
            mRequestQueue.finish(this);
        }
    }

    /**
     * Associates this request with the given queue. The request queue will be notified when this
     * request has finished.
     */
    public void setRequestQueue(RequestQueue requestQueue) {
        mRequestQueue = requestQueue;
    }

    /**
     * Returns the URL of this request.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Mark this request as canceled.  No callback will be delivered.
     */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * Returns true if this request has been canceled.
     */
    public boolean isCanceled() {
        return mCanceled;
    }

    /**
     * Returns a list of extra HTTP headers to go along with this request.
     */
    public final Map<String, String> getHeaders() {
        return mHashHeaders;
    }

    /**
     * Put a Header to RequestHeaders.
     *
     * @param field header key
     * @param value header value
     */
    public final void addHeader(String field, String value) {
        // We don't accept duplicate header.
        removeHeader(field);
        mHashHeaders.put(field, value);
    }

    /**
     * Remove a header from RequestHeaders
     *
     * @param field header key
     */
    public final void removeHeader(String field) {
        mHashHeaders.remove(field);
    }

    /**
     * Returns the socket timeout in milliseconds per retry attempt. (This value can be changed
     * per retry attempt if a backoff is specified via backoffTimeout()). If there are no retry
     * attempts remaining, this will cause delivery of a {@link NetworkException.TimeoutError} error.
     */
    public final int getTimeoutMs() {
        return mTimeout;
    }

    /**
     * Perform delivery of the parsed response. The given response is guaranteed to
     * be non-null; responses that fail to parse are not delivered.
     */
    public void deliverSuccess() {
        if (mListener != null) {
            mListener.onSuccess();
        }
    }

    /**
     * Delivers error message to the Listener that the Request was initialized with.
     *
     * @param error Error details
     */
    public void deliverError(NetworkException error) {
        if (mListener != null) {
            mListener.onError(error);
        }
    }

    /**
     * Delivers request is handling to the Listener.
     */
    public void deliverPreExecute() {
        if (mListener != null && !mIsDeliverPreExecute) {
            mIsDeliverPreExecute = true;
            mListener.onPreExecute();
        }
    }

    /**
     * Delivers when cache used to the Listener.
     */
    public void deliverFinish() {
        if (mListener != null) {
            mListener.onFinish();
        }
    }

    /**
     * Delivers when download request progress change to the Listener.
     */
    public void deliverDownloadProgress(long fileSize, long downloadedSize) {
        if (mListener != null) {
            mListener.onProgressChange(fileSize, downloadedSize);
        }
    }

    @Override
    public String toString() {
        return (mCanceled ? "[X] " : "[ ] ") + getUrl();
    }

    /**
     * Callback interface for delivering request status or response result.
     * Note : all method are calls over UI thread.
     */
    public static abstract class Listener {
        /**
         * Inform when start to handle this Request.
         */
        public void onPreExecute() {
        }

        /**
         * Inform when {@link DownloadRequest} execute is finish,
         * whatever success or error or cancel, this callback
         * method always invoke if request is done.
         */
        public void onFinish() {
        }

        /**
         * Called when response success.
         */
        public abstract void onSuccess();

        /**
         * Callback method that an error has been occurred with the
         * provided error code and optional user-readable message.
         */
        public void onError(NetworkException error) {
        }

        /**
         * Inform when download progress change, this callback method only available
         * when request was {@link DownloadRequest}.
         */
        public void onProgressChange(long fileSize, long downloadedSize) {
        }
    }

    /**
     * Init or reset the Range header, ensure the begin position always be the temporary file size.
     */
    public void prepare() {
        // Note: if the request header "Range" greater than the actual length that server-size have,
        // the response header "Content-Range" will return "bytes */[actual length]", that's wrong.
        addHeader("Range", "bytes=" + mTemporaryFile.length() + "-");

        // Suppress the HttpStack accept gzip encoding, avoid the progress calculate wrong problem.
        addHeader("Accept-Encoding", "identity");
    }

    /**
     * Ignore the response content, just rename the TemporaryFile to StoreFile.
     */
    protected void parseNetworkResponse() {
        Log.d(TAG, "isCanceled: " + isCanceled());
        if (!isCanceled()) {
            if (mTemporaryFile.canRead() && mTemporaryFile.length() > 0) {
                if (mTemporaryFile.renameTo(mStoreFile)) {
                    error = null;
                } else {
                    error = new NetworkException("Can't rename the download temporary file!");
                }
            } else {
                error = new NetworkException("Download temporary file was invalid!");
            }
        }
        error = new NetworkException.NetworkCancel("Request was Canceled!");
    }

    /**
     * In this method, we got the Content-Length, with the TemporaryFile length,
     * we can calculate the actually size of the whole file, if TemporaryFile not exists,
     * we'll take the store file length then compare to actually size, and if equals,
     * we consider this download was already done.
     * We used {@link RandomAccessFile} to continue download, when download success,
     * the TemporaryFile will be rename to StoreFile.
     */
    public byte[] handleResponse(HttpResponse response, RequestDispatcher.Delivery delivery) throws IOException, NetworkException.ServerError {
        // Content-Length might be negative when use HttpURLConnection because it default header Accept-Encoding is gzip,
        // we can force set the Accept-Encoding as identity in prepare() method to slove this problem but also disable gzip response.
        HttpEntity entity = response.getEntity();
        long fileSize = entity.getContentLength();
        if (fileSize <= 0) {
            Log.d(TAG, "Response doesn't present Content-Length!");
        }

        long downloadedSize = mTemporaryFile.length();
        boolean isSupportRange = HttpUtils.isSupportRange(response);
        if (isSupportRange) {
            fileSize += downloadedSize;

            // Verify the Content-Range Header, to ensure temporary file is part of the whole file.
            // Sometime, temporary file length add response content-length might greater than actual file length,
            // in this situation, we consider the temporary file is invalid, then throw an exception.
            String realRangeValue = HttpUtils.getHeader(response, "Content-Range");
            // response Content-Range may be null when "Range=bytes=0-"
            if (!TextUtils.isEmpty(realRangeValue)) {
                String assumeRangeValue = "bytes " + downloadedSize + "-" + (fileSize - 1);
                if (TextUtils.indexOf(realRangeValue, assumeRangeValue) == -1) {
                    throw new IllegalStateException(
                            "The Content-Range Header is invalid Assume[" + assumeRangeValue + "] vs Real[" + realRangeValue + "], " +
                                    "please remove the temporary file [" + mTemporaryFile + "].");
                }
            }
        }

        // Compare the store file size(after download successes have) to server-side Content-Length.
        // temporary file will rename to store file after download success, so we compare the
        // Content-Length to ensure this request already download or not.
        if (fileSize > 0 && mStoreFile.length() == fileSize) {
            // Rename the store file to temporary file, mock the download success. ^_^
            mStoreFile.renameTo(mTemporaryFile);

            // Deliver download progress.
            delivery.postDownloadProgress(this, fileSize, fileSize);

            return null;
        }

        RandomAccessFile tmpFileRaf = new RandomAccessFile(mTemporaryFile, "rw");

        // If server-side support range download, we seek to last point of the temporary file.
        if (isSupportRange) {
            tmpFileRaf.seek(downloadedSize);
        } else {
            // If not, truncate the temporary file then start download from beginning.
            tmpFileRaf.setLength(0);
            downloadedSize = 0;
        }

        try {
            InputStream in = entity.getContent();
            // Determine the response gzip encoding, support for HttpClientStack download.
            if (HttpUtils.isGzipContent(response) && !(in instanceof GZIPInputStream)) {
                in = new GZIPInputStream(in);
            }
            byte[] buffer = new byte[6 * 1024]; // 6K buffer
            int offset;

            while ((offset = in.read(buffer)) != -1) {
                tmpFileRaf.write(buffer, 0, offset);

                downloadedSize += offset;
                delivery.postDownloadProgress(this, fileSize, downloadedSize);

                if (isCanceled()) {
//                    delivery.postCancel(this);
                    break;
                }
            }
        } finally {
            try {
                // Close the InputStream and release the resources by "consuming the content".
                if (entity != null) entity.consumeContent();
            } catch (Exception e) {
                // This can happen if there was an exception above that left the entity in
                // an invalid state.
                Log.v(TAG, "Error occured when calling consumingContent");
            }
            tmpFileRaf.close();
        }

        return null;
    }

    /**
     * Returns whether this response is considered successful.
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Initializes an {@link HttpEntity} from the given {@link HttpURLConnection}.
     *
     * @return an HttpEntity populated with data from <code>connection</code>.
     */
    private HttpEntity entityFromConnection(HttpURLConnection connection) {
        BasicHttpEntity entity = new BasicHttpEntity();
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        entity.setContent(inputStream);
        entity.setContentLength(connection.getContentLength());
        entity.setContentEncoding(connection.getContentEncoding());
        entity.setContentType(connection.getContentType());
        return entity;
    }

    /**
     * Returns the charset specified in the Content-Type of this header,
     * or the defaultCharset if none can be found.
     */
    private String parseCharset(HttpResponse response) {
        String charset = HttpUtils.getCharset(response);
        return charset == null ? HTTP.UTF_8 : charset;
    }

    public void performRequest(RequestDispatcher.Delivery delivery) throws NetworkException  {
        while (true) {
            // If the request was cancelled already,
            // do not perform the network request.
            if (isCanceled()) {
                error = new NetworkException.NetworkCancel("Request was Canceled!");
                return ;
            }

            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            try {
                // prepare to perform this request, normally is reset the request headers.
                prepare();

                httpResponse = performRequest();

                StatusLine statusLine = httpResponse.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode < 200 || statusCode > 299) throw new IOException();

                responseContents = handleResponse(httpResponse, delivery);
                parseNetworkResponse();
            } catch (SocketTimeoutException e) {
            } catch (MalformedURLException e) {
                throw new RuntimeException("Bad URL " + getUrl(), e);
            } catch (IOException e) {
                if (httpResponse == null) throw new NetworkException.NoConnectionError(e);

                int statusCode = httpResponse.getStatusLine().getStatusCode();
                String errMsg = "Unexpected response code " + statusCode + "for " + getUrl();
                Log.e(TAG, errMsg);
                if (responseContents != null) {
                    if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {

                    } else {
                        // TODO: Only throw ServerError for 5xx status codes.
                        throw new NetworkException.ServerError();
                    }
                } else {
                    throw new NetworkException.NetworkError(errMsg);
                }
            }
        }
    }

    public HttpResponse performRequest() throws IOException {
        URL parsedUrl = new URL(getUrl());
        HttpURLConnection connection = openConnection(parsedUrl, this);
        for (String headerName : mHashHeaders.keySet()) {
            connection.addRequestProperty(headerName, mHashHeaders.get(headerName));
        }

        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == -1) {
            // -1 is returned by getResponseCode() if the response code could not be retrieved.
            // Signal to the caller that something was wrong with the connection.
            throw new IOException("Could not retrieve response code from HttpUrlConnection.");
        }

        StatusLine responseStatus = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1),
                connection.getResponseCode(), connection.getResponseMessage());
        BasicHttpResponse response = new BasicHttpResponse(responseStatus);
        response.setEntity(entityFromConnection(connection));

        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null) {
                Header h = new BasicHeader(header.getKey(), header.getValue().get(0));
                response.addHeader(h);
            }
        }

        return response;
    }

    /**
     * Opens an {@link HttpURLConnection} with parameters.
     *
     * @return an open connection
     */
    private HttpURLConnection openConnection(URL url, DownloadRequest request) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        int timeoutMs = request.getTimeoutMs();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setUseCaches(false);
        connection.setDoInput(true);

        return connection;
    }
}