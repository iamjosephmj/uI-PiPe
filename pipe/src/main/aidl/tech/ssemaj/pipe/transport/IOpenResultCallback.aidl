package tech.ssemaj.pipe.transport;

import android.view.SurfaceControlViewHost.SurfacePackage;
import tech.ssemaj.pipe.transport.IEmbedSession;
import tech.ssemaj.pipe.transport.IGuestChannel;

oneway interface IOpenResultCallback {
    void onOpened(in SurfacePackage surfacePackage, IEmbedSession session, IGuestChannel guestChannel);
    void onDenied(String reason);
    void onError(String message);
}
