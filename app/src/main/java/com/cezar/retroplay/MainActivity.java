package com.cezar.retroplay;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.support.v7.widget.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {

	private RecyclerView systemSlider;
	private List<Sistema> sistemas = new ArrayList<>();
	private SystemAdapter adapter;
	private List<SystemConfig> systemConfigs;
	private EditText searchBox;
	private boolean mostrarSoloDescargados = false;
	private TextView toggleList;
	private Sistema sistemaActual;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
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

		if (Build.VERSION.SDK_INT >= 23) {
			if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1001);
			} else {
				cargarSistemas();
			}
		} else {
			cargarSistemas();
		}
	    mostrarEspacioDisponible();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			cargarSistemas();
		} else {
			Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
		}
	}

// Clase auxiliar para la configuración del sistema
	public static class SystemConfig {
		public String system;
		public String nombre;
		public String descripcion;
		public String coreDef;
		public String[] coreOpciones;

		public SystemConfig(JSONObject obj) throws Exception {
			system = obj.getString("system");
			nombre = obj.getString("nombre");
			descripcion = obj.getString("descripcion");
			coreDef = obj.getString("coreDef");

			JSONArray cores = obj.getJSONArray("coreOpciones");
			coreOpciones = new String[cores.length()];
			for (int i = 0; i < cores.length(); i++) {
				coreOpciones[i] = cores.getString(i);
			}
		}
	}
	
	private List<SystemConfig> cargarConfiguracionSistema() {
		List<SystemConfig> configs = new ArrayList<>();
		try {
			File file = new File("/storage/emulated/0/retroplay/conf/system_config.json");
			FileInputStream fis = new FileInputStream(file);
			byte[] buffer = new byte[(int) file.length()];
			fis.read(buffer);
			fis.close();
			String jsonStr = new String(buffer, "UTF-8");
			JSONArray arr = new JSONArray(jsonStr);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject obj = arr.getJSONObject(i);
				configs.add(new SystemConfig(obj));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return configs;
	}

	private void cargarSistemas() {
		File carpeta = new File(Environment.getExternalStorageDirectory(), "retroplay/listas/");
		if (carpeta.exists() && carpeta.isDirectory()) {
			File[] archivos = carpeta.listFiles();
			if (archivos != null) {
				for (int i = 0; i < archivos.length; i++) {
					File archivo = archivos[i];
					if (archivo.getName().endsWith(".txt")) {
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
				public void onSystemFocused(Sistema sistema) {
					// Cambiar fondo (como antes)
					try {
						String ruta = "fondos/" + sistema.getNombre() + ".webp";
						InputStream is = getAssets().open(ruta);
						Bitmap fondo = BitmapFactory.decodeStream(is);
						ImageView fondoView = findViewById(R.id.dynamicBackground);
						fondoView.setImageBitmap(fondo);
						is.close();
					} catch (Exception e) {
						ImageView fondoView = findViewById(R.id.dynamicBackground);
						fondoView.setImageResource(R.drawable.default_back);
					}

					// Mostrar datos de configuración
					SystemConfig config = null;
					for (SystemConfig sc : systemConfigs) {
						if (sc.system.equalsIgnoreCase(sistema.getNombre())) {
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

					if (config != null) {
						tSystem.setText(config.nombre);
						descSystem.setText(config.descripcion);
						coreSystem.setText(config.coreDef);

						tSystem.setVisibility(View.VISIBLE);
						descSystem.setVisibility(View.VISIBLE);
						coreSystem.setVisibility(View.VISIBLE);
					} else {
						tSystem.setVisibility(View.GONE);
						descSystem.setVisibility(View.GONE);
						coreSystem.setVisibility(View.GONE);
					}
				}
			});
			
		adapter.setOnSystemClickListener(new SystemAdapter.OnSystemClickListener() {
				@Override
				public void onSystemClicked(Sistema sistema) {
					// Oculta el layout principal y muestra el contenedor de roms
					findViewById(R.id.uiApp).setVisibility(View.GONE);
					findViewById(R.id.container).setVisibility(View.VISIBLE);

					cargarRomsDelSistema(sistema);
				}
			});

		systemSlider.setAdapter(adapter);

		systemSlider.post(new Runnable() {
				@Override
				public void run() {
					View firstItem = systemSlider.getLayoutManager().findViewByPosition(0);
					if (firstItem != null) {
						firstItem.requestFocus();
					}
				}
			});
	}
	
	private RomListAdapter romListAdapter; // atributo de clase para usarlo en el filtro

	private void cargarRomsDelSistema(Sistema sistema) {
		if (searchBox != null) {
			searchBox.setText("");
		}
		List<Juego> juegos = sistema.getJuegos();
		ListView listViewRoms = findViewById(R.id.listViewRoms);

		// Buscar nombre descriptivo desde systemConfigs
		String nombreVisible = sistema.getNombre(); // fallback
		for (SystemConfig config : systemConfigs) {
			if (config.system.equalsIgnoreCase(sistema.getNombre())) {
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

		// Cambiar fondo del container según el sistema
		FrameLayout container = findViewById(R.id.container);
		try {
			String rutaFondo = "cabinets/" + sistema.getNombre() + ".webp";
			InputStream is = getAssets().open(rutaFondo);
			Bitmap bitmap = BitmapFactory.decodeStream(is);
			container.setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
			is.close();
		} catch (Exception e) {
			container.setBackgroundResource(R.drawable.default_cabinet);
		}

		// Crear adaptador y asignar
		romListAdapter = new RomListAdapter(this, juegos, new RomListAdapter.OnRomClickListener() {
				@Override
				public void onRomClicked(Juego juego) {
					Toast.makeText(MainActivity.this, "Click en: " + juego.getNombre(), Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onRomFocused(Juego juego) {
					mostrarDatosDeJuego(juego);
				}
			});

		listViewRoms.setAdapter(romListAdapter);
		listViewRoms.requestFocus();
		
		//final EditText searchBox = findViewById(R.id.searchBox);
		searchBox.addTextChangedListener(new android.text.TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					if (romListAdapter != null) {
						romListAdapter.filter(s.toString());
					}
				}
				@Override
				public void afterTextChanged(android.text.Editable s) { }
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
				public void onClick(View v) {
					mostrarSoloDescargados = !mostrarSoloDescargados;
					toggleList.setText(mostrarSoloDescargados ? "Todos los juegos" : "Mis juegos");
					actualizarListaDeJuegos(); // usa sistemaActual
				}
			});
	}
	
	private void mostrarDatosDeJuego(Juego juego) {
		
		Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/Exo2-BoldCondensed.otf");      
		Typeface regularFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-RegularCondensed.otf");
		//Typeface semiboldFont = Typeface.createFromAsset(getAssets(), "fonts/Exo2-SemiBoldCondensed.otf");
		//Typeface texgyreFont = Typeface.createFromAsset(getAssets(), "fonts/texgyre.otf");
		
		TextView romTitle = findViewById(R.id.romTitle);
		TextView romSize = findViewById(R.id.romSize);
		
		romTitle.setText(juego.getNombre());
		romTitle.setTypeface(typeface);
		romSize.setText(juego.getPeso());
		romSize.setTypeface(regularFont);

		// Puedes cargar imágenes si están en assets o archivos
		// ImageView dynamicCover = findViewById(R.id.dynamicCover);
		// ImageView dynamicScreenshot = findViewById(R.id.dynamicScreenshot);
		// VideoView videoPreview = findViewById(R.id.videoPreview);
	}
	
	//filtro descargados
	private void actualizarListaDeJuegos() {
		if (romListAdapter == null || sistemaActual == null) return;

		List<Juego> todos = sistemaActual.getJuegos();
		List<Juego> filtrados = new ArrayList<>();

		File carpeta = new File(Environment.getExternalStorageDirectory(), "/retroplay/temp_download/" + sistemaActual.getNombre());

		for (Juego juego : todos) {
			String nombreRom = juego.getNombre();
			String baseName = nombreRom.replaceAll("\\.[^.]+$", ""); // Quitar extensión

			boolean descargado = false;

			if (carpeta.exists() && carpeta.isDirectory()) {
				File[] archivos = carpeta.listFiles();
				if (archivos != null) {
					for (File archivo : archivos) {
						if (archivo.getName().startsWith(baseName)) {
							descargado = true;
							break;
						}
					}
				}
			}

			if (!mostrarSoloDescargados || descargado) {
				filtrados.add(juego);
			}
		}

		romListAdapter.actualizarLista(filtrados);
	}
	//fin descargados
	
	//calcular espacio
	private void mostrarEspacioDisponible() {
		TextView espacioDisp = findViewById(R.id.espacioDisp);

		File path = Environment.getDataDirectory(); // almacenamiento interno: /data

		StatFs stat = new StatFs(path.getPath());

		long blockSize, totalBlocks, availableBlocks;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			blockSize = stat.getBlockSizeLong();
			totalBlocks = stat.getBlockCountLong();
			availableBlocks = stat.getAvailableBlocksLong();
		} else {
			blockSize = stat.getBlockSize();
			totalBlocks = stat.getBlockCount();
			availableBlocks = stat.getAvailableBlocks();
		}

		long totalBytes = totalBlocks * blockSize;
		long availableBytes = availableBlocks * blockSize;

		String texto = "Espacio disponible: " + formatSize(availableBytes) + " / " + formatSize(totalBytes);
		espacioDisp.setText(texto);
	}
	
	private String formatSize(long size) {
		float kb = size / 1024f;
		float mb = kb / 1024f;
		float gb = mb / 1024f;

		if (gb >= 1) {
			return String.format(Locale.US, "%.2f GB", gb);
		} else if (mb >= 1) {
			return String.format(Locale.US, "%.2f MB", mb);
		} else {
			return String.format(Locale.US, "%.2f KB", kb);
		}
	}
	//fin de calculo espacio

	private List<Juego> leerJuegosDesdeArchivo(File archivo) {
		List<Juego> lista = new ArrayList<>();

		try {
			BufferedReader br = new BufferedReader(new FileReader(archivo));
			String linea;
			while ((linea = br.readLine()) != null) {
				String[] partes = linea.split("=");
				if (partes.length >= 3) {
					String nombre = partes[0];
					String url = partes[1];
					String sistema = partes[2];
					String peso = (partes.length >= 4) ? partes[3] : "";
					lista.add(new Juego(nombre, url, sistema, peso));
				}
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return lista;
	}
	
	@Override
	public void onBackPressed() {
		View container = findViewById(R.id.container);
		View uiApp = findViewById(R.id.uiApp);

		if (container.getVisibility() == View.VISIBLE) {
			EditText searchBox = findViewById(R.id.searchBox);
			if (searchBox != null) {
				searchBox.setText("");
			}
			// Volver a la pantalla principal
			container.setVisibility(View.GONE);
			uiApp.setVisibility(View.VISIBLE);
		} else {
			// Confirmar salida
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Salir de RetroPlay");
			builder.setMessage("¿Estás seguro de que deseas salir?");
			builder.setPositiveButton("Sí, salir", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish(); // Cierra la app
					}
				});
			builder.setNegativeButton("No", null);
			builder.show();
		}
	}
}
