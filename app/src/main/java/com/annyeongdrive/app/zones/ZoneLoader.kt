package com.annyeongdrive.app.zones

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** assets/zones.json을 읽어 ZoneData로 바꾼다. 파일은 tools/build_zones.py가 만든다. */
object ZoneLoader {
    private const val FILE_NAME = "zones.json"

    fun load(context: Context): ZoneData {
        val text = context.assets.open(FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        return ZoneData(
            schoolZones = root.getJSONArray("schoolZones").map {
                SchoolZone(
                    id = it.getString("id"),
                    name = it.optString("name"),
                    lat = it.getDouble("lat"),
                    lon = it.getDouble("lon"),
                    radiusM = it.optDouble("radiusM", 300.0),
                    speedLimitKmh = it.optInt("speedLimitKmh", 30),
                )
            },
            speedCameras = root.getJSONArray("speedCameras").map {
                SpeedCamera(
                    id = it.getString("id"),
                    lat = it.getDouble("lat"),
                    lon = it.getDouble("lon"),
                    speedLimitKmh = it.getInt("speedLimitKmh"),
                    protectedZone = it.optString("protectedZone", "none"),
                    place = it.optString("place"),
                )
            },
            parkingCameras = root.getJSONArray("parkingCameras").map {
                ParkingCamera(
                    id = it.getString("id"),
                    lat = it.getDouble("lat"),
                    lon = it.getDouble("lon"),
                    place = it.optString("place"),
                )
            },
        )
    }

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
