package com.cezar.retroplay;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.support.v7.widget.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

public class MainActivity extends Activity
{

	private RecyclerView systemSlider;
	private List<Sistema> sistemas = new ArrayList<>();
	private SystemAdapter adapter;
	private List<SystemConfig> systemConfigs;
	private EditText searchBox;
	private boolean mostrarSoloDescargados = false;
	private TextView toggleList;
	private Sistema sistemaActual;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		systemConfigs = cargarConfiguracionSistema();
		// Configuración para pantalla completa  
		requestWindowFeature(Window.FEATURE_NO_TITLE);  
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);      
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);      

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
		{  
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);   
            View decorView = getWindow().getDecorView();  
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN |   
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |    
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;  
            decorView.setSystemUiVisibility(uiOptions);  
        }  

		setContentView(R.layout.main);      

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			Window window = getWindow();
			window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
			window.getDecorView().setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
				View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			);
			window.setNavigationBarColor(Color.BLACK); // o cualquier otro color que combine
			window.setStatusBarColor(Color.BLACK);
		}
		setContentView(R.layout.main);

		searchBox = findViewById(R.id.searchBox);
		systemSlider = (RecyclerView) findViewById(R.id.systemSlider);

		LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
		systemSlider.setLayoutManager(layoutManager);

		if (Build.VERSION.SDK_INT >= 23)
		{
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			{
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);
			}
			else
			{
				cargarSistemas();
			}
		}
		else
		{
			cargarSistemas();
		}
	    mostrarEspacioDisponible();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
	{
		if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
		{
			cargarSistemas();
		}
		else
		{
			Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
		}
	}

// Clase auxiliar para la configuración del sistema
	public static class SystemConfig
	{
		public String system;
		public String nombre;
		public String descripcion;
		public String coreDef;
		public String[] coreOpciones;

		public SystemConfig(JSONObject obj) throws Exception
		{
			system = obj.getString("system");
			nombre = obj.getString("nombre");
			descripcion = obj.getString("descripcion");
			coreDef = obj.getString("coreDef");

			JSONArray cores = obj.getJSONArray("coreOpciones");
			coreOpciones = new String[cores.length()];
			for (int i = 0; i < cores.length(); i++)
			{
				coreOpciones[i] = cores.getString(i);
			}
		}
	}

	private List<SystemConfig> cargarConfiguracionSistema()
	{
		List<SystemConfig> configs = new ArrayList<>();
		try
		{
			File file = new File("/storage/emulated/0/retroplay/conf/system_config.json");
			FileInputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[(int) file.length()];
			fis.read(buffer);
			fis.close();
			String jsonStr = new String(buffer, "UTF-8");
			JSONArray arr = new JSONArray(jsonStr);
			for (int i = 0; i < arr.length(); i++)
			{
				JSONObject obj = arr.getJSONObject(i);
				configs.add(new SystemConfig(obj));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return configs;
	}

	private void cargarSistemas()
	{
		File carpeta = new File(Environment.getExternalStorageDirectory(), "retroplay/listas/");
		if (carpeta.exists() && carpeta.isDirectory())
		{
			File[] archivos = carpeta.listFiles();
			if (archivos != null)
			{
				for (int i = 0; i < archivos.length; i++)
				{
					File archivo = archivos[i];
					if (archivo.getName().endsWith(".txt"))
					{
						String nombreSistema = archivo.getName().replace(".txt", "");
						List<Juego> juegos = leerJuegosDesdeArchivo(archivo);
						sistemas.add(new Sistema(nombreSistema, juegos));
					}
				}
			}
		}

		adapter = new SystemAdapter(this, sistemas);
		adapter.setOnSystemFocusListener(new SystemAdapter.OnSystemFocusListener() {
				@Override
				public void onSystemFocused(Sistema sistema)
				{
					// Cambiar fondo (como antes)
					try
					{
						String ruta = "fondos/" + sistema.getNombre() + ".webp";
						InputStream is = getAssets().open(ruta);
						Bitmap fondo = BitmapFactory.decodeStream(is);
						ImageView fondoView = findViewById(R.id.dynamicBackground);
						fondoView.setImageBitmap(fondo);
						is.close();
					}
					catch (Exception e)
					{
						try
						{
							InputStream is = getAssets().open("fondos/default_background.webp");
							Bitmap fondo = BitmapFactory.decodeStream(is);
							ImageView fondoView = findViewById(R.id.dynamicBackground);
							fondoView.setImageBitmap(fondo);
							is.close();
						}
						catch (IOException ex)
						{
							ex.printStackTrace(); // por si también falla el fondo default
						}
					}

					// Mostrar datos de configuración
					SystemConfig config = null;
					for (SystemConfig sc : systemConfigs)
					{
						if (sc.system.equalsIgnoreCase(sistema.getNombre()))
						{
							config = sc;
							break;
						}
					}

					Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");      
					Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
					Typeface semiboldFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-SemiBoldCondensed.otf");
					//Typeface texgyreFont = Typeface.createFromAsset(getAssets(), "fonts/texgyre.otf");

					TextView tSystem = findViewById(R.id.tSystem);
					TextView descSystem = findViewById(R.id.descSystem);
					TextView coreSystem = findViewById(R.id.coreSystem);

					tSystem.setTypeface(typeface);
					descSystem.setTypeface(regularFont);
					coreSystem.setTypeface(semiboldFont);

					if (config != null)
					{
						tSystem.setText(config.nombre);
						descSystem.setText(config.descripcion);
						coreSystem.setText(config.coreDef);

						tSystem.setVisibility(View.VISIBLE);
						descSystem.setVisibility(View.VISIBLE);
						coreSystem.setVisibility(View.VISIBLE);
					}
					else
					{
						tSystem.setVisibility(View.GONE);
						descSystem.setVisibility(View.GONE);
						coreSystem.setVisibility(View.GONE);
					}
				}
			});

		adapter.setOnSystemClickListener(new SystemAdapter.OnSystemClickListener() {
				@Override
				public void onSystemClicked(Sistema sistema)
				{
					// Oculta el layout principal y muestra el contenedor de roms
					findViewById(R.id.uiApp).setVisibility(View.GONE);
					findViewById(R.id.container).setVisibility(View.VISIBLE);

					cargarRomsDelSistema(sistema);
				}
			});

		systemSlider.setAdapter(adapter);

		systemSlider.post(new Runnable() {
				@Override
				public void run()
				{
					View firstItem = systemSlider.getLayoutManager().findViewByPosition(0);
					if (firstItem != null)
					{
						firstItem.requestFocus();
					}
				}
			});
	}

	private RomListAdapter romListAdapter; // atributo de clase para usarlo en el filtro

	private void cargarRomsDelSistema(Sistema sistema)
	{
		if (searchBox != null)
		{
			searchBox.setText("");
		}
		List<Juego> juegos = sistema.getJuegos();
		ListView listViewRoms = findViewById(R.id.listViewRoms);

		// Buscar nombre descriptivo desde systemConfigs
		String nombreVisible = sistema.getNombre(); // fallback
		for (SystemConfig config : systemConfigs)
		{
			if (config.system.equalsIgnoreCase(sistema.getNombre()))
			{
				nombreVisible = config.nombre;
				break;
			}
		}

		// Mostrar título
		Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
		String titulo = nombreVisible + " (" + juegos.size() + " juegos disponibles)";
		TextView systemTitle = findViewById(R.id.systemTitle);
		systemTitle.setText(titulo);
		systemTitle.setTypeface(regularFont);

		Toast.makeText(this, "Cargando roms de: " + nombreVisible, Toast.LENGTH_SHORT).show();

		// Fondo default del cabinet
		ImageView baseCabinet = findViewById(R.id.baseCabinet);
		try
		{
			InputStream is = getAssets().open("cabinets/default_back.webp");
			Bitmap bitmap = BitmapFactory.decodeStream(is);
			baseCabinet.setImageBitmap(bitmap);
			is.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		// Cambiar fondo del container según el sistema
		FrameLayout container = findViewById(R.id.container);
		try
		{
			String rutaFondo = "cabinets/" + sistema.getNombre() + ".webp";
			InputStream is = getAssets().open(rutaFondo);
			Bitmap bitmap = BitmapFactory.decodeStream(is);
			container.setBackground(new BitmapDrawable(getResources(), bitmap));
			is.close();
		}
		catch (Exception e)
		{
			try
			{
				// Cargar fondo por defecto desde assets
				InputStream isDefault = getAssets().open("cabinets/default_cabinet.webp");
				Bitmap bitmapDefault = BitmapFactory.decodeStream(isDefault);
				container.setBackground(new BitmapDrawable(getResources(), bitmapDefault));
				isDefault.close();
			}
			catch (Exception ex)
			{
				ex.printStackTrace(); // por si también falla el fondo por defecto
			}
		}

		// Crear adaptador y asignar
		romListAdapter = new RomListAdapter(this, juegos, new RomListAdapter.OnRomClickListener() {
				@Override
				public void onRomClicked(Juego juego)
				{
					Toast.makeText(MainActivity.this, "Click en: " + juego.getNombre(), Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onRomFocused(Juego juego)
				{
					mostrarDatosDeJuego(juego);
				}
			});

		listViewRoms.setAdapter(romListAdapter);
		listViewRoms.requestFocus();

		//final EditText searchBox = findViewById(R.id.searchBox);
		searchBox.addTextChangedListener(new android.text.TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after)
				{ }
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count)
				{
					if (romListAdapter != null)
					{
						romListAdapter.filter(s.toString());
					}
				}
				@Override
				public void afterTextChanged(android.text.Editable s)
				{ }
			});

		// Guarda el sistema actual antes de cualquier filtrado
		sistemaActual = sistema;

		// Configura el toggleList
		toggleList = findViewById(R.id.toggleList);
		Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");
		toggleList.setTypeface(typeface);
		mostrarSoloDescargados = false;
		toggleList.setText("Mis juegos");

		toggleList.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v)
				{
					mostrarSoloDescargados = !mostrarSoloDescargados;
					toggleList.setText(mostrarSoloDescargados ? "Todos los juegos" : "Mis juegos");
					actualizarListaDeJuegos(); // usa sistemaActual
				}
			});
	}

	private void mostrarDatosDeJuego(Juego juego)
	{

		Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");      
		Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
		//Typeface semiboldFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-SemiBoldCondensed.otf");
		//Typeface texgyreFont = Typeface.createFromAsset(getAssets(), "fonts/texgyre.otf");

		TextView romTitle = findViewById(R.id.romTitle);
		TextView romSize = findViewById(R.id.romSize);

		romTitle.setVisibility(View.VISIBLE);
		romSize.setVisibility(View.VISIBLE);
		romTitle.setText(juego.getNombre());
		romTitle.setTypeface(typeface);
		romSize.setText(juego.getPeso());
		romSize.setTypeface(regularFont);

		cargar3DBox(juego.getNombre(), juego.getSistema());
		cargarCover(juego.getNombre(), juego.getSistema());
		cargarVideoPreview(juego.getNombre(), juego.getSistema());
		cargarMarquee(juego.getNombre(), juego.getSistema());
		// ImageView dynamicCover = findViewById(R.id.dynamicCover);
		// ImageView dynamicScreenshot = findViewById(R.id.dynamicScreenshot);
		// VideoView videoPreview = findViewById(R.id.videoPreview);
	}

	private String mapearSistema(String sistema)
	{
		switch (sistema.toLowerCase())
		{
			case "gamegear": return "segaGG";
			case "mame": return "arcade";
			case "mastersystem": return "segaMS";
			case "megadrive": return "segaMD";
			case "megadrive-msu": return "msu-md";
			case "saturn": return "segaSaturn";
			case "segacd": return "segaCD";
			case "snes-msu1": return "msu1";
			case "virtualboy": return "vb";
			case "wonderswan":
			case "wonderswancolor": return "ws";
			default: return sistema.toLowerCase();
		}
	}

	//inicio Marquee
	private void cargarMarquee(final String romName, final String selectedSystem)
	{
		final ImageView dynamicMarquee = findViewById(R.id.dynamicMarquee);
		final String romNameSE = romName.replaceAll("\\.[^.]+$", ""); // sin extensión
		final String systemAlias = mapearSistema(selectedSystem);
		final String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/logos/" + romNameSE + ".png";
		final String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/marquees/" + romNameSE + ".webp";

		final File localFile = new File(Environment.getExternalStorageDirectory(),
										"retroplay/media/" + systemAlias + "/marquees/" + romNameSE + ".webp");

		// Si existe local, mostrar y salir
		if (localFile.exists())
		{
			try
			{
				FileInputStream fis = new FileInputStream(localFile);
				final Drawable localDrawable = Drawable.createFromStream(fis, null);
				fis.close();

				dynamicMarquee.setVisibility(View.VISIBLE);
				dynamicMarquee.setImageDrawable(localDrawable);
				return;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr)
			{
				this.urlStr = urlStr;
			}

			@Override
			public void run()
			{
				try
				{
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(3000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true))
					{
						final InputStream input = connection.getInputStream();
						final Drawable drawable = Drawable.createFromStream(input, null);
						input.close();

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									dynamicMarquee.setVisibility(View.VISIBLE);
									dynamicMarquee.setImageDrawable(drawable);
								}
							});
						return;
					}
				}
				catch (Exception e)
				{
					// ignorado
				}

				if (loaded.compareAndSet(false, true))
				{
					runOnUiThread(new Runnable() {
							@Override
							public void run()
							{
								dynamicMarquee.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin Marquee

	//inicio 3dbox
	private void cargar3DBox(final String romName, final String selectedSystem)
	{
		final ImageView dynamic3dbox = findViewById(R.id.dynamic3dbox);
		final String romNameSE = romName.replaceAll("\\.[^.]+$", ""); // sin extensión
		final String systemAlias = mapearSistema(selectedSystem);
		final String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/corners/" + romNameSE + ".png";
		final String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/3dbox/" + romNameSE + ".webp";

		final File localFile = new File(Environment.getExternalStorageDirectory(),
										"retroplay/media/" + systemAlias + "/3dbox/" + romNameSE + ".webp");

		// Si existe local, mostrar y salir
		if (localFile.exists())
		{
			try
			{
				FileInputStream fis = new FileInputStream(localFile);
				final Drawable localDrawable = Drawable.createFromStream(fis, null);
				fis.close();

				dynamic3dbox.setVisibility(View.VISIBLE);
				dynamic3dbox.setImageDrawable(localDrawable);
				return;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr)
			{
				this.urlStr = urlStr;
			}

			@Override
			public void run()
			{
				try
				{
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(3000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true))
					{
						final InputStream input = connection.getInputStream();
						final Drawable drawable = Drawable.createFromStream(input, null);
						input.close();

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									dynamic3dbox.setVisibility(View.VISIBLE);
									dynamic3dbox.setImageDrawable(drawable);
								}
							});
						return;
					}
				}
				catch (Exception e)
				{
					// ignorado
				}

				if (loaded.compareAndSet(false, true))
				{
					runOnUiThread(new Runnable() {
							@Override
							public void run()
							{
								dynamic3dbox.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin 3dbox

	//Cover
	private void cargarCover(final String romName, final String selectedSystem)
	{
		final ImageView dynamicCover = findViewById(R.id.dynamicCover);
		final String romNameSE = romName.replaceAll("\\.[^.]+$", ""); // sin extensión
		final String systemAlias = mapearSistema(selectedSystem);
		final String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/covers/" + romNameSE + ".png";
		final String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/covers/" + romNameSE + ".webp";

		final File localFile = new File(Environment.getExternalStorageDirectory(),
										"retroplay/media/" + systemAlias + "/covers/" + romNameSE + ".webp");

		// Si existe local, mostrar y salir
		if (localFile.exists())
		{
			try
			{
				FileInputStream fis = new FileInputStream(localFile);
				final Drawable localDrawable = Drawable.createFromStream(fis, null);
				fis.close();

				dynamicCover.setVisibility(View.VISIBLE);
				dynamicCover.setImageDrawable(localDrawable);
				return;
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr)
			{
				this.urlStr = urlStr;
			}

			@Override
			public void run()
			{
				try
				{
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(3000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true))
					{
						final InputStream input = connection.getInputStream();
						final Drawable drawable = Drawable.createFromStream(input, null);
						input.close();

						runOnUiThread(new Runnable() {
								@Override
								public void run()
								{
									dynamicCover.setVisibility(View.VISIBLE);
									dynamicCover.setImageDrawable(drawable);
								}
							});
						return;
					}
				}
				catch (Exception e)
				{
					// ignorado
				}

				if (loaded.compareAndSet(false, true))
				{
					runOnUiThread(new Runnable() {
							@Override
							public void run()
							{
								dynamicCover.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin Cover

	//Video preview
	private void cargarVideoPreview(final String romName, final String selectedSystem)
	{
		final VideoView videoPreview = findViewById(R.id.videoPreview);
		final ImageView dynamicScreenshot = findViewById(R.id.dynamicScreenshot);

		final String romNameSE = romName.contains(".") ? romName.replaceAll("\\.[^.]+$", "") : romName;
		final String systemAlias = mapearSistema(selectedSystem);

		final String videoURL1 = "https://gam.onl/user/" + systemAlias + "/videos/" + romNameSE + ".mp4";
		final String videoURL2 = "https://github.com/Cezar1405/retroplay/raw/refs/heads/main/media/" + systemAlias + "/videos/" + romNameSE + ".mp4";

		new Thread(new Runnable() {
				@Override
				public void run()
				{
					final boolean[] videoFound = {false};

					// Verificar en paralelo ambas URLs
					Thread t1 = new Thread(new Runnable() {
							@Override
							public void run()
							{
								checkUrl(videoURL1, videoFound, videoPreview, dynamicScreenshot);
							}
						});
					Thread t2 = new Thread(new Runnable() {
							@Override
							public void run()
							{
								checkUrl(videoURL2, videoFound, videoPreview, dynamicScreenshot);
							}
						});
					t1.start();
					t2.start();

					try
					{
						t1.join();
						t2.join();
					}
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}

					// Si ninguna URL sirvió, mostrar el screenshot
					if (!videoFound[0])
					{
						mostrarScreenshotFallback(dynamicScreenshot, videoPreview, romNameSE, systemAlias);
					}
				}
			}).start();
	}

	private void checkUrl(final String urlToCheck, final boolean[] videoFound, final VideoView videoPreview, final ImageView dynamicScreenshot)
	{
		try
		{
			URL url = new URL(urlToCheck);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("HEAD");
			connection.setConnectTimeout(2000);
			connection.setReadTimeout(2000);
			int responseCode = connection.getResponseCode();
			connection.disconnect();

			if (responseCode == HttpURLConnection.HTTP_OK && !videoFound[0])
			{
				videoFound[0] = true;

				runOnUiThread(new Runnable() {
						@Override
						public void run()
						{
							videoPreview.setVisibility(View.VISIBLE);
							dynamicScreenshot.setVisibility(View.GONE);
							videoPreview.setVideoURI(Uri.parse(urlToCheck));
							videoPreview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
									@Override
									public void onPrepared(MediaPlayer mp)
									{
										videoPreview.start();
									}
								});
						}
					});
			}
		}
		catch (Exception ignored)
		{}
	}

	private void mostrarScreenshotFallback(final ImageView screenshot, final VideoView videoPreview, final String romNameSE, final String systemAlias)
	{
		runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					videoPreview.setVisibility(View.GONE);
					//screenshot.setVisibility(View.VISIBLE);

					File screenFile = new File(Environment.getExternalStorageDirectory(),
											   "retroplay/media/" + systemAlias + "/screenshots/" + romNameSE + ".webp");

					FileInputStream fis = null;
					try
					{
						fis = new FileInputStream(screenFile);
						screenshot.setImageDrawable(Drawable.createFromStream(fis, null));
					}
					catch (FileNotFoundException e)
					{
						// No se encontró local, cargar desde internet
						final String imageUrl = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/screenshots/" + romNameSE + ".webp";

						new Thread(new Runnable() {
								@Override
								public void run()
								{
									try
									{
										URL url = new URL(imageUrl);
										HttpURLConnection conn = (HttpURLConnection) url.openConnection();
										conn.setDoInput(true);
										conn.connect();
										final InputStream input = conn.getInputStream();
										final Bitmap bitmap = BitmapFactory.decodeStream(input);

										runOnUiThread(new Runnable() {
												@Override
												public void run()
												{
													if (bitmap != null)
													{
														screenshot.setImageBitmap(bitmap);
														screenshot.setVisibility(View.VISIBLE);
													}
													else
													{
														screenshot.setVisibility(View.GONE);
													}
												}
											});
									}
									catch (Exception ex)
									{
										runOnUiThread(new Runnable() {
												@Override
												public void run()
												{
													screenshot.setVisibility(View.GONE);
												}
											});
										ex.printStackTrace();
									}
								}
							}).start();
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
					finally
					{
						if (fis != null)
						{
							try
							{
								fis.close();
							}
							catch (IOException e)
							{
								e.printStackTrace();
							}
						}
					}
				}
			});
	}
	//fin videopreview

	//filtro descargados
	private void actualizarListaDeJuegos()
	{
		if (romListAdapter == null || sistemaActual == null) return;

		List<Juego> todos = sistemaActual.getJuegos();
		List<Juego> filtrados = new ArrayList<>();

		File carpeta = new File(Environment.getExternalStorageDirectory(), "/retroplay/temp_download/" + sistemaActual.getNombre());

		for (Juego juego : todos)
		{
			String nombreRom = juego.getNombre();
			String baseName = nombreRom.replaceAll("\\.[^.]+$", ""); // Quitar extensión

			boolean descargado = false;

			if (carpeta.exists() && carpeta.isDirectory())
			{
				File[] archivos = carpeta.listFiles();
				if (archivos != null)
				{
					for (File archivo : archivos)
					{
						if (archivo.getName().startsWith(baseName))
						{
							descargado = true;
							break;
						}
					}
				}
			}

			if (!mostrarSoloDescargados || descargado)
			{
				filtrados.add(juego);
			}
		}

		romListAdapter.actualizarLista(filtrados);
	}
	//fin descargados

	//calcular espacio
	private void mostrarEspacioDisponible()
	{
		TextView espacioDisp = findViewById(R.id.espacioDisp);

		File path = Environment.getDataDirectory(); // almacenamiento interno: /data

		StatFs stat = new StatFs(path.getPath());

		long blockSize, totalBlocks, availableBlocks;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
		{
			blockSize = stat.getBlockSizeLong();
			totalBlocks = stat.getBlockCountLong();
			availableBlocks = stat.getAvailableBlocksLong();
		}
		else
		{
			blockSize = stat.getBlockSize();
			totalBlocks = stat.getBlockCount();
			availableBlocks = stat.getAvailableBlocks();
		}

		long totalBytes = totalBlocks * blockSize;
		long availableBytes = availableBlocks * blockSize;

		String texto = "Espacio disponible: " + formatSize(availableBytes) + " / " + formatSize(totalBytes);
		espacioDisp.setText(texto);
	}

	private String formatSize(long size)
	{
		float kb = size / 1024f;
		float mb = kb / 1024f;
		float gb = mb / 1024f;

		if (gb >= 1)
		{
			return String.format(Locale.US, "%.2f GB", gb);
		}
		else if (mb >= 1)
		{
			return String.format(Locale.US, "%.2f MB", mb);
		}
		else
		{
			return String.format(Locale.US, "%.2f KB", kb);
		}
	}
	//fin de calculo espacio

	private List<Juego> leerJuegosDesdeArchivo(File archivo)
	{
		List<Juego> lista = new ArrayList<>();

		try
		{
			BufferedReader br = new BufferedReader(new FileReader(archivo));
			String linea;
			while ((linea = br.readLine()) != null)
			{
				String[] partes = linea.split("=");
				if (partes.length >= 3)
				{
					String nombre = partes[0];
					String url = partes[1];
					String sistema = partes[2];
					String peso = (partes.length >= 4) ? partes[3] : "";
					lista.add(new Juego(nombre, url, sistema, peso));
				}
			}
			br.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return lista;
	}

	@Override
	public void onBackPressed()
	{
		View container = findViewById(R.id.container);
		View uiApp = findViewById(R.id.uiApp);

		if (container.getVisibility() == View.VISIBLE)
		{
			EditText searchBox = findViewById(R.id.searchBox);
			if (searchBox != null)
			{
				searchBox.setText("");
			}
			// Volver a la pantalla principal
			container.setVisibility(View.GONE);
			uiApp.setVisibility(View.VISIBLE);

			//findViewById(R.id.container).setVisibility(View.GONE);    
			//findViewById(R.id.sliderScroll).setVisibility(View.VISIBLE);
			findViewById(R.id.dynamicCover).setVisibility(View.GONE);
			findViewById(R.id.videoPreview).setVisibility(View.GONE);
			findViewById(R.id.romTitle).setVisibility(View.GONE);
			findViewById(R.id.romSize).setVisibility(View.GONE);
			//findViewById(R.id.searchBox).setVisibility(View.GONE);
			//final ImageView dynamicBackground = findViewById(R.id.dynamicBackground);
		    //dynamicBackground.setImageResource(R.drawable.background_image);
			findViewById(R.id.dynamic3dbox).setVisibility(View.GONE);
			findViewById(R.id.dynamicScreenshot).setVisibility(View.GONE);
			findViewById(R.id.dynamicMarquee).setVisibility(View.GONE);
			//findViewById(R.id.espacioDisp).setVisibility(View.GONE);
			//findViewById(R.id.dynamicScreenshot).setVisibility(View.GONE);
			//findViewById(R.id.toggleList).setVisibility(View.GONE);
			//findViewById(R.id.paypalButton).setVisibility(View.VISIBLE);
		}
		else
		{
			// Confirmar salida
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Salir de RetroPlay");
			builder.setMessage("¿Estás seguro de que deseas salir?");
			builder.setPositiveButton("Sí, salir", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						finish(); // Cierra la app
					}
				});
			builder.setNegativeButton("No", null);
			builder.show();
		}
	}
}
