package tech.ssemaj.pipe.serialization

import android.os.Bundle
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.serializer
import tech.ssemaj.pipe.core.PipeMessage

/** Opt-in typed messaging: encodes/decodes payloads as CBOR bytes inside the [PipeMessage] Bundle envelope. */
object PipeCodec {
    @PublishedApi internal const val KEY_BYTES = "tech.ssemaj.pipe.codec.cbor"
    @PublishedApi internal const val KEY_TYPE = "tech.ssemaj.pipe.codec.type"

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> encode(payload: T): PipeMessage {
        val bytes = Cbor.encodeToByteArray(serializer<T>(), payload)
        val b = Bundle().apply {
            putByteArray(KEY_BYTES, bytes)
            putString(KEY_TYPE, T::class.qualifiedName)
        }
        return PipeMessage(payload = b)
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> decodeOrNull(message: PipeMessage): T? {
        if (message.payload.getString(KEY_TYPE) != T::class.qualifiedName) return null
        val bytes = message.payload.getByteArray(KEY_BYTES) ?: return null
        return runCatching { Cbor.decodeFromByteArray(serializer<T>(), bytes) }.getOrNull()
    }
}
