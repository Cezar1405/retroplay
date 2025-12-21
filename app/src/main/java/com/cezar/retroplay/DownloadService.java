package com.cezar.retroplay;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;

public class DownloadService extends Service
{

	private static final int NOTIF_ID = 101;
	private NotificationManager notificationManager;
	private boolean isCancelled = false;
	private Thread downloadThread;

	private String system;
	private String romName;

	@Override
	public void onCreate()
	{
		super.onCreate();
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= 26)
		{
			NotificationChannel channel = new NotificationChannel(
				"descargas",
				"Descargas",
				NotificationManager.IMPORTANCE_LOW
			);
			notificationManager.createNotificationChannel(channel);
		}
	}

	@Override
	public int onStartCommand(final Intent intent, int flags, int startId)
	{
		final String action = intent.getAction();

		if ("CANCEL_DOWNLOAD".equals(action))
		{
			isCancelled = true;
			showToast("Cancelando descarga...");
			return START_NOT_STICKY;
		}

		isCancelled = false;
		romName = intent.getStringExtra("romName");
		final String romUrl = intent.getStringExtra("romUrl");
		system = intent.getStringExtra("system");
		notificationManager.cancel(NOTIF_ID);
		downloadThread = new Thread(new Runnable() {
				public void run()
				{
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

		File romFile = new File(downloadDir, romName);

		if (romFile.exists()) {
			sendBroadcastSimple("DOWNLOAD_COMPLETE", romName);
			showToast("ROM encontrado. Iniciando emulador...");
			launchEmulator(romFile);
			removeNotification();
			return;
		}

		sendBroadcastSimple("DOWNLOAD_START", romName);

		HttpURLConnection connection = null;
		InputStream input = null;
		RandomAccessFile raf = null;
		FileOutputStream fos = null;
		int responseCode = -1;
		String mesageCode = "";

		try {
			String currentUrl = romUrl;

			// --- PRIMERA CONEXIÓN: obtener URL final y cookies, SIN Range ---
			try {
				URL url1 = new URL(currentUrl);
				connection = (HttpURLConnection) url1.openConnection();
				connection.setInstanceFollowRedirects(true);
				connection.setConnectTimeout(20000);
				connection.setReadTimeout(20000);

				connection.setRequestProperty("User-Agent",
											  "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36");
				connection.setRequestProperty("Accept", "*/*");
				connection.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
				connection.setRequestProperty("Referer", "https://archive.org/");
				connection.setRequestProperty("Connection", "keep-alive");
				connection.setRequestProperty("Cookie", "");

				connection.connect();
				currentUrl = connection.getURL().toString(); // URL final
				connection.disconnect();
			} catch (Exception e) {
				e.printStackTrace();
				throw new IOException("Error en primera conexión: " + e.getMessage());
			}

			// --- SEGUNDA CONEXIÓN: descarga real con Range ---
			URL url2 = new URL(currentUrl);
			connection = (HttpURLConnection) url2.openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(20000);
			connection.setReadTimeout(20000);

			connection.setRequestProperty("User-Agent",
										  "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36");
			connection.setRequestProperty("Accept", "*/*");
			connection.setRequestProperty("Accept-Language", "es-ES,es;q=0.9");
			connection.setRequestProperty("Referer", "https://archive.org/");
			connection.setRequestProperty("Connection", "keep-alive");
			connection.setRequestProperty("Sec-Fetch-Dest", "empty");
			connection.setRequestProperty("Sec-Fetch-Mode", "cors");
			connection.setRequestProperty("Sec-Fetch-Site", "same-site");
			connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
			connection.setRequestProperty("Cookie", "");

			long existingLength = 0;
			if (romFile.exists()) existingLength = romFile.length();
			if (existingLength > 0) connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
			else connection.setRequestProperty("Range", "bytes=0-");

			connection.connect();
			responseCode = connection.getResponseCode();
			mesageCode = connection.getResponseMessage();

			if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
				throw new IOException("Código HTTP: " + responseCode);
			}

			// calcular longitud real
			int contentLength = connection.getContentLength();
			long totalLength = contentLength;
			if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
				String contentRange = connection.getHeaderField("Content-Range");
				if (contentRange != null) {
					int slash = contentRange.indexOf('/');
					if (slash >= 0 && slash + 1 < contentRange.length()) {
						try {
							totalLength = Long.parseLong(contentRange.substring(slash + 1));
						} catch (Exception ex) {
							totalLength = existingLength + contentLength;
						}
					} else {
						totalLength = existingLength + contentLength;
					}
				} else {
					totalLength = existingLength + contentLength;
				}
			}

			input = new BufferedInputStream(connection.getInputStream());

			if (existingLength > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL) {
				raf = new RandomAccessFile(romFile, "rw");
				raf.seek(existingLength);
				byte[] buffer = new byte[8192];
				int count;
				long total = existingLength;
				showNotification("Descargando " + romName, 0, false);

				while ((count = input.read(buffer)) != -1) {
					if (isCancelled) {
						raf.close();
						input.close();
						romFile.delete();
						showNotification("Descarga cancelada", 0, true);
						sendBroadcastSimple("DOWNLOAD_CANCELLED", romName);
						removeNotification();
						return;
					}
					raf.write(buffer, 0, count);
					total += count;
					if (totalLength > 0) {
						int progress = (int) (total * 100 / totalLength);
						showNotification("Descargando " + romName, progress, false);
						sendBroadcastProgress(romName, progress);
					}
				}
				raf.close();
			} else {
				fos = new FileOutputStream(romFile);
				BufferedOutputStream output = new BufferedOutputStream(fos);
				byte[] buffer = new byte[8192];
				int count;
				long total = 0;
				showNotification("Descargando " + romName, 0, false);

				while ((count = input.read(buffer)) != -1) {
					if (isCancelled) {
						input.close();
						output.close();
						fos.close();
						romFile.delete();
						showNotification("Descarga cancelada", 0, true);
						sendBroadcastSimple("DOWNLOAD_CANCELLED", romName);
						removeNotification();
						return;
					}
					output.write(buffer, 0, count);
					total += count;
					if (totalLength > 0) {
						int progress = (int) (total * 100 / totalLength);
						showNotification("Descargando " + romName, progress, false);
						sendBroadcastProgress(romName, progress);
					} else sendBroadcastProgress(romName, -1);
				}

				output.flush();
				output.close();
				fos.close();
			}

			input.close();
			connection.disconnect();

			sendBroadcastSimple("DOWNLOAD_COMPLETE", romName);
			showNotification("Descarga completa", 100, true);
			showToast("Descarga finalizada");
			launchEmulator(romFile);
			removeNotification();

		} catch (Exception e) {
			e.printStackTrace();
			sendBroadcastSimple("DOWNLOAD_ERROR", romName);
			showNotification("Error en descarga", 0, true);
			showToast("Error al descargar, code " + responseCode + ", Message: " + mesageCode);
			removeNotification();
		} finally {
			try { if (input != null) input.close(); } catch (Exception ex) {}
			try { if (connection != null) connection.disconnect(); } catch (Exception ex) {}
			try { if (raf != null) raf.close(); } catch (Exception ex) {}
			try { if (fos != null) fos.close(); } catch (Exception ex) {}
		}
	}

	private void cleanPreviousRoms(File downloadDir)
	{
		File[] files = downloadDir.listFiles();
		if (files != null)
		{
			for (int i = 0; i < files.length; i++)
			{
				if (!files[i].isDirectory())
				{
					files[i].delete();
				}
			}
		}
	}

	private void sendBroadcastSimple(String action, String romName)
	{
		Intent intent = new Intent(action);
		intent.putExtra("romName", romName);
		sendBroadcast(intent);
	}

	private void sendBroadcastProgress(String romName, int progress)
	{
		Intent intent = new Intent("DOWNLOAD_PROGRESS");
		intent.putExtra("romName", romName);
		intent.putExtra("progress", progress);
		sendBroadcast(intent);
	}

	private void showNotification(String text, int progress, boolean completed)
	{
		Notification.Builder builder = new Notification.Builder(this)
			.setContentTitle(text)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setAutoCancel(completed);

		if (!completed)
		{
			builder.setProgress(100, progress, false);
		}
		else
		{
			builder.setContentText("Toque para abrir");
			notificationManager.cancel(NOTIF_ID);
		}

		if (Build.VERSION.SDK_INT >= 26)
		{
			builder.setChannelId("descargas");
		}

		notificationManager.notify(NOTIF_ID, builder.build());
	}

	private void removeNotification()
	{
		notificationManager.cancel(NOTIF_ID);
	}

	private void showToast(final String message)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
				public void run()
				{
					Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
				}
			});
	}

	private void launchEmulator(File romFile)
	{
		try
		{
			Intent intent = new Intent(this, CoreSelectorActivity.class);
			intent.putExtra("romName", romFile.getName());
			intent.putExtra("system", system);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
			startActivity(intent);
		}
		catch (Exception e)
		{
			showToast("Error al iniciar emulador");
			e.printStackTrace();
		}
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

}

