package com.cezar.retroplay;

import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class RomListAdapter extends BaseAdapter {
    private Context context;
    private List<Juego> originalJuegos;  // lista completa
    private List<Juego> juegos;           // lista filtrada
    private OnRomClickListener listener;

    public interface OnRomClickListener {
        void onRomClicked(Juego juego);
        void onRomFocused(Juego juego);
    }

    public RomListAdapter(Context context, List<Juego> juegos, OnRomClickListener listener) {
        this.context = context;
        this.originalJuegos = new ArrayList<>(juegos);
        this.juegos = new ArrayList<>(juegos);
        this.listener = listener;
    }

    public void filter(String query) {
        query = query.toLowerCase().trim();
        juegos.clear();
        if (query.isEmpty()) {
            juegos.addAll(originalJuegos);
        } else {
            for (Juego juego : originalJuegos) {
                if (juego.getNombre().toLowerCase().contains(query)) {
                    juegos.add(juego);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return juegos.size();
    }

    @Override
    public Object getItem(int position) {
        return juegos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_rom, parent, false);
        }

        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/Exo2-BoldCondensed.otf");

        final Juego juego = juegos.get(position);
        TextView romName = convertView.findViewById(R.id.romName);

        String nombreOriginal = juego.getNombre();
        String nombreSinExtension = nombreOriginal.replaceAll("\\.[^.]+$", "");
        if (!nombreSinExtension.isEmpty()) {
            nombreSinExtension = nombreSinExtension.substring(0, 1).toUpperCase() + nombreSinExtension.substring(1);
        }

        romName.setText(nombreSinExtension);
        romName.setTypeface(typeface);

        convertView.setFocusable(true);
        convertView.setFocusableInTouchMode(true);

        convertView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (hasFocus && listener != null) {
						listener.onRomFocused(juego);
					}
				}
			});

        convertView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (listener != null) {
						listener.onRomClicked(juego);
					}
				}
			});

        return convertView;
    }
	
	public void actualizarLista(List<Juego> nuevaLista) {
		this.juegos = nuevaLista;
		notifyDataSetChanged();
	}
}
