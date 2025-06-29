package com.cezar.retroplay;

public class Juego {
	private String nombre;
	private String url;
	private String sistema;
	private String peso;

	public Juego(String nombre, String url, String sistema, String peso) {
		this.nombre = nombre;
		this.url = url;
		this.sistema = sistema;
		this.peso = peso;
	}

	public String getNombre() {
		return nombre;
	}

	public String getUrl() {
		return url;
	}

	public String getSistema() {
		return sistema;
	}

	public String getPeso() {
		return peso;
	}
}
