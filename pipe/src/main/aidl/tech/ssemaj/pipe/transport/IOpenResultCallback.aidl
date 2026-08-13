package tech.ssemaj.pipe.transport;

import tech.ssemaj.pipe.transport.IEmbedSession;
import tech.ssemaj.pipe.transport.IGuestChannel;

oneway interface IOpenResultCallback {
    void onOpened(IEmbedSession session, IGuestChannel guestChannel);
    void onDenied(String reason);
    void onError(String message);
}
