package tech.ssemaj.pipe.transport;

import tech.ssemaj.pipe.transport.OpenSpec;
import tech.ssemaj.pipe.transport.IHostChannel;
import tech.ssemaj.pipe.transport.IOpenResultCallback;

interface IEmbedProvider {
    int protocolVersion();
    /** Async: result (surface or denial) arrives on [callback]. Caller identity = Binder.getCallingUid(). */
    oneway void open(in OpenSpec spec, IHostChannel hostChannel, IOpenResultCallback callback);
}
