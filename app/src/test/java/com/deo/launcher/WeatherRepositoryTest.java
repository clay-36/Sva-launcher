package com.deo.launcher;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WeatherRepositoryTest {
    @Test public void parsesCurrentAndNextThreeDays() throws Exception {
        JSONObject root = new JSONObject("{\"current\":{\"temperature_2m\":28.4,\"apparent_temperature\":30.1,\"weather_code\":2,\"is_day\":1,\"wind_speed_10m\":12.5},"
                + "\"daily\":{\"time\":[\"2026-09-28\",\"2026-09-29\",\"2026-09-30\",\"2026-10-01\"],"
                + "\"weather_code\":[2,61,3,95],\"temperature_2m_max\":[30,29,31,28],"
                + "\"temperature_2m_min\":[22,21,23,20],\"precipitation_probability_max\":[10,70,20,90]}}");
        WeatherRepository.Forecast forecast = WeatherRepository.parseForecast(root, "Pune", 1234L);
        assertEquals("Pune", forecast.location);
        assertEquals(28.4, forecast.temperature, .001);
        assertEquals(3, forecast.days.size());
        assertEquals("2026-09-29", forecast.days.get(0).date);
        assertEquals(70, forecast.days.get(0).precipitation);
    }

    @Test public void cacheRoundTrips() throws Exception {
        java.util.ArrayList<WeatherRepository.Day> days = new java.util.ArrayList<>();
        days.add(new WeatherRepository.Day("2026-09-29", 61, 20, 30, 75));
        WeatherRepository.Forecast original = new WeatherRepository.Forecast("Mumbai", 99, 29, 33, 8, 61, true, days);
        WeatherRepository.Forecast restored = WeatherRepository.Forecast.fromJson(original.toJson());
        assertEquals(original.location, restored.location);
        assertEquals(original.temperature, restored.temperature, .001);
        assertEquals(75, restored.days.get(0).precipitation);
    }

    @Test public void mapsConditionsAndConversions() {
        assertEquals("Clear", WeatherRepository.condition(0));
        assertEquals("Rain", WeatherRepository.condition(82));
        assertEquals("Thunderstorm", WeatherRepository.condition(95));
        assertEquals(32, WeatherRepository.fahrenheit(0), .001);
        assertEquals(62.1371, WeatherRepository.milesPerHour(100), .001);
        assertTrue(WeatherRepository.validCoordinates(18.52, 73.85));
        assertFalse(WeatherRepository.validCoordinates(100, 73.85));
    }
}
