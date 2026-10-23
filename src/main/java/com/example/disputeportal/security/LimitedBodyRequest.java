package com.example.disputeportal.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * This manages the size of the request body.
 */
public class LimitedBodyRequest extends HttpServletRequestWrapper {

    private final long maxBytes;
    /**
     * Created on first use so every caller shares one stream (and one byte
     * count).
     */
    private ServletInputStream limitedStream;

    public LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
        super(request);
        this.maxBytes = maxBytes;
    }

    /**
     * Hands out the size-limited stream (created once, so repeated calls share
     * the same byte count).
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (limitedStream == null) {
            limitedStream = new LimitedStream(super.getInputStream(), maxBytes);
        }
        return limitedStream;
    }

    /**
     * Reader view of the same limited stream, so reading as text is also
     * capped.
     */
    @Override
    public BufferedReader getReader() throws IOException {
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /**
     * Wraps the real input stream and counts bytes as they are read, failing
     * once the limit is exceeded.
     */
    private static final class LimitedStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        /**
         * Bytes read so far.
         */
        private long count;

        LimitedStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        /**
         * Reads one byte and counts it.
         */
        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        /**
         * Reads a block of bytes and counts them; this is the method normally
         * used to read a body.
         */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        /**
         * Counts n more bytes and fails with PayloadTooLargeException when the
         * total passes the limit.
         */
        private void add(int n) throws IOException {
            count += n;
            if (count > maxBytes) {
                throw new PayloadTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
