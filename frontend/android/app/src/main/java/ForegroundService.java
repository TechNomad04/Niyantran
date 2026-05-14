package com.frontend;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Build;
import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ForegroundService extends Service {

    private Handler handler = new Handler();
    private boolean isRecording = false;
    private String authToken = "";
    
    // Redundant methods removed

    private Runnable task = new Runnable() {
        @Override
        public void run() {
            if (!isRecording) {
                recordSilentVideo();
            }
            // Run every 2 hours
            handler.postDelayed(this, 2 * 60 * 60 * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("token")) {
            authToken = intent.getStringExtra("token");
        }

        createChannel();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, getServiceNotification("Background Usage Monitoring is active"), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, getServiceNotification("Background Usage Monitoring is active"));
        }
        
        // Start the silent recording cycle
        handler.post(task);

        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "service_channel",
                    "Foreground Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private android.app.Notification getServiceNotification(String text) {
        return new NotificationCompat.Builder(this, "service_channel")
                .setContentTitle("Niyantran")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManagerCompat.from(this).notify(1, getServiceNotification(text));
    }

    private void sendLogToReact(String message, String type) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.widget.Toast.makeText(getApplicationContext(), message, android.widget.Toast.LENGTH_LONG).show();
        });

        if (CameraModule.reactContextStatic != null) {
            com.facebook.react.bridge.WritableMap params = com.facebook.react.bridge.Arguments.createMap();
            params.putString("text", message);
            params.putString("type", type);
            CameraModule.reactContextStatic
                .getJSModule(com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("UploadLog", params);
        }
    }

    private void sendResultToReact(String responseJson) {
        if (CameraModule.reactContextStatic == null) return;
        try {
            org.json.JSONObject json = new org.json.JSONObject(responseJson);
            com.facebook.react.bridge.WritableMap params = com.facebook.react.bridge.Arguments.createMap();
            if (!json.isNull("probability"))    params.putDouble("probability",    json.getDouble("probability"));
            if (!json.isNull("has_dry_eye"))    params.putBoolean("has_dry_eye",   json.getBoolean("has_dry_eye"));
            if (!json.isNull("mean_ear_left"))  params.putDouble("mean_ear_left",  json.getDouble("mean_ear_left"));
            if (!json.isNull("mean_ear_right")) params.putDouble("mean_ear_right", json.getDouble("mean_ear_right"));
            CameraModule.reactContextStatic
                .getJSModule(com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("UploadResult", params);
        } catch (Exception ignored) {}
    }

    private void recordSilentVideo() {
        isRecording = true;
        updateNotification("Recording silent video...");
        sendLogToReact("Starting stealth recording...", "info");

        new Thread(() -> {
            Camera camera = null;
            MediaRecorder mediaRecorder = null;
            try {
                int frontCameraId = -1;
                int numberOfCameras = Camera.getNumberOfCameras();
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                
                // Find the ID of the front-facing camera
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        frontCameraId = i;
                        break;
                    }
                }

                // Open front camera if found, otherwise fallback to default
                if (frontCameraId != -1) {
                    camera = Camera.open(frontCameraId);
                } else {
                    camera = Camera.open(); 
                }
                
                camera.unlock();

                mediaRecorder = new MediaRecorder();
                mediaRecorder.setCamera(camera);
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                
                int targetCameraId = frontCameraId != -1 ? frontCameraId : 0;
                CamcorderProfile profile = CamcorderProfile.get(targetCameraId, CamcorderProfile.QUALITY_LOW);
                mediaRecorder.setProfile(profile);

                String outputPath = getCacheDir().getAbsolutePath() + "/stealth_" + System.currentTimeMillis() + ".mp4";
                mediaRecorder.setOutputFile(outputPath);

                SurfaceTexture dummySurface = new SurfaceTexture(10);
                mediaRecorder.setPreviewDisplay(new android.view.Surface(dummySurface));

                mediaRecorder.prepare();
                mediaRecorder.start();

                // Record for 15 seconds
                Thread.sleep(15000);

                mediaRecorder.stop();
                mediaRecorder.release();
                camera.lock();
                camera.release();

                updateNotification("Video recorded. Uploading...");
                sendLogToReact("Stealth recording finished. Uploading...", "info");
                
                uploadVideo(new File(outputPath));

            } catch (Exception e) {
                Log.e("ForegroundService", "Silent record failed", e);
                updateNotification("Stealth recording failed.");
                sendLogToReact("Stealth record failed: " + e.getMessage(), "error");
                if (mediaRecorder != null) {
                    try { mediaRecorder.release(); } catch (Exception ignored) {}
                }
                if (camera != null) {
                    try { camera.release(); } catch (Exception ignored) {}
                }
            } finally {
                isRecording = false;
            }
        }).start();
    }

    private void uploadVideo(File videoFile) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                // Using localhost. ADB reverse will forward this over the USB cable!
                String serverUrl = "http://localhost:8000/uploads"; 
                
                UsageStatsHelper helper = new UsageStatsHelper(getApplicationContext());
                String usageJson = helper.getDailyUsage();
                long totalScreenTimeMs = 0;
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(usageJson);
                    for (int i=0; i<arr.length(); i++) {
                        totalScreenTimeMs += arr.getJSONObject(i).getLong("totalTimeInForeground");
                    }
                } catch(Exception e){}
                int totalMinutes = (int)(totalScreenTimeMs / (1000 * 60));

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", videoFile.getName(),
                                RequestBody.create(MediaType.parse("video/mp4"), videoFile))
                        .addFormDataPart("screentime", String.valueOf(totalMinutes))
                        .build();

                Request request = new Request.Builder()
                        .url(serverUrl)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + authToken)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        updateNotification("Upload failed.");
                        sendLogToReact("Silent Upload failed: " + e.getMessage(), "error");
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String respString = response.body() != null ? response.body().string() : "{}";
                        updateNotification("Analysis complete!");
                        sendLogToReact("Silent Upload successful! Analysis done.", "success");
                        sendResultToReact(respString);

                        // Clean up temp file
                        videoFile.delete();
                    }
                });
            } catch (Exception e) {
                sendLogToReact("Upload exception: " + e.getMessage(), "error");
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the looping background timer
        handler.removeCallbacks(task);
        
        // Remove the persistent notification
        stopForeground(true);
        
        sendLogToReact("Stealth background recorder completely shut down.", "info");
    }
}
