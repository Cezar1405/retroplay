package com.cezar.retroplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

public class SystemAdapter extends RecyclerView.Adapter<SystemAdapter.ViewHolder> {

	private List<Sistema> sistemaList;
	private Context context;

	private OnSystemFocusListener focusListener;
	private OnSystemClickListener clickListener;

	public void setOnSystemFocusListener(OnSystemFocusListener listener) {
		this.focusListener = listener;
	}

	public void setOnSystemClickListener(OnSystemClickListener listener) {
		this.clickListener = listener;
	}

	public interface OnSystemFocusListener {
		void onSystemFocused(Sistema sistema);
	}

	public interface OnSystemClickListener {
		void onSystemClicked(Sistema sistema);
	}

	public static class ViewHolder extends RecyclerView.ViewHolder {
		public ImageView icon;
		public TextView name;

		public ViewHolder(final View view, final List<Sistema> sistemas,
						  final OnSystemFocusListener focusListener,
						  final OnSystemClickListener clickListener) {
			super(view);
			icon = view.findViewById(R.id.systemIco);
			name = view.findViewById(R.id.systemNam);

			view.setFocusable(true);
			view.setFocusableInTouchMode(true);

			view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
					@Override
					public void onFocusChange(View v, boolean hasFocus) {
						if (hasFocus) {
							v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start();
							int pos = getAdapterPosition();
							if (focusListener != null && pos != RecyclerView.NO_POSITION) {
								Sistema sistema = sistemas.get(pos);
								focusListener.onSystemFocused(sistema);
							}
						} else {
							v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
						}
					}
				});

			view.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						int pos = getAdapterPosition();
						if (clickListener != null && pos != RecyclerView.NO_POSITION) {
							if (v.isFocused()) {
								// Segundo click (ya tiene el foco)
								clickListener.onSystemClicked(sistemas.get(pos));
							} else {
								// Solo dar foco
								v.requestFocus();
							}
						}
					}
				});
		}
	}

	public SystemAdapter(Context context, List<Sistema> lista) {
		this.context = context;
		this.sistemaList = lista;
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(context).inflate(R.layout.item_system, parent, false);
		return new ViewHolder(view, sistemaList, focusListener, clickListener);
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		final Sistema sistema = sistemaList.get(position);
		holder.name.setText(sistema.getNombre());

		try {
			String ruta = "sistemas/" + sistema.getNombre() + ".webp";
			InputStream is = context.getAssets().open(ruta);
			Bitmap bitmap = BitmapFactory.decodeStream(is);
			holder.icon.setImageBitmap(bitmap);
			is.close();
		} catch (Exception e) {
			holder.icon.setImageResource(R.drawable.ic_notimage);
		}
	}

	@Override
	public int getItemCount() {
		return sistemaList.size();
	}
}
