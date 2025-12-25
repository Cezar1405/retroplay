package com.cezar.retroplay;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;

public class DownloadService extends Service {

    private static final int NOTIF_ID = 101;
    private NotificationManager notificationManager;
    private boolean isCancelled = false;
    private Thread downloadThread;

    private String system;
    private String romName;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                "descargas",
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            );
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {

        if ("CANCEL_DOWNLOAD".equals(intent.getAction())) {
            isCancelled = true;
            showToast("Cancelando descarga...");
            return START_NOT_STICKY;
        }

        isCancelled = false;
        romName = intent.getStringExtra("romName");
        final String romUrl = intent.getStringExtra("romUrl");
        system = intent.getStringExtra("system");

        downloadThread = new Thread(new Runnable() {
				public void run() {
					startDownload(romUrl);
					stopSelf();
				}
			});
        downloadThread.start();

        return START_NOT_STICKY;
    }

    private void startDownload(String romUrl) {

        File downloadDir = new File("/storage/emulated/0/retroplay/temp_download/" + system);
        if (!downloadDir.exists()) downloadDir.mkdirs();

        String extension = "";
        int dot = romUrl.lastIndexOf('.');
        if (dot != -1) extension = romUrl.substring(dot);

        String baseName = romName;
        int nd = romName.lastIndexOf('.');
        if (nd != -1) baseName = romName.substring(0, nd);

        File romFile = new File(downloadDir, baseName + extension);

        sendBroadcastSimple("DOWNLOAD_START", romName);

        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;

        try {
            URL url = new URL(romUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);

            connection.setRequestProperty("User-Agent", "Android");
            connection.setRequestProperty("Accept", "*/*");

            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode);
            }

            String contentType = connection.getContentType();
            if (contentType == null || contentType.contains("text/html")) {
                throw new IOException("No es archivo descargable");
            }

            int totalLength = connection.getContentLength();

            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(romFile);

            byte[] buffer = new byte[8192];
            long total = 0;
            int count;

            // Primero notificamos inicio con 0
            showNotification(romName, 0, 0, totalLength, false);

            while ((count = input.read(buffer)) != -1) {
                if (isCancelled) {
                    romFile.delete();
                    sendBroadcastSimple("DOWNLOAD_CANCELLED", romName);
                    removeNotification();
                    return;
                }

                output.write(buffer, 0, count);
                total += count;

                if (totalLength > 0) {
                    int progress = (int) (total * 100 / totalLength);

                    // Calcular MB
                    float downloadedMB = total / (1024f * 1024f);
                    float totalMB = totalLength / (1024f * 1024f);

                    // Actualizar notificación y broadcast
                    showNotification(romName, progress, downloadedMB, totalMB, false);
                    sendBroadcastProgress(romName, progress, String.format("%.1f/%.1f MB", downloadedMB, totalMB));
                }
            }

            output.flush();

            sendBroadcastSimple("DOWNLOAD_COMPLETE", romName);
            showNotification(romName, 100, totalLength / (1024f * 1024f), totalLength / (1024f * 1024f), true);
			removeNotification();
            launchEmulator(romFile);

        } catch (Exception e) {
            if (romFile.exists()) romFile.delete();
            sendBroadcastSimple("DOWNLOAD_ERROR", romName);
            showToast("Error al descargar");
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) {}
            try { if (output != null) output.close(); } catch (Exception ignored) {}
            try { if (connection != null) connection.disconnect(); } catch (Exception ignored) {}
        }
    }

    private void sendBroadcastSimple(String action, String romName) {
        Intent intent = new Intent(action);
        intent.putExtra("romName", romName);
        sendBroadcast(intent);
    }

    private void sendBroadcastProgress(String romName, int progress, String mbText) {
        Intent intent = new Intent("DOWNLOAD_PROGRESS");
        intent.putExtra("romName", romName);
        intent.putExtra("progress", progress);
        intent.putExtra("mbText", mbText);
        sendBroadcast(intent);
    }

    // Nuevo showNotification
    private void showNotification(String romName, int progress, float downloadedMB, float totalMB, boolean done) {
        String text = String.format("%.1f/%.1f MB", downloadedMB, totalMB);

        Notification.Builder builder = new Notification.Builder(this)
            .setContentTitle(romName) // Mantener título del archivo
            .setContentText(text)     // Mostrar MB descargados
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setAutoCancel(done);

        if (!done) {
            builder.setProgress(100, progress, false);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            builder.setChannelId("descargas");
        }

        notificationManager.notify(NOTIF_ID, builder.build());
    }

    private void removeNotification() {
        notificationManager.cancel(NOTIF_ID);
    }

    private void showToast(final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
				public void run() {
					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
				}
			});
    }

    private void launchEmulator(File romFile) {
        Intent intent = new Intent(this, CoreSelectorActivity.class);
        intent.putExtra("romName", romFile.getName());
        intent.putExtra("system", system);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
