package com.carlauncher.player

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size

object ArtLoader {

    fun load(ctx: Context, t: Track): Bitmap? {
        // 1) کاور داخل فایل MP3
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, t.uri)
                val d = r.embeddedPicture
                if (d != null) decode(d)?.let { return it }
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // 2) تامبنیل مدیااستور (اندروید 10+)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return ctx.contentResolver.loadThumbnail(t.uri, Size(512, 512), null)
            }
        } catch (_: Throwable) {}

        // 3) albumart قدیمی
        try {
            val u = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), t.albumId)
            ctx.contentResolver.openInputStream(u)?.use { return BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {}
        return null
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / sample > 800) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }

    /** تاری سبک: کوچک کردن شدید + بزرگ کردن با فیلتر */
    fun blur(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, 24, 24, true)
        val mid = Bitmap.createScaledBitmap(small, 96, 96, true)
        return Bitmap.createScaledBitmap(mid, 192, 192, true)
    }
}
