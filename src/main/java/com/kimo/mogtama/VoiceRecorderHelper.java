// language: Java, file: VoiceRecorderHelper.java, target: Android API 21+
// AudioRecord مع Noise Suppressor + Echo Canceler + amplitude callback + تحويل لـ Base64

package com.kimo.mogtama;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VoiceRecorderHelper {

    public interface RecordCallback {
        void onAmplitude(float rms);          // كل 100ms أثناء التسجيل
        void onFinished(File rawFile,
                        String base64,
                        float[] waveSnapshot); // بعد الإيقاف
        void onError(String msg);
    }

    private static final int SAMPLE_RATE  = 16000;  // 16kHz — كافي للصوت، حجم أصغر
    private static final int CHANNEL_CFG  = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AMPLITUDE_INTERVAL_MS = 100;

    private AudioRecord recorder;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;

    private Thread recordThread;
    private volatile boolean isRecording = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RecordCallback callback;
    private final File outputDir;

    // snapshot الأمبليتيود للـ waveform الثابت بعد التسجيل
    private final List<Float> ampSnapshot = new ArrayList<>();

    public VoiceRecorderHelper(File outputDir, RecordCallback callback) {
        this.outputDir = outputDir;
        this.callback  = callback;
    }

    public void startRecording() {
        if (isRecording) return;

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CFG, AUDIO_FORMAT);
        // ضاعف الـ buffer علشان تجنب overrun
        bufferSize = Math.max(bufferSize, 4096) * 2;

        recorder = new AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // مصدر محسَّن للصوت
            SAMPLE_RATE, CHANNEL_CFG, AUDIO_FORMAT, bufferSize
        );

        int sessionId = recorder.getAudioSessionId();

        // Noise Suppressor
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId);
            if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
        }

        // Echo Canceler
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId);
            if (echoCanceler != null) echoCanceler.setEnabled(true);
        }

        recorder.startRecording();
        isRecording = true;
        ampSnapshot.clear();

        final File outFile = new File(outputDir,
            "voice_" + System.currentTimeMillis() + ".pcm");

        recordThread = new Thread(() -> recordLoop(outFile, bufferSize));
        recordThread.start();
    }

    private void recordLoop(File outFile, int bufferSize) {
        short[] buf = new short[bufferSize / 2];
        ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();

        long lastAmpTime = System.currentTimeMillis();

        try (FileOutputStream fos = new FileOutputStream(outFile)) {

            while (isRecording) {
                int read = recorder.read(buf, 0, buf.length);
                if (read <= 0) continue;

                // كتابة raw PCM على الديسك
                byte[] bytes = shortArrayToBytes(buf, read);
                fos.write(bytes);
                pcmStream.write(bytes);

                // حساب RMS كل 100ms
                long now = System.currentTimeMillis();
                if (now - lastAmpTime >= AMPLITUDE_INTERVAL_MS) {
                    lastAmpTime = now;
                    float rms = calcRms(buf, read);
                    float norm = Math.min(rms / 8000f, 1f);
                    ampSnapshot.add(norm);
                    mainHandler.post(() -> callback.onAmplitude(rms));
                }
            }

            // بعد الوقوف — بناء Base64 + waveSnapshot
            byte[] allPcm = pcmStream.toByteArray();
            String b64 = Base64.encodeToString(allPcm, Base64.NO_WRAP);

            float[] wave = buildWaveSnapshot(ampSnapshot, 60);

            mainHandler.post(() -> callback.onFinished(outFile, b64, wave));

        } catch (IOException e) {
            mainHandler.post(() -> callback.onError(e.getMessage()));
        }
    }

    public void stopRecording() {
        isRecording = false;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        if (noiseSuppressor != null) { noiseSuppressor.release(); noiseSuppressor = null; }
        if (echoCanceler   != null) { echoCanceler.release();    echoCanceler    = null; }
    }

    // ── Helpers ──

    private float calcRms(short[] buf, int len) {
        long sum = 0;
        for (int i = 0; i < len; i++) sum += (long) buf[i] * buf[i];
        return (float) Math.sqrt((double) sum / len);
    }

    private byte[] shortArrayToBytes(short[] shorts, int len) {
        byte[] out = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            out[i * 2]     = (byte) (shorts[i] & 0xFF);
            out[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return out;
    }

    // يضغط الـ amplitudes الكتيرة لعدد ثابت من الـ bars
    private float[] buildWaveSnapshot(List<Float> amps, int targetBars) {
        if (amps.isEmpty()) return new float[targetBars];
        float[] wave = new float[targetBars];
        float ratio  = (float) amps.size() / targetBars;
        for (int i = 0; i < targetBars; i++) {
            int idx = Math.min((int) (i * ratio), amps.size() - 1);
            wave[i] = amps.get(idx);
        }
        return wave;
    }
}
