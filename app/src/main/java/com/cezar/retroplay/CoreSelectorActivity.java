package com.cezar.retroplay;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import android.util.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;
import android.support.v4.content.*;
import android.provider.*;
import java.util.zip.*;
import android.view.*;

public class CoreSelectorActivity extends Activity
{

    public static final String EXTRA_ROM_PATH = "romName";
    public static final String EXTRA_SYSTEM = "system";
    private String romName;
    private String system;
	private JSONArray systemConfig;  // JSON completo
	private JSONArray emuladores;
    private String coreToLaunch;

    // Constantes para las versiones de RetroArch
    private static final String RETROARCH_64BIT = "com.retroarch.aarch64";
    private static final String RETROARCH_32BIT = "com.retroarch.ra32";
    private static final String RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture";


    @Override
    protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);

        // Obtener los extras pasados
        romName = getIntent().getStringExtra(EXTRA_ROM_PATH);
        system = getIntent().getStringExtra(EXTRA_SYSTEM);

        final String romPath = "/storage/emulated/0/retroplay/temp_download/" + system + "/" + romName;

		loadSystemConfig();
		loadEmuladores();

        coreToLaunch = getCoreForSystem(system);
		JSONObject emulador = getEmuladorByNombre(coreToLaunch);

		if (coreToLaunch == null)
		{
			Toast.makeText(this, "No se encontró un core para este sistema: " + system, Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		//azahar
		if ("Azahar Plus".equals(coreToLaunch))
		{
            launchAzaharAsync(romPath);
            return;
        }
		if ("Winlator".equals(coreToLaunch))
		{
            launchWinlatorAsync(romPath);
            return;
        }
		if ("android".equals(system))
		{
			launchAndroidApp(romPath);
			return;
		}
		if ("Vita3k ZX".equals(coreToLaunch))
		{
			launchVita3k(romPath);
			return;
		}
		if ("ps2".equals(system))
		{
			launchNetherAsync(romPath);
			return;
		}

		//llamar emuladores
		if (emulador != null)
		{
			launchEmuladorDinamico(emulador, romPath);
			return;
		}
		else
		{
			Toast.makeText(this, "No se encontró el emulador para: " + coreToLaunch, Toast.LENGTH_LONG).show();
		}

        launchRetroArchWithRom(coreToLaunch, romPath);
		return;
    }

	//llamando json emuladores
	private void loadEmuladores()
	{
		File archivoDestino = new File(Environment.getExternalStorageDirectory(), "retroplay/conf/emuladores.json");

		try
		{
			if (archivoDestino.exists())
			{
				InputStream is = new FileInputStream(archivoDestino);
				int size = is.available();
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();

				String jsonStr = new String(buffer, "UTF-8");
				emuladores = new JSONArray(jsonStr);
			}
			else
			{
				Toast.makeText(this, "No se encontró el archivo emuladores.json", Toast.LENGTH_SHORT).show();
			}
		}
		catch (IOException | JSONException e)
		{
			e.printStackTrace();
		}
	}
	private JSONObject getEmuladorByNombre(String nombre)
	{
		if (emuladores == null) return null;

		try
		{
			for (int i = 0; i < emuladores.length(); i++)
			{
				JSONObject obj = emuladores.getJSONObject(i);
				if (nombre.equalsIgnoreCase(obj.getString("nombre")))
				{
					return obj;
				}
			}
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	//llamando datos del json de los sistemas
	private void loadSystemConfig()
	{
		File archivoDestino = new File(Environment.getExternalStorageDirectory(), "retroplay/conf/system_config.json");

		try
		{
			if (archivoDestino.exists())
			{
				InputStream is = new FileInputStream(archivoDestino);
				int size = is.available();
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();

				String jsonStr = new String(buffer, "UTF-8");
				systemConfig = new JSONArray(jsonStr);
			}
			else
			{
				Toast.makeText(this, "No se encontró el archivo de configuración", Toast.LENGTH_SHORT).show();
			}
		}
		catch (IOException | JSONException e)
		{
			e.printStackTrace();
		}
	}

	private String getCoreForSystem(String system)
	{
		if (systemConfig == null)
		{
			Log.e("getCoreForSystem", "systemConfig es null");
			return null;
		}

		try
		{
			for (int i = 0; i < systemConfig.length(); i++)
			{
				JSONObject obj = systemConfig.getJSONObject(i);
				String sys = obj.getString("system");
				if (sys.equalsIgnoreCase(system))
				{
					String core = obj.optString("coreDef", null);
					Log.d("getCoreForSystem", "Sistema: " + sys + ", Core: " + core);
					return core;
				}
			}
			Log.w("getCoreForSystem", "Sistema no encontrado: " + system);
		}
		catch (Exception e)
		{
			Log.e("getCoreForSystem", "Error leyendo JSON", e);
		}

		return null;
	}

	//iniciando retroarch
    private void launchRetroArchWithRom(final String core, final String romPathOriginal)
	{
		if ("openbor".equals(system) || "snes-msu1".equals(system) || "megadrive-msu".equals(system) || "naomi".equals(system))
		{
			final File romFile = new File(romPathOriginal);

			if (romFile.getName().toLowerCase().endsWith(".zip"))
			{
				// Mostrar diálogo de carga
				final AlertDialog loadingDialog = createLoadingDialog("Descomprimiendo...");
				loadingDialog.show();

				new Thread(new Runnable() {
						@Override
						public void run()
						{
							File descomprimido = null;
							try
							{
								//deleteExtractedFiles(romFile); Limpiar antes de descomprimir
								descomprimido = unzipAllAndFindRom(romFile);

								if (descomprimido == null || !descomprimido.exists())
								{
									throw new IOException("No se encontró archivo dentro del ZIP");
								}

								final String finalRomPath = descomprimido.getAbsolutePath();

								runOnUiThread(new Runnable() {
										@Override
										public void run()
										{
											loadingDialog.dismiss();
											if (!tryLaunchRetroArch(RETROARCH_64BIT, core, finalRomPath))
											{
												tryLaunchRetroArch(RETROARCH_32BIT, core, finalRomPath);
											}
										}
									});

							}
							catch (final Exception e)
							{
								e.printStackTrace();
								final String errorMsg = e.getMessage();
								runOnUiThread(new Runnable() {
										@Override
										public void run()
										{
											loadingDialog.dismiss();
											Toast.makeText(CoreSelectorActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
										}
									});
							}
						}
					}).start();
				return;
			}

			if (romFile.getName().toLowerCase().endsWith(".patchzip"))
			{
				final AlertDialog loadingDialog = createLoadingDialog("Descomprimiendo MSU1...");
				loadingDialog.show();

				new Thread(new Runnable() {
						@Override
						public void run()
						{
							File descomprimido = null;
							try
							{
								deleteExtractedFiles(romFile); // Limpiar antes de descomprimir
								descomprimido = unzipAllAndFindRom(romFile);

								if (descomprimido == null || !descomprimido.exists())
								{
									throw new IOException("No se encontró archivo dentro del ZIP");
								}

								final String finalRomPath = descomprimido.getAbsolutePath();

								runOnUiThread(new Runnable() {
										@Override
										public void run()
										{
											loadingDialog.dismiss();
											if (!tryLaunchRetroArch(RETROARCH_64BIT, core, finalRomPath))
											{
												tryLaunchRetroArch(RETROARCH_32BIT, core, finalRomPath);
											}
										}
									});

							}
							catch (final Exception e)
							{
								e.printStackTrace();
								final String errorMsg = e.getMessage();
								runOnUiThread(new Runnable() {
										@Override
										public void run()
										{
											loadingDialog.dismiss();
											Toast.makeText(CoreSelectorActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
										}
									});
							}
						}
					}).start();
				return;
			}
		}

		// Si no es openbor o no es ZIP, lanzar directamente
		if (!tryLaunchRetroArch(RETROARCH_64BIT, core, romPathOriginal))
		{
			tryLaunchRetroArch(RETROARCH_32BIT, core, romPathOriginal);
		}
	}

    private boolean tryLaunchRetroArch(String packageName, String core, String romPath)
	{
        String configPath = "/storage/emulated/0/Android/data/" + packageName + "/files/retroarch.cfg";
        String coreFinal = "/data/data/" + packageName + "/cores/" + core;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, RETROARCH_ACTIVITY));
        intent.setAction(packageName + "/.browser.retroactivity.RetroActivityFuture");
        intent.putExtra("ROM", romPath);
        intent.putExtra("LIBRETRO", coreFinal);
        intent.putExtra("CONFIGFILE", configPath);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try
		{
            startActivity(intent);
            finish();
            return true;
        }
		catch (Exception e)
		{
            // Si es la versión 64 bits, no mostramos error todavía (probaremos con 32 bits)
            if (packageName.equals(RETROARCH_64BIT))
			{
                return false;
            }

            // Para la versión 32 bits o si no hay más opciones, mostramos el diálogo de descarga
            e.printStackTrace();
            showDownloadDialog(
				"RetroArch no disponible",
				"https://www.retroarch.com"
			);
            return false;
        }
    }

    private void showDownloadDialog(String title, String defaultUrl)
	{
		final String[] opciones = {
			"RetroArch 64 bits",
			"RetroArch 32 bits"
		};

		final String[] urls = {
			"https://buildbot.libretro.com/stable/1.21.0/android/RetroArch_aarch64.apk",
			"https://buildbot.libretro.com/stable/1.21.0/android/RetroArch_ra32.apk"
		};

		final int[] opcionSeleccionada = {0}; // Por defecto la primera

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(title);
		builder.setSingleChoiceItems(opciones, 0, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					opcionSeleccionada[0] = which;
				}
			});
		builder.setPositiveButton("Descargar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					String url = urls[opcionSeleccionada[0]];
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});
		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});
		builder.show();
	}
	//terminando retroarch

	//verificando si los package estan instalados antes de lanzar
	private boolean isAppInstalled(String packageName)
	{
		try
		{
			getPackageManager().getPackageInfo(packageName, 0);
			return true;
		}
		catch (PackageManager.NameNotFoundException e)
		{
			return false;
		}
	}
	//terminando verifica

	//Iniciando Azahar +
	private void launchAzaharAsync(final String romPath)
	{
		// Crear el Dialog de carga
		final AlertDialog loadingDialog = createLoadingDialog("Descomprimiendo...");
		loadingDialog.show();

		new Thread(new Runnable() {
				@Override
				public void run()
				{
					try
					{
						File romFile = new File(romPath);
						if (!romFile.exists())
						{
							throw new FileNotFoundException("Archivo de ROM no encontrado: " + romPath);
						}

						// Descomprimir si es ZIP
						if (romFile.getName().toLowerCase().endsWith(".zip"))
						{
							romFile = unzipAllAndFindRom(romFile);
						}

						final File finalRomFile = romFile;

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									try
									{
										loadingDialog.dismiss(); // Cerrar el diálogo

										String packageName = "io.github.lime3ds.android";
										String activityName = "org.citra.citra_emu.activities.EmulationActivity";

										if (!isAppInstalled(packageName))
										{
											showAzaharMissingDialog();
											return;
										}

										Uri uri;
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
										{
											uri = FileProvider.getUriForFile(
												CoreSelectorActivity.this,
												"com.cezar.retroplay.fileprovider",
												finalRomFile
											);
										}
										else
										{
											uri = Uri.fromFile(finalRomFile);
										}

										Intent intent = new Intent(Intent.ACTION_VIEW);
										intent.setComponent(new ComponentName(packageName, activityName));
										//intent.setDataAndType(uri, "application/octet-stream");
										intent.setDataAndType(uri, "*/*");
										intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
										intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

										startActivity(intent);
										finish();

									}
									catch (Exception e)
									{
										e.printStackTrace();
										Toast.makeText(CoreSelectorActivity.this, "Error al abrir la ROM: " + e.getMessage(), Toast.LENGTH_LONG).show();
									}
								}
							});

					}
					catch (final Exception e)
					{
						e.printStackTrace();
						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									loadingDialog.dismiss();
									Toast.makeText(CoreSelectorActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			}).start();
	}

	private void showAzaharMissingDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Azahar plus no instalado");
		builder.setMessage("Parece que Azahar plus no está instalado.");

		builder.setPositiveButton("Descargar Azahar plus", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					String url = "https://objects.githubusercontent.com/github-production-release-asset-2e65be/957488576/58f41d02-29ff-44ce-8b72-38599d300d10?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=releaseassetproduction%2F20250612%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20250612T203517Z&X-Amz-Expires=300&X-Amz-Signature=ed62061f53dd4547bfd0fdae2b9cc4519e78a81768d721c46acda8d1796f68ec&X-Amz-SignedHeaders=host&response-content-disposition=attachment%3B%20filename%3Dazaharplus-2121.2-A-android_replaces_azahar.apk&response-content-type=application%2Fvnd.android.package-archive";
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});

		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});

		builder.show();
	}
	//terminando Azahar +

	//Iniciando Winlator
	private void launchWinlatorAsync(final String romPath)
	{
		final AlertDialog loadingDialog = createLoadingDialog("Descomprimiendo...");
		loadingDialog.show();

		new Thread(new Runnable() {
				@Override
				public void run()
				{
					try
					{
					    File romFile = new File(romPath);
						if (!romFile.exists())
						{
							throw new FileNotFoundException("ROM no encontrada: " + romPath);
						}

						// Descomprimir si es ZIP
						if (romFile.getName().toLowerCase().endsWith(".zip"))
						{
							romFile = unzipAllAndFindRom(romFile);
						}

						final File finalRomFile = romFile;

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									try
									{
										loadingDialog.dismiss();

										String packageName = "com.winlator";
										String activityName = "com.winlator.XServerDisplayActivity";

										if (!isAppInstalled(packageName))
										{
											showWinlatorMissingDialog();
											return;
										}

										Intent intent = new Intent(Intent.ACTION_MAIN); // ← importante
										intent.setClassName(packageName, activityName);
										intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
														Intent.FLAG_ACTIVITY_CLEAR_TOP |
														Intent.FLAG_ACTIVITY_CLEAR_TASK);

										// Pasar la ruta como string absoluto
										intent.putExtra("shortcut_path", finalRomFile.getAbsolutePath());

										Toast.makeText(CoreSelectorActivity.this,
													   "Lanzando: " + finalRomFile.getName() +
													   "\nRuta: " + finalRomFile.getAbsolutePath(),
													   Toast.LENGTH_LONG).show();

										startActivity(intent);
										finish();

									}
									catch (Exception e)
									{
										e.printStackTrace();
										Toast.makeText(CoreSelectorActivity.this,
													   "Error al abrir el acceso directo: " + e.getMessage(),
													   Toast.LENGTH_LONG).show();
									}
								}
							});

					}
					catch (final Exception e)
					{
						e.printStackTrace();
						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									loadingDialog.dismiss();
									Toast.makeText(CoreSelectorActivity.this,
												   "Error: " + e.getMessage(),
												   Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			}).start();
	}

	private void showWinlatorMissingDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Winlator Glibcmod no instalado");
		builder.setMessage("Parece que Winlator Glibcmod no está instalado.");

		builder.setPositiveButton("Descargar Winlator Glibcmod", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					String url = "https://github.com/coffincolors/winlator/releases/download/Winlator-7.1.3x-Cmod-GLIBC-v11R2/Winlator_7.1.3x_Cmod_GLIBC_v11R2.apk";
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});

		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});

		builder.show();
	}
	//terminando Winlator

	//Iniciando ps2
	private void launchNetherAsync(final String romPath)
	{
		final AlertDialog loadingDialog = createLoadingDialog("Preparando ROM...");
		loadingDialog.show();

		new Thread(new Runnable() {
				@Override
				public void run()
				{
					try
					{
						File romFile = new File(romPath);
						if (!romFile.exists())
						{
							throw new FileNotFoundException("ROM no encontrada: " + romPath);
						}

						String pathInTree = "primary:retroplay/temp_download/ps2/" + romFile.getName();
						String encodedPath = Uri.encode(pathInTree, "/"); // mantiene las barras

						String bootPath = "content://com.android.externalstorage.documents/document/" + encodedPath;
						final Uri bootUri = Uri.parse(bootPath);

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									try
									{
										loadingDialog.dismiss();

										String packageName = "xyz.aethersx2.android"; // o NetherSX2 package
										String activityName = "xyz.aethersx2.android.EmulationActivity";

										if (!isAppInstalled(packageName))
										{
											showNetherMissingDialog();
											return;
										}

										Intent intent = new Intent(Intent.ACTION_MAIN);
										intent.setClassName(packageName, activityName);

										// Añadir flag para permiso lectura URI
										intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
														Intent.FLAG_ACTIVITY_CLEAR_TOP |
														Intent.FLAG_ACTIVITY_CLEAR_TASK |
														Intent.FLAG_GRANT_READ_URI_PERMISSION);
										// Poner la URI en el extra con la clave correcta
										intent.putExtra("bootPath", bootUri.toString());
										startActivity(intent);
										finish();

									}
									catch (Exception e)
									{
										e.printStackTrace();
										Toast.makeText(CoreSelectorActivity.this,
													   "Error: " + e.getMessage(),
													   Toast.LENGTH_LONG).show();
									}
								}
							});

					}
					catch (final Exception e)
					{
						e.printStackTrace();
						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									loadingDialog.dismiss();
									Toast.makeText(CoreSelectorActivity.this,
												   "Error: " + e.getMessage(),
												   Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			}).start();
	}

	private void showNetherMissingDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Winlator Glibcmod no instalado");
		builder.setMessage("Parece que Winlator Glibcmod no está instalado.");

		builder.setPositiveButton("Descargar Winlator Glibcmod", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					String url = "https://github.com/coffincolors/winlator/releases/download/Winlator-7.1.3x-Cmod-GLIBC-v11R2/Winlator_7.1.3x_Cmod_GLIBC_v11R2.apk";
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});

		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});

		builder.show();
	}
	//terminando ps2

	//Iniciando Vita3k
	private void launchVita3k(final String romPath)
	{
		new Thread(new Runnable() {
				@Override
				public void run()
				{
					String psvitaPath = romPath.replaceAll("\\.[^.]+$", ".psvita");
					final File file = new File(psvitaPath);

					if (!file.exists())
					{
						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									Toast.makeText(CoreSelectorActivity.this,
												   "No se encontró el archivo: " + romPath,
												   Toast.LENGTH_LONG).show();
								}
							});
						return;
					}

					final String gameId = readGameIdFromPsvitaFile(psvitaPath); // PCSA00017, etc.

					runOnUiThread(new Runnable() {
							@Override
							public void run()
							{
								try
								{
									String packageName = "org.vita3k.emulator.ikhoeyZX"; 
									String activityName = "org.vita3k.emulator.Emulator";

									if (!isAppInstalled(packageName))
									{
										showVita3kMissingDialog();
										return;
									}

									Intent intent = new Intent(Intent.ACTION_MAIN);
									intent.setClassName(packageName, activityName);
									intent.addCategory(Intent.CATEGORY_LAUNCHER);
									intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
													Intent.FLAG_ACTIVITY_CLEAR_TOP |
													Intent.FLAG_ACTIVITY_CLEAR_TASK);

									// 💡 Usa un array, no un string plano
									String[] args = new String[] { "-r", gameId };
									intent.putExtra("AppStartParameters", args);

									startActivity(intent);
									finish();

								}
								catch (Exception e)
								{
									e.printStackTrace();
									Toast.makeText(CoreSelectorActivity.this,
												   "Error al lanzar Vita3K: " + e.getMessage(),
												   Toast.LENGTH_LONG).show();
								}
							}
						});
				}
			}).start();
	}

// Leer contenido del .psvita
	private String readGameIdFromPsvitaFile(String path)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(path));
			String id = reader.readLine().trim();
			reader.close();
			return id;
		}
		catch (Exception e)
		{
			return "";
		}
	}

	private void showVita3kMissingDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Vita3k ZX no instalado");
		builder.setMessage("Parece que Vita3k ZX no está instalado.");

		builder.setPositiveButton("Descargar Vita3k ZX", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					String url = ".3x-Cmod-GLIBC-v11R2/W";
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});

		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});

		builder.show();
	}
	//terminando Vita3k

	//iniciando lanzar emulador
	private void launchEmuladorDinamico(JSONObject emulador, String romPath)
	{
		try
		{
			String pkg = emulador.getString("pkg");
			String atv = emulador.getString("atv");
			String action = emulador.optString("action", Intent.ACTION_MAIN);
			String filepathkey = emulador.optString("filepathkey");
			String url = emulador.optString("url");

			File romFile = new File(romPath);
			if (!romFile.exists())
			{
				throw new FileNotFoundException("Archivo de ROM no encontrado: " + romPath);
			}

			if (!isAppInstalled(pkg))
			{
				showEmuladorMissingDialog(pkg, url);
				return;
			}

			// Comprobamos si el emulador necesita URI
			boolean useUri = emulador.optBoolean("useUri", false);
			Uri uri = null;

			if (useUri)
			{
				// Si se necesita URI, lo construimos adecuadamente dependiendo de la versión de Android
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
				{
					uri = FileProvider.getUriForFile(
						CoreSelectorActivity.this,
						"com.cezar.retroplay.fileprovider", // Cambia esto según el nombre de tu file provider
						romFile
					);
				}
				else
				{
					uri = Uri.fromFile(romFile);
				}
			}

			// Crear el Intent según la acción y el tipo de URI
			String intentAction;
			if (action.equalsIgnoreCase("VIEW"))
			{
				intentAction = Intent.ACTION_VIEW;
			}
			else if (action.equalsIgnoreCase("MAIN"))
			{
				intentAction = Intent.ACTION_MAIN;
			}
			else
			{
				intentAction = action;
			}
			Intent intent = new Intent(intentAction);
			intent.setComponent(new ComponentName(pkg, atv));

			// Si usa URI, agregamos el URI en lugar del path
			if (useUri)
			{
				intent.setDataAndType(uri, "application/octet-stream");
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
			else
			{
				intent.putExtra(filepathkey, romFile.getAbsolutePath());
			}

			// Configuración adicional de flags
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			startActivity(intent);
			finish();

		}
		catch (Exception e)
		{
			e.printStackTrace();
			Toast.makeText(this, "Error al lanzar emulador: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
	private void showEmuladorMissingDialog(final String packageName, final String url)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Emulador no instalado");
		builder.setMessage("El emulador requerido no está instalado. ¿Deseas descargarlo?");

		builder.setPositiveButton("Descargar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					startActivity(intent);
				}
			});

		builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					dialog.dismiss();
				}
			});

		builder.show();
	}

	//descomprimiendo
	private File unzipAllAndFindRom(File zipFile) throws IOException
	{
		File zipDir = zipFile.getParentFile();

		// Nombre de carpeta basado en el ZIP (sin extensión)
		String zipName = zipFile.getName();
		if (zipName.endsWith(".zip"))
		{
			zipName = zipName.substring(0, zipName.length() - 4);
		}
		File outputDir = new File(zipDir, zipName);

		// Verificar si ya existe la carpeta con la ROM
		if (outputDir.exists() && outputDir.isDirectory())
		{
			File existingRom = findFirstRomInDir(outputDir);
			if (existingRom != null) return existingRom;
		}

		// Descomprimir
		ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
		ZipEntry entry;
		File firstRom = null;

		while ((entry = zis.getNextEntry()) != null)
		{
			File outFile = new File(outputDir, entry.getName());

			if (entry.isDirectory())
			{
				outFile.mkdirs();
			}
			else
			{
				File parent = outFile.getParentFile();
				if (!parent.exists()) parent.mkdirs();

				FileOutputStream fos = new FileOutputStream(outFile);
				byte[] buffer = new byte[4096];
				int len;
				while ((len = zis.read(buffer)) > 0)
				{
					fos.write(buffer, 0, len);
				}
				fos.close();
				zis.closeEntry();

				if (firstRom == null && isRomFile(outFile.getName()))
				{
					firstRom = outFile;
				}
			}
		}
		zis.close();

		// Reemplazar el ZIP con uno vacío ficticio
		if (zipFile.exists())
		{
			zipFile.delete(); // Elimina el original

			// Crea uno nuevo de 0 bytes (solo para simular su existencia)
			boolean created = zipFile.createNewFile();
			if (!created)
			{
				Log.w("ROM", "No se pudo crear el zip ficticio: " + zipFile.getName());
			}
		}

		if (firstRom != null) return firstRom;
		else throw new IOException("No se encontró una ROM válida.");
	}

	private File findFirstRomInDir(File dir)
	{
		File[] files = dir.listFiles();
		if (files == null) return null;

		for (File f : files)
		{
			if (f.isDirectory())
			{
				File rom = findFirstRomInDir(f); // Recursivo por si hay subcarpetas
				if (rom != null) return rom;
			}
			else if (isRomFile(f.getName()))
			{
				return f;
			}
		}
		return null;
	}

	private boolean isRomFile(String name)
	{
		String lower = name.toLowerCase();
		return lower.endsWith(".3ds") ||
			lower.endsWith(".cia") ||
			lower.endsWith(".cci") ||
			name.endsWith(".PBP") ||
			lower.endsWith(".v64") ||
			lower.endsWith(".sfc") ||
			lower.endsWith(".md") ||
			lower.endsWith(".zip") ||
			lower.endsWith(".bin") ||
			lower.endsWith(".desktop");
	}

	//creando dialogo
	private AlertDialog createLoadingDialog(String message)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false); // No permitir cerrar con touch ni botón atrás

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.HORIZONTAL);
		layout.setPadding(30, 30, 30, 30);
		layout.setGravity(Gravity.CENTER_VERTICAL);

		ProgressBar progressBar = new ProgressBar(this);
		progressBar.setIndeterminate(true);
		layout.addView(progressBar);

		TextView textView = new TextView(this);
		textView.setText(message);
		textView.setPadding(30, 0, 0, 0);
		textView.setTextSize(16);
		layout.addView(textView);

		builder.setView(layout);
		return builder.create();
	}

	//eliminando archivos y carpetas descomprimidas
	private void deleteExtractedFiles(File zipFile)
	{
		File parentDir = zipFile.getParentFile();
		File[] files = parentDir.listFiles();
		if (files != null)
		{
			for (File file : files)
			{
				if (!file.equals(zipFile))
				{
					if (file.isDirectory())
					{
						deleteDirectory(file);
					}
					else
					{
						file.delete();
					}
				}
			}
		}
	}

	private void deleteDirectory(File dir)
	{
		if (dir != null && dir.exists())
		{
			File[] files = dir.listFiles();
			if (files != null)
			{
				for (File file : files)
				{
					if (file.isDirectory())
					{
						deleteDirectory(file);
					}
					else
					{
						file.delete();
					}
				}
			}
			dir.delete();
		}
	}

	//iniciando metodo para lanzar juegos android
	private void launchAndroidApp(String str)
	{ 
		try
		{ 
			File file = new File(str); 
			if (file.exists())
			{ 
				BufferedReader bufferedReader = new BufferedReader(new FileReader(file)); 
				String trim = bufferedReader.readLine().trim(); 
				bufferedReader.close(); 
				if (trim.isEmpty())
				{ 
					throw new IllegalArgumentException("El archivo .app está vacío o malformado"); 
				} 
				Intent launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(trim); 
				if (launchIntentForPackage != null)
				{ 
					launchIntentForPackage.addFlags(268435456); 
					startActivity(launchIntentForPackage); 
					finish(); 
					return; 
				} 
				Toast.makeText(this, new StringBuffer().append("No se encontró la app: ").append(trim).toString(), 1).show(); 
				return; 
			} 
			throw new FileNotFoundException(new StringBuffer().append("Archivo .app no encontrado: ").append(str).toString()); 
		}
		catch (Exception e)
		{ 
			e.printStackTrace(); 
			Toast.makeText(this, new StringBuffer().append("Error al abrir app Android: ").append(e.getMessage()).toString(), 1).show(); 
		} 
	} 
	//terminando de lanzar juegos android
}

