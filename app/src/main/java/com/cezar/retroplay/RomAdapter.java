package com.cezar.retroplay;

import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.TextView;

import java.util.*;

public class RomAdapter extends RecyclerView.Adapter<RomAdapter.ViewHolder> {

	private List<Juego> juegos;
	private List<Juego> juegosOriginales;
	private Context context;
	private OnRomListener listener;
	private int posFocused = RecyclerView.NO_POSITION;
	private boolean searchBoxTieneFoco = false;

	public void setSearchBoxFoco(boolean tieneFoco) {
		this.searchBoxTieneFoco = tieneFoco;
	}

	public interface OnRomListener {
		void onRomClicked(Juego juego);
		void onRomFocused(Juego juego);
	}

	public void setOnRomListener(OnRomListener listener) {
		this.listener = listener;
	}

	public RomAdapter(Context context, List<Juego> juegos) {
		this.context = context;
		this.juegos = new ArrayList<>(juegos);
		this.juegosOriginales = new ArrayList<>(juegos);
	}

	public void filter(String query) {
		query = query.toLowerCase().trim();
		juegos.clear();
		if (query.isEmpty()) {
			juegos.addAll(juegosOriginales);
		} else {
			for (Juego juego : juegosOriginales) {
				if (juego.getNombre().toLowerCase().contains(query)) {
					juegos.add(juego);
				}
			}
		}
		notifyDataSetChanged();
	}

	public void actualizarLista(List<Juego> nuevaLista) {
		this.juegos = new ArrayList<>(nuevaLista);
		this.juegosOriginales = new ArrayList<>(nuevaLista);

		// Si la lista no está vacía, enfocamos el primer item
		if (!juegos.isEmpty()) {
			posFocused = 0;
		} else {
			posFocused = RecyclerView.NO_POSITION;
		}

		notifyDataSetChanged();
	}

	public Juego getJuego(int position) {
		return juegos.get(position);
	}

	@Override
	public int getItemCount() {
		return juegos.size();
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(context).inflate(R.layout.item_rom, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(final ViewHolder holder, int position) {
		Juego juego = juegos.get(position);

		String nombre = juego.getNombre().replaceAll("\\.[^.]+$", "");
		if (!nombre.isEmpty()) {
			nombre = nombre.substring(0, 1).toUpperCase() + nombre.substring(1);
		}

		holder.romName.setText(nombre);
		holder.romName.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/Exo2-BoldCondensed.otf"));
		holder.itemView.setTag(juego);

		// Mantener el foco solo si el searchBox NO está activo
		if (!searchBoxTieneFoco && position == posFocused) {
			holder.itemView.post(new Runnable() {
					@Override
					public void run() {
						holder.itemView.requestFocus();
					}
				});
		}
	}

	public class ViewHolder extends RecyclerView.ViewHolder {
		public TextView romName;

		public ViewHolder(View itemView) {
			super(itemView);
			romName = itemView.findViewById(R.id.romName);

			itemView.setFocusable(true);
			itemView.setFocusableInTouchMode(true);

			itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
					@Override
					public void onFocusChange(View v, boolean hasFocus) {
						int pos = getAdapterPosition();
						if (pos != RecyclerView.NO_POSITION && hasFocus) {
							posFocused = pos; // <--- guardas la posición
							if (listener != null) listener.onRomFocused(juegos.get(pos));
						}
					}
				});

			itemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						int pos = getAdapterPosition();
						if (pos != RecyclerView.NO_POSITION && listener != null) {
							listener.onRomClicked(juegos.get(pos));
						}
					}
				});
		}
	}
}
