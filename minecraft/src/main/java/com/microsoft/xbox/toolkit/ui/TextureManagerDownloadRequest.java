package com.microsoft.xbox.toolkit.ui;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;


public class TextureManagerDownloadRequest implements Comparable<TextureManagerDownloadRequest> {
    private static final AtomicInteger nextIndex = new AtomicInteger(0);
    public int index = nextIndex.incrementAndGet();
    public TextureManagerScaledNetworkBitmapRequest key;
    public InputStream stream;

    public TextureManagerDownloadRequest(TextureManagerScaledNetworkBitmapRequest textureManagerScaledNetworkBitmapRequest) {
        this.key = textureManagerScaledNetworkBitmapRequest;
    }

    public int compareTo(@NotNull TextureManagerDownloadRequest textureManagerDownloadRequest) {
        return this.index - textureManagerDownloadRequest.index;
    }
}
