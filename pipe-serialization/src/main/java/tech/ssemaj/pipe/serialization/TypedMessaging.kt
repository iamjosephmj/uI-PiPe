package tech.ssemaj.pipe.serialization

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import tech.ssemaj.pipe.host.PipeSession
import tech.ssemaj.pipe.provider.HostHandle

suspend inline fun <reified T> PipeSession.send(payload: T): Boolean = send(PipeCodec.encode(payload))
inline fun <reified T> PipeSession.messagesOf(): Flow<T> = messages.mapNotNull { PipeCodec.decodeOrNull<T>(it) }
suspend inline fun <reified T> HostHandle.send(payload: T): Boolean = send(PipeCodec.encode(payload))
