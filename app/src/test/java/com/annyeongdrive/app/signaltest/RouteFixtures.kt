package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.R
import com.annyeongdrive.app.guide.GuidanceMessage
import com.annyeongdrive.app.guide.GuidanceSentence
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.zones.ParkingCamera
import com.annyeongdrive.app.zones.SchoolZone
import com.annyeongdrive.app.zones.SpeedCamera
import com.annyeongdrive.app.zones.ZoneData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 안내 하나를 규칙과 문장으로 적은 것. 기대 목록 파일과 실제 안내를 같은 모양으로 비교한다.
 *
 * @property rule 규칙 이름(SPEED_CAMERA 등)
 * @property sentences 문장 목록
 */
data class AnnouncementLine(val rule: String, val sentences: List<String>)

/**
 * 주행 경로 기대 목록 파일(tools/smoke/expected/route_*.json). 스모크(에뮬레이터)와 이 JVM 테스트가 함께 쓴다.
 *
 * @property route 경로 GPX 파일(저장소 기준)
 * @property kmh 재생 속도
 * @property spoken 음성으로 말해야 하면 true(영어), 알림으로만 띄우면 false(한국어)
 * @property announcements 주행 중 안내 목록(순서대로)
 * @property announcementKm 안내마다 나오는 경로 위치(시작부터 km). 스모크가 실제 앱의 위치를 이 값과 비교한다
 * @property stopAtEnd 경로 끝에서 정차할 때의 주정차 경고
 */
data class RouteExpectation(
    val route: String,
    val kmh: Double,
    val spoken: Boolean,
    val announcements: List<AnnouncementLine>,
    val announcementKm: List<Double>,
    val stopAtEnd: AnnouncementLine,
)

/**
 * 재생 위치 하나. 경로 시작부터의 거리를 함께 적는다.
 *
 * @property point 위치
 * @property km 시작부터 거리(km)
 */
data class ReplayPoint(val point: GeoPoint, val km: Double)

/** 경로 주행 테스트가 쓰는 파일(구역 데이터, GPX, 기대 목록, 문구)을 읽는다. 경로는 app 모듈 기준이다. */
object RouteFixtures {

    private const val REPO = ".."
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val MPS_PER_KMH = 1 / 3.6
    private const val METERS_PER_KM = 1000.0

    /**
     * 앱에 들어 있는 시연 지역 구역 데이터(zones.json)를 읽는다.
     *
     * @return 구역 데이터
     */
    fun zones(): ZoneData {
        val root = Json.parseToJsonElement(File("src/main/assets/zones.json").readText()).jsonObject
        return ZoneData(
            schoolZones = root.getValue("schoolZones").jsonArray.map { schoolZoneOf(it.jsonObject) },
            speedCameras = root.getValue("speedCameras").jsonArray.map { speedCameraOf(it.jsonObject) },
            parkingCameras = root.getValue("parkingCameras").jsonArray.map { parkingCameraOf(it.jsonObject) },
        )
    }

    /**
     * 기대 목록 파일을 읽는다.
     *
     * @param name 파일 이름(예: route_en.json)
     * @return 기대 목록
     */
    fun expectation(name: String): RouteExpectation {
        val root = Json.parseToJsonElement(File("$REPO/tools/smoke/expected/$name").readText()).jsonObject
        return RouteExpectation(
            route = root.getValue("route").jsonPrimitive.content,
            kmh = root.getValue("kmh").jsonPrimitive.double,
            spoken = root.getValue("spoken").jsonPrimitive.boolean,
            announcements = root.getValue("announcements").jsonArray.map { lineOf(it.jsonObject) },
            announcementKm = root.getValue("announcements").jsonArray.map { it.jsonObject.getValue("atKm").jsonPrimitive.double },
            stopAtEnd = lineOf(root.getValue("stopAtEnd").jsonObject),
        )
    }

    /**
     * GPX 경로를 재생 속도로 1초마다 지나는 점들로 나눈다. tools/gpx_replay.py의 densify와 같은 방식이다.
     *
     * @param route 저장소 기준 GPX 경로
     * @param kmh 재생 속도
     * @return 1초 간격 위치와 시작부터 거리 목록
     */
    fun replayPoints(route: String, kmh: Double): List<ReplayPoint> {
        val points = gpxPoints(File("$REPO/$route"))
        val stepM = kmh * MPS_PER_KMH
        val out = mutableListOf(ReplayPoint(points.first(), 0.0))
        var totalM = 0.0
        points.zipWithNext().forEach { (a, b) ->
            val segmentM = distanceM(a, b)
            val count = max(1, ceil(segmentM / stepM).toInt())
            (1..count).forEach { i ->
                val t = i.toDouble() / count
                out += ReplayPoint(interpolate(a, b, t), (totalM + segmentM * t) / METERS_PER_KM)
            }
            totalM += segmentM
        }
        return out
    }

    /**
     * 안내 하나를 규칙과 실제 문장으로 바꾼다.
     *
     * @param message 안내
     * @param language 문구 언어 폴더(values 또는 values-en)
     * @return 규칙과 문장
     */
    fun lineOf(message: GuidanceMessage, language: String): AnnouncementLine =
        AnnouncementLine(message.rule.name, message.sentences.map { textOf(it, language) })

    /**
     * 문장 하나를 그 언어의 문구로 바꾼다. 제한속도가 있으면 자리 표시에 넣는다.
     *
     * @param sentence 문장(문구 ID + 제한속도)
     * @param language 문구 언어 폴더
     * @return 문구
     */
    private fun textOf(sentence: GuidanceSentence, language: String): String {
        val template = strings(language).getValue(nameOf(sentence.textId))
        val limit = sentence.limitKmh ?: return template
        return String.format(Locale.ROOT, template, limit)
    }

    /**
     * 문구 ID 숫자를 문구 이름으로 바꾼다.
     *
     * @param id R.string의 ID
     * @return 문구 이름
     */
    private fun nameOf(id: Int): String = R.string::class.java.fields.first { it.getInt(null) == id }.name

    /**
     * strings.xml을 읽는다.
     *
     * @param language 문구 언어 폴더
     * @return 문구 이름 → 문구
     */
    private fun strings(language: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$language/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    /**
     * GPX의 trkpt·rtept를 읽는다.
     *
     * @param file GPX 파일
     * @return 위치 목록
     */
    private fun gpxPoints(file: File): List<GeoPoint> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return listOf("trkpt", "rtept").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
        }.map { GeoPoint(it.getAttribute("lat").toDouble(), it.getAttribute("lon").toDouble()) }
    }

    /**
     * 두 점 사이를 비율만큼 나아간 점을 만든다.
     *
     * @param a 시작점
     * @param b 끝점
     * @param t 비율(0~1)
     * @return 사이의 점
     */
    private fun interpolate(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint =
        GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)

    /**
     * 두 점의 거리를 잰다(하버사인, gpx_replay.py와 같은 식).
     *
     * @param a 점
     * @param b 점
     * @return 거리(m)
     */
    private fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val dla = la2 - la1
        val dlo = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dla / 2) * sin(dla / 2) + cos(la1) * cos(la2) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /**
     * 기대 목록의 안내 한 줄을 읽는다.
     *
     * @param item JSON 객체
     * @return 규칙과 문장
     */
    private fun lineOf(item: JsonObject): AnnouncementLine = AnnouncementLine(
        rule = item.getValue("rule").jsonPrimitive.content,
        sentences = item.getValue("sentences").jsonArray.map { it.jsonPrimitive.content },
    )

    /**
     * 보호구역 하나를 읽는다.
     *
     * @param item JSON 객체
     * @return 보호구역
     */
    private fun schoolZoneOf(item: JsonObject) = SchoolZone(
        id = item.getValue("id").jsonPrimitive.content,
        name = item.getValue("name").jsonPrimitive.content,
        lat = item.getValue("lat").jsonPrimitive.double,
        lon = item.getValue("lon").jsonPrimitive.double,
        radiusM = item.getValue("radiusM").jsonPrimitive.double,
        speedLimitKmh = item["speedLimitKmh"]?.jsonPrimitive?.intOrNull,
    )

    /**
     * 과속 카메라 하나를 읽는다.
     *
     * @param item JSON 객체
     * @return 과속 카메라
     */
    private fun speedCameraOf(item: JsonObject) = SpeedCamera(
        id = item.getValue("id").jsonPrimitive.content,
        lat = item.getValue("lat").jsonPrimitive.double,
        lon = item.getValue("lon").jsonPrimitive.double,
        speedLimitKmh = item.getValue("speedLimitKmh").jsonPrimitive.int,
        protectedZone = item.getValue("protectedZone").jsonPrimitive.content,
        place = item.getValue("place").jsonPrimitive.content,
    )

    /**
     * 주정차 카메라 하나를 읽는다.
     *
     * @param item JSON 객체
     * @return 주정차 카메라
     */
    private fun parkingCameraOf(item: JsonObject) = ParkingCamera(
        id = item.getValue("id").jsonPrimitive.content,
        lat = item.getValue("lat").jsonPrimitive.double,
        lon = item.getValue("lon").jsonPrimitive.double,
        place = item.getValue("place").jsonPrimitive.content,
    )
}
