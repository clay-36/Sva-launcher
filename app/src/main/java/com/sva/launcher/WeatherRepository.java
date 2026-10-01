package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small Open-Meteo client. Values are cached in metric units and formatted by the UI. */
final class WeatherRepository {
    static final class Location {
        final String label;
        final double latitude, longitude;
        Location(String label, double latitude, double longitude) {
            this.label = label; this.latitude = latitude; this.longitude = longitude;
        }
    }

    static final class Day {
        final String date;
        final int code, precipitation;
        final double low, high;
        Day(String date, int code, double low, double high, int precipitation) {
            this.date = date; this.code = code; this.low = low; this.high = high;
            this.precipitation = precipitation;
        }
    }

    static final class Forecast {
        final String location;
        final long fetchedAt;
        final double temperature, apparentTemperature, windSpeed;
        final int code;
        final boolean day;
        final List<Day> days;
        Forecast(String location, long fetchedAt, double temperature, double apparentTemperature,
                 double windSpeed, int code, boolean day, List<Day> days) {
            this.location = location; this.fetchedAt = fetchedAt; this.temperature = temperature;
            this.apparentTemperature = apparentTemperature; this.windSpeed = windSpeed;
            this.code = code; this.day = day; this.days = days;
        }

        JSONObject toJson() throws Exception {
            JSONArray daily = new JSONArray();
            for (Day item : days) daily.put(new JSONObject().put("date", item.date).put("code", item.code)
                    .put("low", item.low).put("high", item.high).put("precipitation", item.precipitation));
            return new JSONObject().put("location", location).put("fetchedAt", fetchedAt)
                    .put("temperature", temperature).put("apparentTemperature", apparentTemperature)
                    .put("windSpeed", windSpeed).put("code", code).put("day", day).put("days", daily);
        }

        static Forecast fromJson(JSONObject value) throws Exception {
            if (value == null) return null;
            JSONArray daily = value.getJSONArray("days");
            ArrayList<Day> days = new ArrayList<>();
            for (int i = 0; i < daily.length(); i++) {
                JSONObject item = daily.getJSONObject(i);
                days.add(new Day(item.getString("date"), item.getInt("code"), item.getDouble("low"),
                        item.getDouble("high"), item.optInt("precipitation", 0)));
            }
            return new Forecast(value.optString("location", "Weather"), value.getLong("fetchedAt"),
                    value.getDouble("temperature"), value.getDouble("apparentTemperature"),
                    value.getDouble("windSpeed"), value.getInt("code"), value.optBoolean("day", true), days);
        }
    }

    private WeatherRepository() { }

    static List<Location> search(String query, Locale locale) throws Exception {
        String language = locale == null ? "en" : locale.getLanguage().toLowerCase(Locale.ROOT);
        if (language.length() != 2) language = "en";
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=5&format=json&language="
                + language + "&name=" + URLEncoder.encode(query.trim(), "UTF-8");
        JSONObject root = new JSONObject(download(url));
        JSONArray results = root.optJSONArray("results");
        ArrayList<Location> locations = new ArrayList<>();
        if (results == null) return locations;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i); if (item == null) continue;
            String name = item.optString("name", "").trim(); if (name.isEmpty()) continue;
            String admin = item.optString("admin1", "").trim(), country = item.optString("country", "").trim();
            String label = name + (admin.isEmpty() || admin.equals(name) ? "" : ", " + admin)
                    + (country.isEmpty() ? "" : ", " + country);
            double latitude = item.optDouble("latitude", Double.NaN), longitude = item.optDouble("longitude", Double.NaN);
            if (validCoordinates(latitude, longitude)) locations.add(new Location(label, latitude, longitude));
        }
        return locations;
    }

    static Forecast fetch(Location location, long now) throws Exception {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=4",
                location.latitude, location.longitude);
        return parseForecast(new JSONObject(download(url)), location.label, now);
    }

    static Forecast parseForecast(JSONObject root, String label, long now) throws Exception {
        JSONObject current = root.getJSONObject("current"), daily = root.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time"), codes = daily.getJSONArray("weather_code");
        JSONArray highs = daily.getJSONArray("temperature_2m_max"), lows = daily.getJSONArray("temperature_2m_min");
        JSONArray rain = daily.optJSONArray("precipitation_probability_max");
        int count = Math.min(4, Math.min(Math.min(dates.length(), codes.length()), Math.min(highs.length(), lows.length())));
        if (count < 2) throw new IllegalArgumentException("Forecast did not include upcoming days.");
        ArrayList<Day> days = new ArrayList<>();
        for (int i = 1; i < count; i++) days.add(new Day(dates.getString(i), codes.getInt(i),
                lows.getDouble(i), highs.getDouble(i), rain == null ? 0 : rain.optInt(i, 0)));
        return new Forecast(label, now, current.getDouble("temperature_2m"),
                current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
                current.optDouble("wind_speed_10m", 0), current.getInt("weather_code"),
                current.optInt("is_day", 1) == 1, days);
    }

    static boolean validCoordinates(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if ((code >= 61 && code <= 67) || code == 80 || code == 81 || code == 82) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Mixed weather";
    }

    static String symbol(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 3) return "☁";
        if (code == 45 || code == 48) return "≋";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄";
        if (code >= 95) return "ϟ";
        return "☂";
    }

    static double fahrenheit(double celsius) { return celsius * 9d / 5d + 32d; }
    static double milesPerHour(double kmh) { return kmh * 0.621371d; }

    private static String download(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(6000); connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "SvaLauncher/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("Weather service returned " + status + ".");
            if (connection.getContentLength() > 1024 * 1024) throw new IllegalStateException("Weather response was too large.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read, total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read; if (total > 1024 * 1024) throw new IllegalStateException("Weather response was too large.");
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
