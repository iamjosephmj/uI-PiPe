package tech.ssemaj.pipe.transport;

import tech.ssemaj.pipe.core.PipeMessage;

/** Provider -> host. */
interface IHostChannel {
    oneway void send(in PipeMessage message);
    oneway void onClosed(int closeReasonWire);
}
