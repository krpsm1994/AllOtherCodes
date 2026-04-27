package com.sai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONArray;
import org.json.JSONObject;

public class PrintInstruments {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		printInstruments();
	}
	
	private static void printInstruments() {
		String url = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.GET()
					.build();

			HttpResponse<String> response = HttpClient.newHttpClient()
					.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				System.err.println("Failed to fetch scrip master [HTTP " + response.statusCode() + "]");
				return;
			}

			JSONArray instruments = new JSONArray(response.body());
			System.out.println("Total instruments: " + instruments.length());
			System.out.println("=== Equity (-EQ) Instruments ===");
			for (int i = 0; i < instruments.length(); i++) {
				JSONObject instrument = instruments.getJSONObject(i);
				String symbol = instrument.optString("symbol", "");
				String token = instrument.optString("token", "");
				String name = instrument.optString("name", "");
				if (symbol.endsWith("-EQ")) {
					System.out.println("{\"token\":\"" + token + "\",\"symbol\":\"" + name + "\"}");
				}
			}
			

			// Pass 1: collect unique base symbol names present in NFO segment
			/*java.util.Set<String> nfoNames = new java.util.HashSet<>();
			for (int i = 0; i < instruments.length(); i++) {
				JSONObject instrument = instruments.getJSONObject(i);
				if ("NFO".equalsIgnoreCase(instrument.optString("exch_seg", ""))) {
					nfoNames.add(instrument.optString("name", ""));
				}
			}

			// Pass 2: print EQ instruments whose base symbol/name exists in NFO
			System.out.println("=== EQ Stocks that have NFO instruments ===");
			for (int i = 0; i < instruments.length(); i++) {
				JSONObject instrument = instruments.getJSONObject(i);
				String symbol = instrument.optString("symbol", "");
				if (!symbol.endsWith("-EQ")) continue;

				String name = instrument.optString("name", "");
				String base = symbol.replace("-EQ", "");
				if (nfoNames.contains(name) || nfoNames.contains(base)) {
					String token = instrument.optString("token", "");
					System.out.println("{\"token\":\"" + token + "\",\"symbol\":\"" + base + "\"}");
				}
			}*/
		} catch (Exception e) {
			System.err.println("Error fetching instruments: " + e.getMessage());
			e.printStackTrace();
		}
	}

}
