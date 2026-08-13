package tech.ssemaj.pipe.transport;

import tech.ssemaj.pipe.core.PipeMessage;

/** Host -> provider. */
interface IGuestChannel {
    oneway void send(in PipeMessage message);
}
