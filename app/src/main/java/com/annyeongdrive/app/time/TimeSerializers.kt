package com.annyeongdrive.app.time

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration
import java.time.Instant

/** [Instant]를 ISO-8601 문자열(예: 2026-11-11T00:00:00Z)로 저장하는 직렬화기. */
object InstantIsoSerializer : KSerializer<Instant> {

    /** 문자열 하나로 저장한다는 설명. */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("annyeong.Instant", PrimitiveKind.STRING)

    /**
     * 시각을 ISO-8601 문자열로 쓴다.
     *
     * @param encoder 출력
     * @param value 시각
     */
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    /**
     * ISO-8601 문자열을 시각으로 읽는다.
     *
     * @param decoder 입력
     * @return 시각
     */
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** [Duration]을 ISO-8601 기간 문자열(예: PT24H)로 저장하는 직렬화기. */
object DurationIsoSerializer : KSerializer<Duration> {

    /** 문자열 하나로 저장한다는 설명. */
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("annyeong.Duration", PrimitiveKind.STRING)

    /**
     * 기간을 ISO-8601 문자열로 쓴다.
     *
     * @param encoder 출력
     * @param value 기간
     */
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())

    /**
     * ISO-8601 기간 문자열을 기간으로 읽는다.
     *
     * @param decoder 입력
     * @return 기간
     */
    override fun deserialize(decoder: Decoder): Duration = Duration.parse(decoder.decodeString())
}
