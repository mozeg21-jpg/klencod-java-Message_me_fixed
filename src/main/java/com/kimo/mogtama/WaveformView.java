// language: Java, file: WaveformView.java, target: Android API 21+
// Custom View — رسم الشريط المتحرك أثناء التسجيل + عرض ثابت وقت التشغيل

package com.kimo.mogtama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class WaveformView extends View {

    // ── Live recording mode ──
    private final List<Float> amplitudes = new ArrayList<>();
    private static final int MAX_BARS   = 60;   // عدد الأعمدة المرئية
    private static final int BAR_WIDTH  = 6;    // px
    private static final int BAR_GAP    = 3;    // px
    private boolean isRecording = false;

    // ── Playback mode ──
    private float[] staticWave = null;          // مُمرَّر من الخارج
    private float   playbackProgress = 0f;      // 0..1

    private final Paint barPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WaveformView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        barPaint.setColor(0xFF4FC3F7);          // أزرق فاتح
        playedPaint.setColor(0xFF0288D1);       // أزرق داكن (الجزء اللي اتشغل)
    }

    // ── يُستدعى من AudioRecord callback كل ~100ms ──
    public void pushAmplitude(float amp) {
        // تطبيع 0..1 (الإدخال: RMS raw 0..32768)
        float norm = Math.min(amp / 8000f, 1f);
        if (amplitudes.size() >= MAX_BARS) amplitudes.remove(0);
        amplitudes.add(norm);
        isRecording = true;
        invalidate();
    }

    // ── وضع الـ Playback ──
    public void setStaticWave(float[] wave) {
        this.staticWave = wave;
        isRecording = false;
        invalidate();
    }

    public void setPlaybackProgress(float p) {
        this.playbackProgress = p;
        invalidate();
    }

    public void reset() {
        amplitudes.clear();
        staticWave = null;
        playbackProgress = 0f;
        isRecording = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int cx = h / 2;

        if (isRecording) {
            drawLive(canvas, w, h, cx);
        } else if (staticWave != null) {
            drawStatic(canvas, w, h, cx);
        }
    }

    private void drawLive(Canvas canvas, int w, int h, int cx) {
        int total = amplitudes.size();
        int step  = BAR_WIDTH + BAR_GAP;
        int startX = w - total * step;          // يزحف من اليمين

        for (int i = 0; i < total; i++) {
            float amp   = amplitudes.get(i);
            float barH  = Math.max(4, amp * (h - 4));
            float x     = startX + i * step;
            canvas.drawRoundRect(x, cx - barH / 2f,
                                 x + BAR_WIDTH, cx + barH / 2f,
                                 3, 3, barPaint);
        }
    }

    private void drawStatic(Canvas canvas, int w, int h, int cx) {
        int n    = staticWave.length;
        float step = (float) w / n;
        int pivot = (int) (n * playbackProgress);

        for (int i = 0; i < n; i++) {
            float barH = Math.max(4, staticWave[i] * (h - 4));
            float x    = i * step;
            Paint p    = i < pivot ? playedPaint : barPaint;
            canvas.drawRoundRect(x, cx - barH / 2f,
                                 x + step - 1, cx + barH / 2f,
                                 2, 2, p);
        }
    }
}
