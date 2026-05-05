package com.spmods.ytpro;

import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class YTProDownloader {

    private final MainActivity activity;

    public YTProDownloader(MainActivity activity) {
        this.activity = activity;
    }

    public void requestBinaryPort(String fileName) {
        activity.runOnUiThread(() -> {
            try {
                WebMessagePort[] ports = activity.web.createWebMessageChannel();
                WebMessagePort mainPort = ports[0];
                WebMessagePort filePort = ports[1];

                File outputDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(outputDir, fileName);
                FileOutputStream[] fosHolder = {new FileOutputStream(outputFile)};

                mainPort.setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                    @Override
                    public void onMessage(WebMessagePort port, WebMessage message) {
                        try {
                            String data = message.getData();
                            
                            if ("END".equals(data)) {
                                fosHolder[0].flush();
                                fosHolder[0].close();
                                Intent scan = new Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                                scan.setData(Uri.fromFile(outputFile));
                                activity.sendBroadcast(scan);
                                Log.d("YTPRO", "✅ Saved: " + fileName);
                                return;
                            }

                            if (data != null && !data.isEmpty()) {
                                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                                fosHolder[0].write(bytes);
                            }

                        } catch (Exception e) {
                            Log.e("YTPRO", "❌ Write error: " + e.getMessage());
                        }
                    }
                }, new Handler(Looper.getMainLooper()));

                activity.web.postWebMessage(
                    new WebMessage("PORT_FOR:" + fileName,
                        new WebMessagePort[]{filePort}),
                    Uri.parse("https://m.youtube.com")
                );

            } catch (Exception e) {
                Log.e("YTPRO", "❌ requestBinaryPort error: " + e.getMessage());
            }
        });
    }

    public void muxVideoAudio(String videoFileName, String audioFileName, String outputFileName) {
        new Thread(() -> {
            try {
                activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(activity,
                        "Muxing... please wait",
                        android.widget.Toast.LENGTH_SHORT).show()
                );

                File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
                File videoFile = new File(dir, videoFileName);
                File audioFile = new File(dir, audioFileName);
                File outputFile = new File(dir, outputFileName);

                MediaMuxer muxer = new MediaMuxer(
                    outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                );

                MediaExtractor videoExtractor = new MediaExtractor();
                videoExtractor.setDataSource(videoFile.getAbsolutePath());

                MediaExtractor audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(audioFile.getAbsolutePath());

                int videoTrackIndex = -1;
                int muxVideoTrack = -1;
                for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                    MediaFormat fmt = videoExtractor.getTrackFormat(i);
                    if (fmt.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                        videoTrackIndex = i;
                        muxVideoTrack = muxer.addTrack(fmt);
                        break;
                    }
                }

                int audioTrackIndex = -1;
                int muxAudioTrack = -1;
                for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                    MediaFormat fmt = audioExtractor.getTrackFormat(i);
                    if (fmt.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                        audioTrackIndex = i;
                        muxAudioTrack = muxer.addTrack(fmt);
                        break;
                    }
                }

                muxer.start();

                ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                if (videoTrackIndex >= 0) {
                    videoExtractor.selectTrack(videoTrackIndex);
                    while (true) {
                        int size = videoExtractor.readSampleData(buffer, 0);
                        if (size < 0) break;
                        bufferInfo.offset = 0;
                        bufferInfo.size = size;
                        bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                        bufferInfo.flags = videoExtractor.getSampleFlags();
                        muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo);
                        videoExtractor.advance();
                    }
                }

                if (audioTrackIndex >= 0) {
                    audioExtractor.selectTrack(audioTrackIndex);
                    while (true) {
                        int size = audioExtractor.readSampleData(buffer, 0);
                        if (size < 0) break;
                        bufferInfo.offset = 0;
                        bufferInfo.size = size;
                        bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                        bufferInfo.flags = audioExtractor.getSampleFlags();
                        muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo);
                        audioExtractor.advance();
                    }
                }

                muxer.stop();
                muxer.release();
                videoExtractor.release();
                audioExtractor.release();

                videoFile.delete();
                audioFile.delete();

                Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scan.setData(Uri.fromFile(outputFile));
                activity.sendBroadcast(scan);

                activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(activity,
                        "✅ Download complete: " + outputFileName,
                        android.widget.Toast.LENGTH_SHORT).show()
                );

            } catch (Exception e) {
                Log.e("YTPRO", "❌ Mux error: " + e.getMessage());
                activity.runOnUiThread(() ->
                    android.widget.Toast.makeText(activity,
                        "Mux failed: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}
