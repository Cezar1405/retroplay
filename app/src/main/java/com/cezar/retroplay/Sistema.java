package com.cezar.retroplay;

import java.util.List;

public class Sistema {
	private String nombre;
	private List<Juego> juegos;

	public Sistema(String nombre, List<Juego> juegos) {
		this.nombre = nombre;
		this.juegos = juegos;
	}

	public String getNombre() {
		return nombre;
	}

	public List<Juego> getJuegos() {
		return juegos;
	}
}
