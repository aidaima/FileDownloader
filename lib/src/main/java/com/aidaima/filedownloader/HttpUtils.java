package com.aidaima.filedownloader;

import android.text.TextUtils;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HTTP;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class HttpUtils {
    private final static String TAG = HttpUtils.class.getSimpleName();
    private static int DEFAULT_POOL_SIZE = 4096;

    {
        ByteArrayPool.init(DEFAULT_POOL_SIZE);
    }

    /**
     * Reads the contents of HttpEntity into a byte[].
     */
    public static byte[] responseToBytes(HttpResponse response) throws IOException, NetworkException.ServerError {
        HttpEntity entity = response.getEntity();
        PoolingByteArrayOutputStream bytes =
                new PoolingByteArrayOutputStream(ByteArrayPool.get(), (int) entity.getContentLength());
        byte[] buffer = null;
        try {
            InputStream in = entity.getContent();
            if (isGzipContent(response) && !(in instanceof GZIPInputStream)) {
                in = new GZIPInputStream(in);
            }

            if (in == null) {
                throw new NetworkException.ServerError();
            }

            buffer = ByteArrayPool.get().getBuf(1024);
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            try {
                // Close the InputStream and release the resources by "consuming the content".
                entity.consumeContent();
            } catch (IOException e) {
                // This can happen if there was an exception above that left the entity in
                // an invalid state.
                Log.v(TAG, "Error occured when calling consumingContent");
            }
            ByteArrayPool.get().returnBuf(buffer);
            bytes.close();
        }
    }

    /**
     * Returns the charset specified in the Content-Type of this header.
     */
    public static String getCharset(HttpResponse response) {
        Header header = response.getFirstHeader(HTTP.CONTENT_TYPE);
        if (header != null) {
            String contentType = header.getValue();
            if (!TextUtils.isEmpty(contentType)) {
                String[] params = contentType.split(";");
                for (int i = 1; i < params.length; i++) {
                    String[] pair = params[i].trim().split("=");
                    if (pair.length == 2) {
                        if (pair[0].equals("charset")) {
                            return pair[1];
                        }
                    }
                }
            }
        }
        return null;
    }

    public static String getHeader(HttpResponse response, String key) {
        Header header = response.getFirstHeader(key);
        return header == null ? null : header.getValue();
    }

    public static boolean isSupportRange(HttpResponse response) {
        if (TextUtils.equals(getHeader(response, "Accept-Ranges"), "bytes")) {
            return true;
        }
        String value = getHeader(response, "Content-Range");
        return value != null && value.startsWith("bytes");
    }

    public static boolean isGzipContent(HttpResponse response) {
        return TextUtils.equals(getHeader(response, "Content-Encoding"), "gzip");
    }

    /**
     * ByteArrayPool is a source and repository of <code>byte[]</code> objects. Its purpose is to
     * supply those buffers to consumers who need to use them for a short period of time and then
     * dispose of them. Simply creating and disposing such buffers in the conventional manner can
     * considerable heap churn and garbage collection delays on Android, which lacks good management of
     * short-lived heap objects. It may be advantageous to trade off some memory in the form of a
     * permanently allocated pool of buffers in order to gain heap performance improvements; that is
     * what this class does.
     * <p/>
     * A good candidate user for this class is something like an I/O system that uses large temporary
     * <code>byte[]</code> buffers to copy data around. In these use cases, often the consumer wants
     * the buffer to be a certain minimum size to ensure good performance (e.g. when copying data chunks
     * off of a stream), but doesn't mind if the buffer is larger than the minimum. Taking this into
     * account and also to maximize the odds of being able to reuse a recycled buffer, this class is
     * free to return buffers larger than the requested size. The caller needs to be able to gracefully
     * deal with getting buffers any size over the minimum.
     * <p/>
     * If there is not a suitably-sized buffer in its recycling pool when a buffer is requested, this
     * class will allocate a new buffer and return it.
     * <p/>
     * This class has no special ownership of buffers it creates; the caller is free to take a buffer
     * it receives from this pool, use it permanently, and never return it to the pool; additionally,
     * it is not harmful to return to this pool a buffer that was allocated elsewhere, provided there
     * are no other lingering references to it.
     * <p/>
     * This class ensures that the total size of the buffers in its recycling pool never exceeds a
     * certain byte limit. When a buffer is returned that would cause the pool to exceed the limit,
     * least-recently-used buffers are disposed.
     */
    private static class ByteArrayPool {
        /**
         * Compares buffers by size
         */
        protected static final Comparator<byte[]> BUF_COMPARATOR = new Comparator<byte[]>() {
            @Override
            public int compare(byte[] lhs, byte[] rhs) {
                return lhs.length - rhs.length;
            }
        };
        /**
         * Singleton for this class.
         */
        private static ByteArrayPool mPool;
        /**
         * The maximum aggregate size of the buffers in the pool. Old buffers are discarded to stay
         * under this limit.
         */
        private final int mSizeLimit;
        /**
         * The buffer pool, arranged both by last use and by buffer size
         */
        private List<byte[]> mBuffersByLastUse = new LinkedList<byte[]>();
        private List<byte[]> mBuffersBySize = new ArrayList<byte[]>(64);
        /**
         * The total size of the buffers in the pool
         */
        private int mCurrentSize = 0;

        /**
         * @param sizeLimit the maximum size of the pool, in bytes
         */
        private ByteArrayPool(int sizeLimit) {
            mSizeLimit = sizeLimit;
        }

        /**
         * Get the singleton instance.
         */
        public static ByteArrayPool get() {
            return mPool;
        }

        /**
         * Init and persisting the singleton instance.
         */
        public static void init(int poolSize) {
            mPool = new ByteArrayPool(poolSize);
        }

        /**
         * Returns a buffer from the pool if one is available in the requested size, or allocates a new
         * one if a pooled one is not available.
         *
         * @param len the minimum size, in bytes, of the requested buffer. The returned buffer may be
         *            larger.
         * @return a byte[] buffer is always returned.
         */
        public synchronized byte[] getBuf(int len) {
            for (int i = 0; i < mBuffersBySize.size(); i++) {
                byte[] buf = mBuffersBySize.get(i);
                if (buf.length >= len) {
                    mCurrentSize -= buf.length;
                    mBuffersBySize.remove(i);
                    mBuffersByLastUse.remove(buf);
                    return buf;
                }
            }
            return new byte[len];
        }

        /**
         * Returns a buffer to the pool, throwing away old buffers if the pool would exceed its allotted
         * size.
         *
         * @param buf the buffer to return to the pool.
         */
        public synchronized void returnBuf(byte[] buf) {
            if (buf == null || buf.length > mSizeLimit) {
                return;
            }
            mBuffersByLastUse.add(buf);
            int pos = Collections.binarySearch(mBuffersBySize, buf, BUF_COMPARATOR);
            if (pos < 0) {
                pos = -pos - 1;
            }
            mBuffersBySize.add(pos, buf);
            mCurrentSize += buf.length;
            trim();
        }

        /**
         * Removes buffers from the pool until it is under its size limit.
         */
        private synchronized void trim() {
            while (mCurrentSize > mSizeLimit) {
                byte[] buf = mBuffersByLastUse.remove(0);
                mBuffersBySize.remove(buf);
                mCurrentSize -= buf.length;
            }
        }

    }

    /**
     * A variation of {@link ByteArrayOutputStream} that uses a pool of byte[] buffers instead
     * of always allocating them fresh, saving on heap churn.
     */
    private static class PoolingByteArrayOutputStream extends ByteArrayOutputStream {
        /**
         * If the {@link #PoolingByteArrayOutputStream(ByteArrayPool)} constructor is called, this is
         * the default size to which the underlying byte array is initialized.
         */
        private static final int DEFAULT_SIZE = 256;

        private final ByteArrayPool mPool;

        /**
         * Constructs a new PoolingByteArrayOutputStream with a default size. If more bytes are written
         * to this instance, the underlying byte array will expand.
         */
        public PoolingByteArrayOutputStream(ByteArrayPool pool) {
            this(pool, DEFAULT_SIZE);
        }

        /**
         * Constructs a new {@code ByteArrayOutputStream} with a default size of {@code size} bytes. If
         * more than {@code size} bytes are written to this instance, the underlying byte array will
         * expand.
         *
         * @param size initial size for the underlying byte array. The value will be pinned to a default
         *             minimum size.
         */
        public PoolingByteArrayOutputStream(ByteArrayPool pool, int size) {
            mPool = pool;
            buf = mPool.getBuf(Math.max(size, DEFAULT_SIZE));
        }

        @Override
        public void close() throws IOException {
            mPool.returnBuf(buf);
            buf = null;
            super.close();
        }

        @Override
        public void finalize() {
            mPool.returnBuf(buf);
        }

        /**
         * Ensures there is enough space in the buffer for the given number of additional bytes.
         */
        private void expand(int i) {
        /* Can the buffer handle @i more bytes, if not expand it */
            if (count + i <= buf.length) {
                return;
            }
            byte[] newbuf = mPool.getBuf((count + i) * 2);
            System.arraycopy(buf, 0, newbuf, 0, count);
            mPool.returnBuf(buf);
            buf = newbuf;
        }

        @Override
        public synchronized void write(byte[] buffer, int offset, int len) {
            expand(len);
            super.write(buffer, offset, len);
        }

        @Override
        public synchronized void write(int oneByte) {
            expand(1);
            super.write(oneByte);
        }
    }
}

