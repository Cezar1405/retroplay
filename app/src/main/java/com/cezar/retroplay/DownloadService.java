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

	private void startDownload(String romUrl)
	{
		File downloadDir = new File("/storage/emulated/0/retroplay/temp_download/" + system);
		if (!downloadDir.exists())
		{
			downloadDir.mkdirs();
		}

		File romFile = new File(downloadDir, romName);

		if (romFile.exists())
		{
			sendBroadcastSimple("DOWNLOAD_COMPLETE", romName);
			showToast("ROM encontrado. Iniciando emulador...");
			launchEmulator(romFile);
			removeNotification();
			return;
		}

		sendBroadcastSimple("DOWNLOAD_START", romName);

		try
		{
			//cleanPreviousRoms(downloadDir);

			URL url = new URL(romUrl);
			URLConnection connection = url.openConnection();
			connection.connect();
			int fileLength = connection.getContentLength();

			InputStream input = new BufferedInputStream(url.openStream());
			OutputStream output = new FileOutputStream(romFile);

			byte[] data = new byte[8192];
			int count;
			long total = 0;

			showNotification("Descargando " + romName, 0, false);

			while ((count = input.read(data)) != -1)
			{
				if (isCancelled)
				{
					input.close();
					output.close();
					romFile.delete();
					showNotification("Descarga cancelada", 0, true);
					sendBroadcastSimple("DOWNLOAD_CANCELLED", romName);
					removeNotification();
					return;
				}

				total += count;
				output.write(data, 0, count);

				if (fileLength > 0)
				{
					int progress = (int) (total * 100 / fileLength);
					showNotification("Descargando " + romName, progress, false);
					sendBroadcastProgress(romName, progress);
				}
			}

			output.flush();
			output.close();
			input.close();

			sendBroadcastSimple("DOWNLOAD_COMPLETE", romName);
			showNotification("Descarga completa", 100, true);
			showToast("Descarga finalizada");
			launchEmulator(romFile);
			removeNotification();

		}
		catch (Exception e)
		{
			e.printStackTrace();
			sendBroadcastSimple("DOWNLOAD_ERROR", romName);
			showNotification("Error en descarga", 0, true);
			showToast("Error al descargar " + romName);
			removeNotification();
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

