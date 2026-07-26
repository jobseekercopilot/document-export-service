package com.jobseekercopilot.documentexport.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class BoundedByteArrayOutputStream extends OutputStream {

    private final ByteArrayOutputStream delegate;
    private final long maximumBytes;

    BoundedByteArrayOutputStream(long maximumBytes) {
        this.maximumBytes = maximumBytes;
        this.delegate = new ByteArrayOutputStream(
                (int) Math.min(maximumBytes, 64L * 1024));
    }

    @Override
    public void write(int value) throws IOException {
        ensureCapacity(1);
        delegate.write(value);
    }

    @Override
    public void write(byte[] bytes, int offset, int length)
            throws IOException {
        ensureCapacity(length);
        delegate.write(bytes, offset, length);
    }

    int size() {
        return delegate.size();
    }

    byte[] toByteArray() {
        return delegate.toByteArray();
    }

    private void ensureCapacity(int additionalBytes) throws IOException {
        if ((long) delegate.size() + additionalBytes > maximumBytes) {
            throw new IOException(
                    "Rendered output exceeds the configured byte budget");
        }
    }
}
