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
import android.support.v4.util.*;
import android.support.v7.widget.*;
import android.util.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

import android.support.v4.util.LruCache;

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
	private LruCache<String, Bitmap> memoryCache;
	private android.app.ProgressDialog progressDialog;
	private android.app.ProgressDialog downloadProgressDialog;
	private String currentRomName = "";
	private int lastProgress = 0;
	private boolean isDownloading = false;
	private RecyclerView recyclerRoms;
	private int lastFocusedRomIndex = 0;
	private ImageView ajustes;
	String basePath = Environment.getExternalStorageDirectory() + "/retroplay/listas/"; // importante el /
	String baseOcultas = Environment.getExternalStorageDirectory() + "/retroplay/listas/ocultas/";



	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		initImageCache();
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
			window.setNavigationBarColor(Color.BLACK);
			window.setStatusBarColor(Color.BLACK);
		}

		searchBox = findViewById(R.id.searchBox);
		systemSlider = (RecyclerView) findViewById(R.id.systemSlider);
		
		ajustes = findViewById(R.id.ajustes);
		ajustes.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mostrarDialogoSistemas();
				}
			});

		LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
		systemSlider.setLayoutManager(layoutManager);

		// 🔒 Verificar permisos y copiar archivos base antes de cargar sistemas
		if (Build.VERSION.SDK_INT >= 23)
		{
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
				|| checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			{
				requestPermissions(
					new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
					1001
				);
			}
			else
			{
				inicializarArchivosBase(); // 👈 copia listas, configuraciones y crea /ocultas
				cargarSistemas();          // 👈 luego carga los sistemas
				actualizarListaDeJuegos();
			}
		}
		else
		{
			inicializarArchivosBase(); // 👈 versiones viejas
			cargarSistemas();
			actualizarListaDeJuegos();
		}

		obtenerEspacioAlmacenamiento();
	}
	
	private void initImageCache()  
	{      
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);      
		final int cacheSize = maxMemory / 8;      

		memoryCache = new LruCache<String, Bitmap>(cacheSize) {      
			@Override      
			protected int sizeOf(String key, Bitmap bitmap)  
			{      
				return bitmap.getByteCount() / 1024;      
			}      
		};      
	}      

	private void addBitmapToMemoryCache(String key, Bitmap bitmap)  
	{      
		if (getBitmapFromMemCache(key) == null)  
		{      
			memoryCache.put(key, bitmap);      
		}      
	}      

	private Bitmap getBitmapFromMemCache(String key)  
	{      
		return memoryCache.get(key);      
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
				Arrays.sort(archivos);
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

					final SystemConfig finalConfig = config;

					Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");      
					Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
					Typeface semiboldFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-SemiBoldCondensed.otf");
					//Typeface texgyreFont = Typeface.createFromAsset(getAssets(), "fonts/texgyre.otf");

					TextView tSystem = findViewById(R.id.tSystem);
					TextView descSystem = findViewById(R.id.descSystem);
					final TextView coreSystem = findViewById(R.id.coreSystem);

					tSystem.setTypeface(typeface);
					descSystem.setTypeface(regularFont);
					coreSystem.setTypeface(semiboldFont);

					if (config != null)
					{
						tSystem.setText(config.nombre);
						descSystem.setText(config.descripcion);
						coreSystem.setText(config.coreDef);
						
						// Hacer clic en el TextView para elegir otro emulador
						coreSystem.setOnClickListener(new View.OnClickListener() {
								@Override
								public void onClick(View v) {
									if (finalConfig != null && finalConfig.coreOpciones != null && finalConfig.coreOpciones.length > 0) {
										PopupMenu popup = new PopupMenu(MainActivity.this, v);

										for (int i = 0; i < finalConfig.coreOpciones.length; i++) {
											popup.getMenu().add(finalConfig.coreOpciones[i]);
										}

										popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
												@Override
												public boolean onMenuItemClick(MenuItem item) {
													String seleccionado = item.getTitle().toString();
													coreSystem.setText(seleccionado);
													finalConfig.coreDef = seleccionado; // Actualiza el valor por defecto en memoria

													// Opcional: guardar de inmediato en el archivo JSON
													guardarConfiguracionSistema(systemConfigs);

													Toast.makeText(MainActivity.this, "Emulador: " + seleccionado, Toast.LENGTH_SHORT).show();
													return true;
												}
											});

										popup.show();
									}
								}
							});

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
					actualizarListaDeJuegos();
				}
			});

		systemSlider.setAdapter(adapter);

	// Restaurar foco del sistema previamente enfocado
		int posFoco = -1;
		for (int i = 0; i < sistemas.size(); i++) {
			if (sistemas.get(i).isFocado()) {
				posFoco = i;
				break;
			}
		}

		final int focoParaRunnable = posFoco; // sí necesitas final aquí para el Runnable

		systemSlider.post(new Runnable() {
				@Override
				public void run() {
					if (focoParaRunnable != -1) {
						RecyclerView.ViewHolder vh = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
						if (vh != null) {
							vh.itemView.requestFocus();
						} else {
							systemSlider.scrollToPosition(focoParaRunnable);
							systemSlider.post(new Runnable() {
									@Override
									public void run() {
										RecyclerView.ViewHolder vh2 = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
										if (vh2 != null) vh2.itemView.requestFocus();
									}
								});
						}
					} else {
						View firstItem = systemSlider.getLayoutManager().findViewByPosition(0);
						if (firstItem != null) firstItem.requestFocus();
					}
				}
			});
	}
	
	//Configuracion de los emuladores predeterminados
	private void guardarConfiguracionSistema(List<SystemConfig> listaConfigs) {
		try {
			JSONArray arr = new JSONArray();
			for (SystemConfig sc : listaConfigs) {
				JSONObject obj = new JSONObject();
				obj.put("system", sc.system);
				obj.put("nombre", sc.nombre);
				obj.put("descripcion", sc.descripcion);
				obj.put("coreDef", sc.coreDef);

				JSONArray cores = new JSONArray();
				for (int i = 0; i < sc.coreOpciones.length; i++) {
					cores.put(sc.coreOpciones[i]);
				}
				obj.put("coreOpciones", cores);

				arr.put(obj);
			}

			File file = new File("/storage/emulated/0/retroplay/conf/system_config.json");
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(arr.toString(2).getBytes("UTF-8"));
			fos.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//Metodo de inicializacion para copiar listas y configuraciones
	private void inicializarArchivosBase() {
		File baseDir = new File(Environment.getExternalStorageDirectory(), "retroplay");
		File listasDir = new File(baseDir, "listas");
		File ocultasDir = new File(listasDir, "ocultas");
		File confDir = new File(baseDir, "conf");

		if (!listasDir.exists()) listasDir.mkdirs();
		if (!ocultasDir.exists()) ocultasDir.mkdirs();
		if (!confDir.exists()) confDir.mkdirs();

		// --- SOLO COPIA SI LA CARPETA LISTAS ESTÁ VACÍA ---
		if (carpetaVacia(listasDir)) {
			copiarDirectorioAssetsFiltrado("listas", listasDir, ".txt");
		}

		copiarAssetSiNoExiste("emuladores.json", new File(confDir, "emuladores.json"));
		copiarAssetSiNoExiste("system_config.json", new File(confDir, "system_config.json"));
	}

	private boolean carpetaVacia(File dir) {
		File[] archivos = dir.listFiles();
		return archivos == null || archivos.length == 0;
	}

// Copia un archivos de configuraciones si no existe aún
	private void copiarAssetSiNoExiste(String assetPath, File destino) {
		try {
			if (!destino.exists()) {
				InputStream in = getAssets().open(assetPath);
				OutputStream out = new FileOutputStream(destino);
				byte[] buffer = new byte[1024];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
				in.close();
				out.flush();
				out.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

// Copia solo sistemas en txt si no existen
	private void copiarDirectorioAssetsFiltrado(String assetDir, File destinoDir, String extension) {
		try {
			String[] archivos = getAssets().list(assetDir);
			if (archivos == null) return;

			if (!destinoDir.exists()) destinoDir.mkdirs();

			for (String nombre : archivos) {
				if (nombre.endsWith(extension)) {
					InputStream in = getAssets().open(assetDir + "/" + nombre);
					File outFile = new File(destinoDir, nombre);
					if (!outFile.exists()) {
						OutputStream out = new FileOutputStream(outFile);
						byte[] buffer = new byte[1024];
						int read;
						while ((read = in.read(buffer)) != -1) {
							out.write(buffer, 0, read);
						}
						in.close();
						out.flush();
						out.close();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//

	private RomAdapter romListAdapter; // atributo de clase para usarlo en el filtro
	private SystemAdapter systemAdapter; // atributo de clase para usarlo en el filtro
	
	private boolean deleteRecursive(File fileOrDir)
	{
		if (fileOrDir.isDirectory())
		{
			for (File child : fileOrDir.listFiles())
			{
				deleteRecursive(child);
			}
		}
		return fileOrDir.delete();
	}
	
	//Inicio proceso descarga mediafire
	public interface MediaFireCallback
	{
		void onSuccess(String directUrl);
		void onError(String errorMessage);
	}

	public void getMediaFireDirectUrl(final String mediafireUrl, final MediaFireCallback callback)
	{
		final WebView webView = findViewById(R.id.mediafireWebView);
		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setDomStorageEnabled(true);
		webView.getSettings().setUserAgentString("Mozilla/5.0");

		webView.setWebViewClient(new WebViewClient() {
				public void onPageFinished(WebView view, String url)
				{
					webView.evaluateJavascript(
						"(function() {" +
						"var link = document.getElementById('downloadButton');" +
						"if (link) return link.href;" +
						"return null;" +
						"})()", new ValueCallback<String>() {
							@Override
							public void onReceiveValue(String value)
							{
								if (value != null && !value.equals("null"))
								{
									// Quitar comillas del string devuelto por evaluateJavascript
									String cleanUrl = value.replaceAll("^\"|\"$", "").replace("\\u0026", "&");
									if (!cleanUrl.startsWith("http"))
									{
										cleanUrl = "https:" + cleanUrl;
									}
									callback.onSuccess(cleanUrl);
								}
								else
								{
									callback.onError("No se encontró el botón de descarga.");
								}
							}
						}
					);
				}

				public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
				{
					callback.onError("Error cargando MediaFire: " + description);
				}
			});

		webView.loadUrl(mediafireUrl);
	}
	//Fin proceso descarga mediafire
	
	//Proceso con CDromance
	public interface CdRomanceCallback {
		void onSuccess(String directUrl);
		void onError(String error);
	}
	public void getCdRomanceDirectUrl(String pageUrl, final CdRomanceCallback callback) {

		final WebView webView = findViewById(R.id.mediafireWebView);

		webView.getSettings().setJavaScriptEnabled(true);
		webView.getSettings().setDomStorageEnabled(true);
		webView.getSettings().setUserAgentString("Mozilla/5.0");

		webView.setWebViewClient(new WebViewClient() {

				@Override
				public void onPageFinished(WebView view, String url) {

					// Paso 1: click al botón inicial
					String jsClick =
						"(function(){" +
						"var btn=document.querySelector('.acf-get-content-button');" +
						"if(btn){btn.click();return 'OK';}" +
						"return 'NO_BTN';" +
						"})();";

					webView.evaluateJavascript(jsClick, new ValueCallback<String>() {
							@Override
							public void onReceiveValue(String value) {

								if (value != null && value.contains("OK")) {
									// Paso 2: iniciar polling
									startPolling(webView, callback, 0);
								} else {
									callback.onError("Botón inicial no encontrado.");
								}
							}
						});
				}

				@Override
				public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
					callback.onError(description);
				}
			});

		webView.loadUrl(pageUrl);
	}
	private void startPolling(
		final WebView webView,
		final CdRomanceCallback callback,
		final int attempt
	) {

		if (attempt >= 5) {
			callback.onError("Tiempo de espera agotado.");
			return;
		}

		webView.postDelayed(new Runnable() {
				@Override
				public void run() {

					String jsCheck =
						"(function(){" +
						"var link=document.getElementById('dl-btn-0');" +
						"return link ? link.href : null;" +
						"})();";

					webView.evaluateJavascript(jsCheck, new ValueCallback<String>() {
							@Override
							public void onReceiveValue(String value) {

								if (value != null && !value.equals("null")) {

									String cleanUrl = value
										.replaceAll("^\"|\"$", "")
										.replace("\\u0026", "&");

									callback.onSuccess(cleanUrl);

								} else {
									// Reintento
									startPolling(webView, callback, attempt + 1);
								}
							}
						});

				}
			}, 1000); // 1 segundo entre intentos
	}
	//Fin del proceso de CDRomance
	
	private void cargarRomsDelSistema(final Sistema sistema)
	{
		if (searchBox != null) {
			searchBox.setText("");
		}
		final List<Juego> juegos = sistema.getJuegos();

		recyclerRoms = findViewById(R.id.recyclerRoms);
		recyclerRoms.setLayoutManager(new LinearLayoutManager(this));
		recyclerRoms.setSaveEnabled(false);
		recyclerRoms.setFocusable(true);
		recyclerRoms.setFocusableInTouchMode(true);
		recyclerRoms.setClickable(true);

		// Buscar nombre visible
		String nombreVisible = sistema.getNombre(); // fallback
		for (SystemConfig config : systemConfigs) {
			if (config.system.equalsIgnoreCase(sistema.getNombre())) {
				nombreVisible = config.nombre;
				break;
			}
		}

		// Título
		Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
		String titulo = nombreVisible + " (" + juegos.size() + " juegos disponibles)";
		TextView systemTitle = findViewById(R.id.systemTitle);
		systemTitle.setText(titulo);
		systemTitle.setTypeface(regularFont);

		Toast.makeText(this, "Cargando roms de: " + nombreVisible, Toast.LENGTH_SHORT).show();

		// Fondo cabinet default
		ImageView baseCabinet = findViewById(R.id.baseCabinet);
		baseCabinet.setAlpha(0.3f);
		String claveFondoBase = "fondos/" + sistema.getNombre() + ".webp";

		Bitmap fondoBase = getBitmapFromMemCache(claveFondoBase);
		if (fondoBase != null) {
			baseCabinet.setImageBitmap(fondoBase);
		} else {
			try {
				InputStream is = getAssets().open(claveFondoBase);
				Bitmap bitmap = BitmapFactory.decodeStream(is);
				baseCabinet.setImageBitmap(bitmap);
				addBitmapToMemoryCache(claveFondoBase, bitmap);
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

       // Fondo container
		FrameLayout container = findViewById(R.id.container);
		String rutaFondo = "cabinets/" + sistema.getNombre() + ".webp";

		Bitmap fondoSistema = getBitmapFromMemCache(rutaFondo);
		if (fondoSistema != null) {
			container.setBackground(new BitmapDrawable(getResources(), fondoSistema));
		} else {
			try {
				InputStream is = getAssets().open(rutaFondo);
				Bitmap bitmap = BitmapFactory.decodeStream(is);
				container.setBackground(new BitmapDrawable(getResources(), bitmap));
				addBitmapToMemoryCache(rutaFondo, bitmap);
				is.close();
			} catch (Exception e) {
				String rutaDefault = "cabinets/default_cabinet.webp";
				Bitmap fondoDefault = getBitmapFromMemCache(rutaDefault);
				if (fondoDefault != null) {
					container.setBackground(new BitmapDrawable(getResources(), fondoDefault));
				} else {
					try {
						InputStream isDefault = getAssets().open(rutaDefault);
						Bitmap bitmapDefault = BitmapFactory.decodeStream(isDefault);
						container.setBackground(new BitmapDrawable(getResources(), bitmapDefault));
						addBitmapToMemoryCache(rutaDefault, bitmapDefault);
						isDefault.close();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}
		}

		// Adaptador
		romListAdapter = new RomAdapter(this, juegos);
		romListAdapter.setOnRomListener(new RomAdapter.OnRomListener() {
				
				@Override
				public void onRomClicked(final Juego juego) {
					final String romName = juego.getNombre();
					final String romUrl = juego.getUrl();
					final String selectedSystem = juego.getSistema();

					File zipFile = new File(Environment.getExternalStorageDirectory(),
											"retroplay/temp_download/" + selectedSystem + "/" + romName);

					String baseName = romName.replaceFirst("[.][^.]+$", ""); // quitar extensión
					File extractedFolder = new File(zipFile.getParent(), baseName);

					if (zipFile.exists() || extractedFolder.exists()) {
						try {
							Intent intent = new Intent(MainActivity.this, CoreSelectorActivity.class);
							intent.putExtra("romName", romName);
							intent.putExtra("system", selectedSystem);
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
							startActivity(intent);
						} catch (Exception e) {
							showToast("Error al iniciar emulador, " + selectedSystem + ", " + romName + ", " + baseName);
							e.printStackTrace();
						}
					} else {
						// ❌ ROM no existe, mostrar opciones de descarga
						new AlertDialog.Builder(MainActivity.this)
							.setTitle("¿Cómo deseas descargar el ROM?")
							.setMessage("Puedes descargarlo usando Retroplay o tu navegador.")
							.setPositiveButton("Retroplay", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {

									if (romUrl.contains("mediafire.com")) {

										// MediaFire
										getMediaFireDirectUrl(romUrl, new MediaFireCallback() {
												@Override
												public void onSuccess(String directUrl) {
													startRomDownload(romName, directUrl, selectedSystem);
												}

												@Override
												public void onError(String errorMessage) {
													Toast.makeText(MainActivity.this,
																   "Error MediaFire: " + errorMessage,
																   Toast.LENGTH_SHORT).show();
												}
											});

									} else if (romUrl.contains("cdromance.org")) {

										// CDRomance
										getCdRomanceDirectUrl(romUrl, new CdRomanceCallback() {
												@Override
												public void onSuccess(String directUrl) {
													startRomDownload(romName, directUrl, selectedSystem);
												}

												@Override
												public void onError(String error) {
													Toast.makeText(MainActivity.this,
																   "Error CDRomance: " + error,
																   Toast.LENGTH_SHORT).show();
												}
											});

									} else {

										// URL directa
										startRomDownload(romName, romUrl, selectedSystem);
									}
								}
							})
							.setNegativeButton("Descargar con...", new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int which) {
									Intent shareIntent = new Intent(Intent.ACTION_SEND);
									shareIntent.setType("text/plain");
									shareIntent.putExtra(Intent.EXTRA_TEXT, romUrl);
									startActivity(Intent.createChooser(shareIntent, "Descargar con..."));
								}
							})
							.setNeutralButton("Cancelar", null)
							.show();
					}
				}
				
				private void startRomDownload(String romName, String romUrl, String system) {
					Intent intent = new Intent(MainActivity.this, DownloadService.class);
					intent.putExtra("romName", romName);
					intent.putExtra("romUrl", romUrl);
					intent.putExtra("system", system);
					startService(intent);
					showDownloadDialog(romName);
				}

				@Override
				public void onRomFocused(final Juego juego) {
					int index = juegos.indexOf(juego);
					if (index >= 0) {
						lastFocusedRomIndex = index;
					}
					
					TextView romTitle = findViewById(R.id.romTitle);
					TextView romSize = findViewById(R.id.romSize);
					final TextView borrarRom = findViewById(R.id.borrarRom);
					borrarRom.setVisibility(View.GONE);
					Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");
					Typeface regular = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");

					romTitle.setVisibility(View.VISIBLE);
					romSize.setVisibility(View.VISIBLE);
					romTitle.setText(juego.getNombre());
					romTitle.setTypeface(typeface);
					romSize.setText(juego.getPeso());
					romSize.setTypeface(regular);
					borrarRom.setTypeface(typeface);
					
					//boton borrar
					if (juego.isDescargado()) {
						borrarRom.setVisibility(View.VISIBLE);
					} else {
						borrarRom.setVisibility(View.GONE);
					}

// Acción al hacer click en borrar
					borrarRom.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								new AlertDialog.Builder(MainActivity.this)
									.setTitle("Eliminar ROM")
									.setMessage("¿Deseas eliminar todos los archivos y carpetas que coincidan con este juego?")
									.setPositiveButton("Eliminar", new DialogInterface.OnClickListener() {
										@Override
										public void onClick(DialogInterface dialog, int which) {
											final String baseName = juego.getNombre().replaceFirst("[.][^.]+$", "");
											final File parentDir = new File(Environment.getExternalStorageDirectory(),
																			"retroplay/temp_download/" + juego.getSistema());

											if (parentDir.exists()) {
												File[] files = parentDir.listFiles();
												if (files != null) {
													for (File f : files) {
														if (f.getName().contains(baseName)) {
															if (f.isDirectory()) deleteRecursive(f);
															else f.delete();
														}
													}
												}
											}

											Toast.makeText(getApplicationContext(), "ROM eliminada", Toast.LENGTH_SHORT).show();
											borrarRom.setVisibility(View.GONE);
											actualizarListaDeJuegos();
										}
									})
									.setNegativeButton("Cancelar", null)
									.show();
							}
						});

					// Cargar imágenes
					cargar3DBox(juego.getNombre(), juego.getSistema());
					cargarCover(juego.getNombre(), juego.getSistema());
					cargarVideoPreview(juego.getNombre(), juego.getSistema());
					cargarMarquee(juego.getNombre(), juego.getSistema());
				}
			});
		recyclerRoms.setAdapter(romListAdapter);

		// Focus inicial
		recyclerRoms.post(new Runnable() {
				@Override
				public void run() {
					View firstItem = recyclerRoms.getLayoutManager().findViewByPosition(0);
					if (firstItem != null) {
						firstItem.requestFocus();
					}
				}
			});

		// Soporte botón A del gamepad
		recyclerRoms.setOnKeyListener(new View.OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BUTTON_A) {
						View focused = recyclerRoms.getFocusedChild();
						if (focused != null) {
							focused.performClick();
							return true;
						}
					}
					return false;
				}
			});

		// Filtro en buscador
		searchBox.addTextChangedListener(new android.text.TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					if (romListAdapter != null) {
						romListAdapter.filter(s.toString());
					}
				}
				@Override
				public void afterTextChanged(android.text.Editable s) {}
			});
			
		searchBox.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					romListAdapter.setSearchBoxFoco(hasFocus);
				}
			});

		sistemaActual = sistema;

		// Filtro juegos descargados/todos los juegos
		toggleList = findViewById(R.id.toggleList);
		Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");
		toggleList.setTypeface(typeface);
		mostrarSoloDescargados = false;
		toggleList.setText("Mis juegos");

		toggleList.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mostrarSoloDescargados = !mostrarSoloDescargados;
					toggleList.setText(mostrarSoloDescargados ? "Todos los juegos" : "Mis juegos");
					actualizarListaDeJuegos(); // usa sistemaActual
				}
			});
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
	
	//mensajes al lanzar roms
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
	
	private void showDownloadDialog(final String romName)
	{  
		runOnUiThread(new Runnable() {  
				@Override  
				public void run()
				{  
					// Si ya hay un ProgressDialog para la misma ROM y sigue mostrando, no hacer nada  
					if (downloadProgressDialog != null && downloadProgressDialog.isShowing()  
						&& romName.equals(currentRomName))
					{  
						return;  
					}  

					// Si hay uno anterior distinto, cerrarlo  
					if (downloadProgressDialog != null && downloadProgressDialog.isShowing())
					{  
						downloadProgressDialog.dismiss();  
					}  

					currentRomName = romName;  
					downloadProgressDialog = new ProgressDialog(MainActivity.this);  
					downloadProgressDialog.setTitle("Descargando " + romName);  
					downloadProgressDialog.setMessage("Preparando...");  
					downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);  
					downloadProgressDialog.setMax(100);  
					downloadProgressDialog.setProgress(lastProgress);  

					downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar",  
						new DialogInterface.OnClickListener() {  
							@Override  
							public void onClick(DialogInterface dialog, int which)
							{  
								cancelDownload(romName);  
								downloadProgressDialog.dismiss();  
							}  
						});  

					downloadProgressDialog.setCancelable(false);  
					downloadProgressDialog.show();  
				}  
			});  
	}  

	private void cancelDownload(String romName)
	{  
		// Crear Intent para cancelar la descarga en el servicio  
		Intent cancelIntent = new Intent(this, DownloadService.class);  
		cancelIntent.setAction("CANCEL_DOWNLOAD");  
		cancelIntent.putExtra("romName", romName);  // Enviar el nombre de la ROM  
		startService(cancelIntent);  // Iniciar el servicio de cancelación  
	}  

	private void updateDownloadProgress(final int progress)
	{  
		lastProgress = progress; // Guarda el último progreso conocido  

		runOnUiThread(new Runnable() {  
				@Override  
				public void run()
				{  
					if (downloadProgressDialog != null && downloadProgressDialog.isShowing())
					{  
						downloadProgressDialog.setProgress(progress);  
						downloadProgressDialog.setMessage("Descargando... " + progress + "%");  
					}  
				}  
			});  
	}

	//inicio Marquee
	private void cargarMarquee(final String romName, final String selectedSystem) {
		final ImageView dynamicMarquee = findViewById(R.id.dynamicMarquee);
		final String romNameSE = romName.contains(".") ? romName.replaceAll("\\.[^.]+$", "") : romName;
		final String systemAlias = mapearSistema(selectedSystem);
		final String cacheKey = "marquee_" + systemAlias + "_" + romNameSE;

		// 1. Verificar en memoria
		Bitmap cached = getBitmapFromMemCache(cacheKey);
		if (cached != null) {
			dynamicMarquee.setVisibility(View.VISIBLE);
			dynamicMarquee.setImageBitmap(cached);
			return;
		}

		// 2. Verificar en almacenamiento local
		File localFile = new File(Environment.getExternalStorageDirectory(),
								  "retroplay/media/" + systemAlias + "/marquees/" + romNameSE + ".webp");

		if (localFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(localFile);
				Bitmap bmp = BitmapFactory.decodeStream(fis);
				fis.close();

				if (bmp != null) {
					addBitmapToMemoryCache(cacheKey, bmp);
					dynamicMarquee.setVisibility(View.VISIBLE);
					dynamicMarquee.setImageBitmap(bmp);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 3. Descargar desde red si no hay local
		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr) {
				this.urlStr = urlStr;
			}

			@Override
			public void run() {
				try {
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(3000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true)) {
						InputStream input = connection.getInputStream();
						final Bitmap bmp = BitmapFactory.decodeStream(input);
						input.close();

						if (bmp != null) {
							addBitmapToMemoryCache(cacheKey, bmp);

							runOnUiThread(new Runnable() {
									@Override
									public void run() {
										dynamicMarquee.setVisibility(View.VISIBLE);
										dynamicMarquee.setImageBitmap(bmp);
									}
								});
						}
						return;
					}
				} catch (Exception e) {
					// ignorado
				}

				if (loaded.compareAndSet(false, true)) {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								dynamicMarquee.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/logos/" + romNameSE + ".png";
		String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/marquees/" + romNameSE + ".webp";

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin Marquee

	//inicio 3dbox
	private void cargar3DBox(final String romName, final String selectedSystem) {
		final ImageView dynamic3dbox = findViewById(R.id.dynamic3dbox);
		final String romNameSE = romName.contains(".") ? romName.replaceAll("\\.[^.]+$", "") : romName;
		final String systemAlias = mapearSistema(selectedSystem);
		final String cacheKey = "3dbox_" + systemAlias + "_" + romNameSE;

		// 1. Revisar memoria
		Bitmap cached = getBitmapFromMemCache(cacheKey);
		if (cached != null) {
			dynamic3dbox.setVisibility(View.VISIBLE);
			dynamic3dbox.setImageBitmap(cached);
			return;
		}

		// 2. Revisar local
		File localFile = new File(Environment.getExternalStorageDirectory(),
								  "retroplay/media/" + systemAlias + "/3dbox/" + romNameSE + ".webp");

		if (localFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(localFile);
				Bitmap bmp = BitmapFactory.decodeStream(fis);
				fis.close();

				if (bmp != null) {
					addBitmapToMemoryCache(cacheKey, bmp);
					dynamic3dbox.setVisibility(View.VISIBLE);
					dynamic3dbox.setImageBitmap(bmp);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 3. Si no existe, intenta descargar
		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr) {
				this.urlStr = urlStr;
			}

			@Override
			public void run() {
				try {
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(5000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true)) {
						InputStream input = connection.getInputStream();
						final Bitmap bmp = BitmapFactory.decodeStream(input);
						input.close();

						if (bmp != null) {
							addBitmapToMemoryCache(cacheKey, bmp);

							runOnUiThread(new Runnable() {
									@Override
									public void run() {
										dynamic3dbox.setVisibility(View.VISIBLE);
										dynamic3dbox.setImageBitmap(bmp);
									}
								});
						}
						return;
					}
				} catch (Exception e) {
					// Ignorado
				}

				// Si falla todo
				if (loaded.compareAndSet(false, true)) {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								dynamic3dbox.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/corners/" + romNameSE + ".png";
		String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/3dbox/" + romNameSE + ".webp";

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin 3dbox

	//Cover
	private void cargarCover(final String romName, final String selectedSystem) {
		final ImageView dynamicCover = findViewById(R.id.dynamicCover);
		final String romNameSE = romName.contains(".") ? romName.replaceAll("\\.[^.]+$", "") : romName;
		final String systemAlias = mapearSistema(selectedSystem);
		final String cacheKey = "cover_" + systemAlias + "_" + romNameSE;

		// 1. Revisar en memoria
		Bitmap cached = getBitmapFromMemCache(cacheKey);
		if (cached != null) {
			dynamicCover.setVisibility(View.VISIBLE);
			dynamicCover.setImageBitmap(cached);
			return;
		}

		// 2. Revisar en almacenamiento local
		File localFile = new File(Environment.getExternalStorageDirectory(),
								  "retroplay/media/" + systemAlias + "/covers/" + romNameSE + ".webp");

		if (localFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(localFile);
				Bitmap bmp = BitmapFactory.decodeStream(fis);
				fis.close();

				if (bmp != null) {
					addBitmapToMemoryCache(cacheKey, bmp);
					dynamicCover.setVisibility(View.VISIBLE);
					dynamicCover.setImageBitmap(bmp);
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// 3. Si no existe, intentar descargar
		final AtomicBoolean loaded = new AtomicBoolean(false);

		class ImageLoader extends Thread {
			private String urlStr;

			ImageLoader(String urlStr) {
				this.urlStr = urlStr;
			}

			@Override
			public void run() {
				try {
					URL url = new URL(urlStr);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(5000);
					connection.connect();

					if (connection.getResponseCode() == HttpURLConnection.HTTP_OK && loaded.compareAndSet(false, true)) {
						InputStream input = connection.getInputStream();
						final Bitmap bmp = BitmapFactory.decodeStream(input);
						input.close();

						if (bmp != null) {
							addBitmapToMemoryCache(cacheKey, bmp);

							runOnUiThread(new Runnable() {
									@Override
									public void run() {
										dynamicCover.setVisibility(View.VISIBLE);
										dynamicCover.setImageBitmap(bmp);
									}
								});
						}
						return;
					}
				} catch (Exception e) {
					// ignorado
				}

				if (loaded.compareAndSet(false, true)) {
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								dynamicCover.setVisibility(View.GONE);
							}
						});
				}
			}
		}

		String imageUrl1 = "https://gam.onl/user/" + systemAlias + "/covers/" + romNameSE + ".png";
		String imageUrl2 = "https://raw.githubusercontent.com/Cezar1405/retroplay/refs/heads/main/media/" + systemAlias + "/covers/" + romNameSE + ".webp";

		new ImageLoader(imageUrl1).start();
		new ImageLoader(imageUrl2).start();
	}
	//fin Cover

	//Video preview
	private void cargarVideoPreview(final String romName, final String selectedSystem)
	{
		final VideoView videoPreview = findViewById(R.id.videoPreview);
		final ImageView dynamicScreenshot = findViewById(R.id.dynamicScreenshot);
		
		videoPreview.setVisibility(View.GONE);
		dynamicScreenshot.setVisibility(View.GONE);

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
			connection.setConnectTimeout(5000);
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
							//dynamicScreenshot.setVisibility(View.GONE);
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
						screenshot.setVisibility(View.VISIBLE);
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
	private void actualizarListaDeJuegos() {
		if (romListAdapter == null || sistemaActual == null) return;

		// Creamos un nuevo hilo para no bloquear la UI
		Thread hilo = new Thread(new Runnable() {
				@Override
				public void run() {
					List<Juego> todos = sistemaActual.getJuegos();
					final List<Juego> filtrados = new ArrayList<Juego>();

					File carpeta = new File(Environment.getExternalStorageDirectory(),
											"/retroplay/temp_download/" + sistemaActual.getNombre());

					// Crear un Set con los nombres de los archivos descargados (sin extensión)
					Set<String> archivosDescargados = new HashSet<String>();
					if (carpeta.exists() && carpeta.isDirectory()) {
						File[] archivos = carpeta.listFiles();
						if (archivos != null) {
							for (int i = 0; i < archivos.length; i++) {
								String nombreSinExt = archivos[i].getName().replaceAll("\\.[^.]+$", "");
								archivosDescargados.add(nombreSinExt);
							}
						}
					}

					// Filtrar los juegos según mostrarSoloDescargados
					for (int j = 0; j < todos.size(); j++) {
						Juego juego = todos.get(j);
						String baseName = juego.getNombre().replaceAll("\\.[^.]+$", "");
						boolean descargado = archivosDescargados.contains(baseName);

						// 🔴 AQUÍ FALTA ESTO
						juego.setDescargado(descargado);

						if (!mostrarSoloDescargados || descargado) {
							filtrados.add(juego);
						}
					}

					// Actualizar el adapter en el hilo principal
					runOnUiThread(new Runnable() {
							@Override
							public void run() {
								romListAdapter.actualizarLista(filtrados);
							}
						});
				}
			});

		hilo.start();
	}
	//fin descargados

	//espacio en el sistema
	public String obtenerEspacioAlmacenamiento()
	{
		File path = Environment.getExternalStorageDirectory();
		StatFs stat = new StatFs(path.getPath());

		long totalBytes, availableBytes;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
		{
			totalBytes = stat.getTotalBytes();
			availableBytes = stat.getAvailableBytes();
		}
		else
		{
			long blockSize = stat.getBlockSize();
			totalBytes = blockSize * stat.getBlockCount();
			availableBytes = blockSize * stat.getAvailableBlocks();
		}

		String totalStr = formatSize(totalBytes);
		String availableStr = formatSize(availableBytes);

		return availableStr + " / " + totalStr;
	}

	private String formatSize(long size)
	{
		float kb = size / 1024f;
		float mb = kb / 1024f;
		float gb = mb / 1024f;

		if (gb >= 1)
		{
			return String.format(Locale.getDefault(), "%.2f GB", gb);
		}
		else if (mb >= 1)
		{
			return String.format(Locale.getDefault(), "%.2f MB", mb);
		}
		else
		{
			return String.format(Locale.getDefault(), "%.2f KB", kb);
		}
	}
	//fin
	
	//Actualizar espacio en tiempo real
	private void actualizarEspacio()
	{
		TextView espacioTextView = findViewById(R.id.espacioDisp);
		espacioTextView.setText(obtenerEspacioAlmacenamiento());
	}
	//fin
	
	//metodo para mostrar u ocultar sistemas
	private void mostrarDialogoSistemas() {
		LayoutInflater inflater = LayoutInflater.from(this);
		View dialogView = inflater.inflate(R.layout.dialog_sistemas, null);
		final ListView lista = (ListView) dialogView.findViewById(R.id.listaSistemas);

		// Carpetas
		final File carpetaVisible = new File(basePath);
		final File carpetaOculta = new File(baseOcultas);
		if (!carpetaOculta.exists()) carpetaOculta.mkdirs();

		final ArrayList<SistemaItem> listaSistemas = new ArrayList<SistemaItem>();

		// Archivos visibles
		File[] visibles = carpetaVisible.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".txt");
				}
			});
		if (visibles != null) {

			// Orden alfabético SIN lambdas
			Arrays.sort(visibles, new Comparator<File>() {
					@Override
					public int compare(File a, File b) {
						return a.getName().compareToIgnoreCase(b.getName());
					}
				});

			for (File f : visibles) {
				String id = f.getName().replace(".txt", "");
				String nombreMostrado = obtenerNombreSistemaBonito(id);
				listaSistemas.add(new SistemaItem(id, nombreMostrado, false));
			}
		}

		// Archivos ocultos
		File[] ocultos = carpetaOculta.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					return name.endsWith(".txt");
				}
			});
		if (ocultos != null) {

			// Orden alfabético SIN lambdas
			Arrays.sort(ocultos, new Comparator<File>() {
					@Override
					public int compare(File a, File b) {
						return a.getName().compareToIgnoreCase(b.getName());
					}
				});

			for (File f : ocultos) {
				String id = f.getName().replace(".txt", "");
				String nombreMostrado = obtenerNombreSistemaBonito(id);
				listaSistemas.add(new SistemaItem(id, nombreMostrado, true));
			}
		}

		final SistemaDialogAdapter adapter = new SistemaDialogAdapter(this, listaSistemas);
		lista.setAdapter(adapter);
		lista.setItemsCanFocus(true);
		lista.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(dialogView);

// Se mantiene
		lista.setItemsCanFocus(true);
		lista.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

// Adapter
		lista.setAdapter(adapter);

// --- SINCRONIZACIÓN IMPORTANTE ---
		for (int i = 0; i < listaSistemas.size(); i++) {
			SistemaItem s = listaSistemas.get(i);
			lista.setItemChecked(i, s.oculto);
		}

// --- MANEJO DE CLICK SIN LAMBDAS ---
		lista.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
					SistemaItem s = listaSistemas.get(position);
					s.oculto = !s.oculto;                       // actualiza modelo
					lista.setItemChecked(position, s.oculto);   // actualiza UI
				}
			});

// Botón Guardar sin lambdas
		builder.setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					for (SistemaItem sistema : listaSistemas) {
						moverSistemaArchivo(sistema);
					}
					recreate();
				}
			});

// Botón Cancelar
		builder.setNegativeButton("Cancelar", null);

		builder.show();
	}

// Método para mover archivos (copia + elimina)
	private boolean moverArchivo(File origen, File destino) {
		if (!origen.exists()) return false;

		try {
			// Crear carpeta destino si no existe
			File parent = destino.getParentFile();
			if (!parent.exists()) parent.mkdirs();

			// Copiar archivo
			FileInputStream in = new FileInputStream(origen);
			FileOutputStream out = new FileOutputStream(destino);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
			}
			in.close();
			out.close();

			// Intentar eliminar
			boolean borrado = origen.delete();
			if (!borrado) {
				Log.e("MOVE", "No se pudo borrar: " + origen.getAbsolutePath());
				origen.deleteOnExit(); // último recurso
			}

			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

// Método para mover sistema según si está marcado
	private void moverSistemaArchivo(SistemaItem sistema) {
		File archivoListas = new File(basePath + sistema.id + ".txt");
		File archivoOcultas = new File(baseOcultas + sistema.id + ".txt");

		if (sistema.oculto) {
			// Mover a la carpeta de ocultos (listasB)
			moverArchivo(archivoListas, archivoOcultas);
		} else {
			// Restaurar de ocultos a listas
			moverArchivo(archivoOcultas, archivoListas);
		}
	}

//obteniendo nombres de los sistenas
	private String obtenerNombreSistemaBonito(String idSistema) {
		for (SystemConfig sc : systemConfigs) {
			if (sc.system.equalsIgnoreCase(idSistema)) {
				return sc.nombre; // retorna el nombre descriptivo
			}
		}
		return idSistema; // fallback si no se encuentra en JSON
	}

// Clase auxiliar
	static class SistemaItem {
		String id;     // nombre real del archivo
		String nombre; // nombre bonito mostrado
		boolean oculto;

		SistemaItem(String id, String nombre, boolean oculto) {
			this.id = id;
			this.nombre = nombre;
			this.oculto = oculto;
		}
	}

// Adaptador sin lambdas
	class SistemaDialogAdapter extends ArrayAdapter<SistemaItem> {

		public SistemaDialogAdapter(Context ctx, ArrayList<SistemaItem> sistemas) {
			super(ctx, 0, sistemas);
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext())
					.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
			}

			SistemaItem s = getItem(position);
			CheckedTextView check = (CheckedTextView) convertView;

			check.setText(s.nombre.toUpperCase());
			check.setTextSize(14);

			// ❗ Importante: NO USAR setChecked aquí
			// El ListView se encarga de eso con setItemChecked()

			// ❗ También quitar cualquier listener
			// check.setOnClickListener(...) → ELIMINADO

			return convertView;
		}
	}
	//

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

// BroadcastReceiver para manejar las descargas  
	private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {  
		@Override  
		public void onReceive(Context context, Intent intent)  
		{  
			String romName = intent.getStringExtra("romName");  
			String action = intent.getAction();  

			switch (action)  
			{    
				case "DOWNLOAD_START":    
					// Evitar mostrar dos veces el mismo dialog    
					if (!romName.equals(currentRomName) || downloadProgressDialog == null || !downloadProgressDialog.isShowing())  
					{    
						currentRomName = romName;    
						lastProgress = 0;    
						isDownloading = true;    
						showDownloadDialog(romName);    
					}    
					break;    

				case "DOWNLOAD_PROGRESS":    
					int progress = intent.getIntExtra("progress", 0);    
					String mbText = intent.getStringExtra("mbText"); // 🔹 aquí obtenemos MB descargados

					lastProgress = progress;    
					updateDownloadProgress(progress); // mantiene la barra de porcentaje

					// Actualiza el ProgressDialog con el texto MB
					if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
						downloadProgressDialog.setMessage(mbText);
					}
					break;

				case "DOWNLOAD_COMPLETE":    
					isDownloading = false;    
					Toast.makeText(MainActivity.this, romName + " listo", Toast.LENGTH_SHORT).show();    
					if (downloadProgressDialog != null) downloadProgressDialog.dismiss();    
					break;    

				case "DOWNLOAD_ERROR":    
					isDownloading = false;    
					if (downloadProgressDialog != null) downloadProgressDialog.dismiss();    
					Toast.makeText(MainActivity.this, "Error con " + romName, Toast.LENGTH_SHORT).show();    
					break;    

				case "DOWNLOAD_CANCELLED":    
					isDownloading = false;    
					if (downloadProgressDialog != null) downloadProgressDialog.dismiss();    
					Toast.makeText(MainActivity.this, "Descarga cancelada: " + romName, Toast.LENGTH_SHORT).show();    
					break;    
			}    
		}    
	};  

	@Override  
	protected void onResume()  
	{  
		super.onResume();  

		IntentFilter filter = new IntentFilter();  
		filter.addAction("DOWNLOAD_START");  
		filter.addAction("DOWNLOAD_PROGRESS");  
		filter.addAction("DOWNLOAD_COMPLETE");  
		filter.addAction("DOWNLOAD_ERROR");  
		filter.addAction("DOWNLOAD_CANCELLED");  
		registerReceiver(downloadReceiver, filter);  

		// Si la descarga sigue (podés usar una bandera como isDownloading)  
		if (isDownloading && downloadProgressDialog == null)  
		{  
			showDownloadDialog(currentRomName); // Tu rom actual  
			updateDownloadProgress(lastProgress);  
		}  

		actualizarEspacio();
		actualizarListaDeJuegos();

		// evitar offset en roms
		if (recyclerRoms != null && romListAdapter != null && romListAdapter.getItemCount() > 0)  
		{  
			recyclerRoms.post(new Runnable() {  
					@Override  
					public void run() {  
						// Forzar scroll al primer ítem (puedes cambiar a la posición deseada)  
						recyclerRoms.scrollToPosition(lastFocusedRomIndex);  

						// Esperar un poco más para pedir el focus  
						recyclerRoms.postDelayed(new Runnable() {  
								@Override  
								public void run() {  
									View item = recyclerRoms.getLayoutManager().findViewByPosition(lastFocusedRomIndex);  
									if (item != null) {  
										item.requestFocus();  
									}  
								}  
							}, 100);  
					}  
				});  
		}  
		//evitar offset en sistemas
		if (systemSlider != null && systemAdapter != null && systemAdapter.getItemCount() > 0) {
			
			int posFoco = -1;
			for (int i = 0; i < sistemas.size(); i++) {
				if (sistemas.get(i).isFocado()) {
					posFoco = i;
					break;
				}
			}
			final int focoParaRunnable = posFoco; // sí necesitas final aquí para el Runnable
			
			systemSlider.post(new Runnable() {
					@Override
					public void run() {
						if (focoParaRunnable != -1) {
							RecyclerView.ViewHolder vh = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
							if (vh != null) {
								vh.itemView.requestFocus();
							} else {
								systemSlider.scrollToPosition(focoParaRunnable);
								systemSlider.post(new Runnable() {
										@Override
										public void run() {
											RecyclerView.ViewHolder vh2 = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
											if (vh2 != null) vh2.itemView.requestFocus();
										}
									});
							}
						} else {
							View firstItem = systemSlider.getLayoutManager().findViewByPosition(0);
							if (firstItem != null) firstItem.requestFocus();
						}
					}
				});
		}
	}  

	@Override  
	protected void onPause() {  
		super.onPause();  
		// 🔧 Solución para evitar offset o focus perdido tras volver  
		if (recyclerRoms != null && romListAdapter != null && romListAdapter.getItemCount() > 0)  
		{  
			recyclerRoms.post(new Runnable() {  
					@Override  
					public void run() {  
						// Forzar scroll al primer ítem (puedes cambiar a la posición deseada)  
						recyclerRoms.scrollToPosition(lastFocusedRomIndex);  

						// Esperar un poco más para pedir el focus  
						recyclerRoms.postDelayed(new Runnable() {  
								@Override  
								public void run() {  
									View item = recyclerRoms.getLayoutManager().findViewByPosition(lastFocusedRomIndex);  
									if (item != null) {  
										item.requestFocus();  
									}  
								}  
							}, 100);  
					}  
				});  
		}  
		
		//evitar offset en sistemas
		if (systemSlider != null && systemAdapter != null && systemAdapter.getItemCount() > 0) {

			int posFoco = -1;
			for (int i = 0; i < sistemas.size(); i++) {
				if (sistemas.get(i).isFocado()) {
					posFoco = i;
					break;
				}
			}
			final int focoParaRunnable = posFoco; // sí necesitas final aquí para el Runnable

			systemSlider.post(new Runnable() {
					@Override
					public void run() {
						if (focoParaRunnable != -1) {
							RecyclerView.ViewHolder vh = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
							if (vh != null) {
								vh.itemView.requestFocus();
							} else {
								systemSlider.scrollToPosition(focoParaRunnable);
								systemSlider.post(new Runnable() {
										@Override
										public void run() {
											RecyclerView.ViewHolder vh2 = systemSlider.findViewHolderForAdapterPosition(focoParaRunnable);
											if (vh2 != null) vh2.itemView.requestFocus();
										}
									});
							}
						} else {
							View firstItem = systemSlider.getLayoutManager().findViewByPosition(0);
							if (firstItem != null) firstItem.requestFocus();
						}
					}
				});
		}
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		memoryCache.evictAll();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if (progressDialog != null && progressDialog.isShowing())
		{
			progressDialog.dismiss();
		}

		if (downloadProgressDialog != null && downloadProgressDialog.isShowing())
		{
			downloadProgressDialog.dismiss();
		}
	}
}
