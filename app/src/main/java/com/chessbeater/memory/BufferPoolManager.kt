package com.chessbeater.memory

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * High-performance Zero-Allocation Buffer Pool Manager.
 * Reuses Bitmaps and DirectByteBuffers across frame capture & vision slicing cycles
 * to eliminate Garbage Collection pauses and keep memory footprint strictly under 180MB (PRD Section 7.1).
 */
class BufferPoolManager(
    private val maxPooledBitmaps: Int = 4,
    private val maxPooledByteBuffers: Int = 4
) {

    private val bitmapPool = ConcurrentLinkedQueue<Bitmap>()
    private val byteBufferPool = ConcurrentLinkedQueue<ByteBuffer>()

    /**
     * Obtains a reusable Bitmap from pool or creates a new one if pool is empty
     */
    fun acquireBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        var recycled: Bitmap? = null

        while (bitmapPool.isNotEmpty()) {
            val candidate = bitmapPool.poll() ?: break
            if (!candidate.isRecycled && candidate.width == width && candidate.height == height && candidate.config == config) {
                recycled = candidate
                break
            } else if (!candidate.isRecycled) {
                candidate.recycle()
            }
        }

        return recycled ?: Bitmap.createBitmap(width, height, config)
    }

    /**
     * Releases a Bitmap back into the pool for reuse
     */
    fun releaseBitmap(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return

        if (bitmapPool.size < maxPooledBitmaps) {
            bitmapPool.offer(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    /**
     * Obtains a DirectByteBuffer from the pool
     */
    fun acquireByteBuffer(capacity: Int): ByteBuffer {
        var buffer: ByteBuffer? = null

        while (byteBufferPool.isNotEmpty()) {
            val candidate = byteBufferPool.poll() ?: break
            if (candidate.capacity() >= capacity) {
                candidate.clear()
                buffer = candidate
                break
            }
        }

        return buffer ?: ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
    }

    /**
     * Releases a DirectByteBuffer back to the pool
     */
    fun releaseByteBuffer(buffer: ByteBuffer?) {
        if (buffer == null) return
        if (byteBufferPool.size < maxPooledByteBuffers) {
            buffer.clear()
            byteBufferPool.offer(buffer)
        }
    }

    /**
     * Clears all pools and recycles allocated Bitmaps
     */
    fun clear() {
        while (bitmapPool.isNotEmpty()) {
            val bmp = bitmapPool.poll()
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
        }
        byteBufferPool.clear()
    }
}
