package fm.icelink.chat.websync4;

import android.content.*;
import android.media.projection.MediaProjection;
import android.view.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import fm.icelink.*;
import fm.icelink.android.*;
import fm.icelink.android.LayoutManager;
import fm.icelink.websync4.*;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class App {

    // This flag determines the signalling mode used.
    // Note that Manual and Auto signalling do not Interop.
    private final static boolean SIGNAL_MANUALLY = false;
    private Signalling signalling;

    private OnReceivedTextListener textListener;

    private String sessionId;
    public String getSessionId() {
        return this.sessionId;
    }
    public void setSessionId(String sid) {
        this.sessionId = sid;
    }

    private String name;
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
    }

    private boolean enableAudioSend;
    public boolean getEnableAudioSend() {
        return this.enableAudioSend;
    }
    public void setEnableAudioSend(boolean enable) {
        this.enableAudioSend = enable;
    }

    private boolean enableAudioReceive;
    public boolean getEnableAudioReceive() {
        return this.enableAudioReceive;
    }
    public void setEnableAudioReceive(boolean enable) {
        this.enableAudioReceive = enable;
    }

    private boolean enableVideoSend;
    public boolean getEnableVideoSend() {
        return this.enableVideoSend;
    }
    public void setEnableVideoSend(boolean enable) {
        this.enableVideoSend = enable;
    }

    private boolean enableVideoReceive;
    public boolean getEnableVideoReceive() {
        return this.enableVideoReceive;
    }
    public void setEnableVideoReceive(boolean enable) {
        this.enableVideoReceive = enable;
    }

    private boolean enableScreenShare;
    public boolean getEnableScreenShare() {
        return this.enableScreenShare;
    }
    public void setEnableScreenShare(boolean enable) {
        this.enableScreenShare = enable;
    }

    private MediaProjection mediaProjection;
    public MediaProjection getMediaProjection() {
        return this.mediaProjection;
    }
    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    private IceServer[] iceServers = new IceServer[]
    {
        new IceServer("stun:turn.icelink.fm:3478"),
        //NB: url "turn:turn.icelink.fm:443" implies that the relay server supports both TCP and UDP
        //if you want to restrict the network protocol, use "turn:turn.icelink.fm:443?transport=udp"
        //or "turn:turn.icelink.fm:443?transport=tcp". For further info, refer to RFC 7065 3.1 URI Scheme Syntax
        //Secure TURN and STUN (i.e. turns and stuns schemata) are currently unsupported.
        new IceServer("turn:turn.icelink.fm:443", "test", "pa55w0rd!")
    };

    private HashMap<View, RemoteMedia> mediaTable;

    private String websyncServerUrl = "https://v4.websync.fm/websync.ashx"; // WebSync On-Demand

    private LocalMedia localMedia = null;
    private LayoutManager layoutManager = null;

    private fm.icelink.chat.websync4.AecContext aecContext;
    private boolean enableH264 = false;

    private Context context = null;

    private App(Context context) {
        this.context = context.getApplicationContext();

        mediaTable = new HashMap<>();

        enableAudioSend = true;
        enableAudioReceive = true;
        enableVideoSend = true;
        enableVideoReceive = true;

        // Log to the console.
        fm.icelink.Log.setProvider(new fm.icelink.android.LogProvider(LogLevel.Debug));
    }

    private static App app;

    public static synchronized App getInstance(Context context) {
        if (app == null) {
            app = new App(context);
        }
        return app;
    }

    /**
     * Convenience: allow registry for local and remote views for context menu.
     */
    public void registerAvailableViewsForContextMenu(final VideoChatFragment fragment) {
        if (fragment == null) {
            String e = "Cannot register for context menus on a null object.";
            Log.debug(e, new Exception(e));
        }

        // Register local.
        if (localMedia != null && localMedia.getView() != null) {
            fragment.registerForContextMenu((View)localMedia.getView());
        }

        // Register any remotes.
        if (mediaTable != null && !mediaTable.isEmpty()) {
            Iterator<Map.Entry<View, RemoteMedia>> i = mediaTable.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<View, RemoteMedia> e = i.next();
                fragment.registerForContextMenu(e.getKey());
            }
        }
    }

    public Future<fm.icelink.LocalMedia> startLocalMedia(final VideoChatFragment fragment) {
        return downloadOpenH264().then(new IFunction1<Object, Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke(Object o) {
                // Set up the local media.
                aecContext = new AecContext();
                if (fm.icelink.Global.equals(fm.icelink.Platform.getInstance().getArchitecture(), fm.icelink.Architecture.X86)) {
                    aecContext = null;
                }

                if (enableScreenShare) {
                    localMedia = new ScreenShareLocalMedia(mediaProjection, context, enableH264, !enableAudioSend, !enableVideoSend, aecContext);
                } else {
                    localMedia = new CameraLocalMedia(context, enableH264, !enableAudioSend, !enableVideoSend, aecContext);
                }

                final View localView = (View)localMedia.getView();
                // Set up the layout manager.
                fragment.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        layoutManager = new LayoutManager(fragment.container);
                        layoutManager.setLocalView(localView);
                    }
                });

                fragment.registerForContextMenu(localView);
                localView.setOnTouchListener(fragment);

                // Start the local media.
                return localMedia.start();
            }
        });
    }

    private Future<Object> downloadOpenH264() {
        return Promise.wrapPromise(new IFunction0<Future<Object>>() {
            public Future<Object> invoke() {
                Architecture arch = Platform.getInstance().getArchitecture();
                if (arch == Architecture.Armv7 || arch == Architecture.Armv8) {
                    final String libraryPath = context.getFilesDir() + "/libopenh264.so";
                    if (!new FileStream(libraryPath).exists()) {
                        String downloadUrl = fm.icelink.openh264.Utility.getDownloadUrl();
                        Log.info(String.format("OpenH264 library missing. Downloading now from: %s.", downloadUrl));
                        return HttpFileTransfer.downloadFile(downloadUrl, libraryPath).then(new IAction1<Object>() {
                            public void invoke(Object o) {
                                Log.info("OpenH264 library downloaded.");
                                System.load(libraryPath);
                                enableH264 = true;
                            }
                        });
                    }

                    System.load(libraryPath);
                    enableH264 = true;
                }
                return Promise.resolveNow(null);
            }
        });
    }

    public Future<fm.icelink.LocalMedia> stopLocalMedia() {
        return Promise.wrapPromise(new IFunction0<Future<fm.icelink.LocalMedia>>() {
            public Future<fm.icelink.LocalMedia> invoke() {
                if (localMedia == null) {
                    throw new RuntimeException("Local media has already been stopped.");
                }

                // Stop the local media.
                return localMedia.stop().then(new IAction1<fm.icelink.LocalMedia>() {
                    public void invoke(fm.icelink.LocalMedia o) {
                        // Tear down the layout manager.
                        if (layoutManager != null) {
                            layoutManager.removeRemoteViews();
                            layoutManager.unsetLocalView();
                            layoutManager = null;
                        }

                        // Tear down the local media.
                        if (localMedia != null) {
                            localMedia.destroy();
                            localMedia = null;
                        }
                    }
                });
            }
        });
    }

    public fm.icelink.Future<Object> joinAsync(final VideoChatFragment fragment, TextChatFragment textChat) {
        textListener = textChat;
        if (SIGNAL_MANUALLY) {
            signalling = manualSignalling(fragment);
        }
        else {
            signalling = autoSignalling(fragment);
        }

        return signalling.joinAsync();
    }

    private Signalling autoSignalling(final VideoChatFragment fragment) {
        return new AutoSignalling(websyncServerUrl, getSessionId(), name, new IFunction1<PeerClient, Connection>() {
            @Override
            public Connection invoke(PeerClient remoteClient) {
                return connection(fragment, remoteClient);
            }
        }, new IAction2<String, String> () {
            @Override
            public void invoke(String n, String m) {
                textListener.onReceivedText(n, m);
            }
        });
    }
	
    private Signalling manualSignalling(final VideoChatFragment fragment) {
        return new ManualSignalling(websyncServerUrl, getSessionId(), name, new IFunction1<PeerClient, Connection>(){
            @Override
            public Connection invoke(PeerClient remoteClient) {
                return connection(fragment, remoteClient);
            }
        }, new IAction2<String, String> () {
            @Override
            public void invoke(String n, String m) {
                textListener.onReceivedText(n, m);
            }
        });
   }

   private Connection connection(final VideoChatFragment fragment, final PeerClient remoteClient) {

       String n = remoteClient.getBoundRecords().get("userName").getValueJson();
       final String peerName = n.substring(1, n.length() - 1);

       // Create connection to remote client.
       final RemoteMedia remoteMedia = new RemoteMedia(context, enableH264, !enableAudioReceive, !enableVideoReceive, aecContext);
       final AudioStream audioStream = new AudioStream(enableAudioSend ? localMedia : null, enableAudioReceive ? remoteMedia : null);
       final VideoStream videoStream = new VideoStream(enableVideoSend ? localMedia : null, enableVideoReceive ? remoteMedia : null);

       final Connection connection = new Connection(new Stream[]{audioStream, videoStream});
       connection.setIceServers(iceServers);

       // Add the remote view to the layout.
       layoutManager.addRemoteView(remoteMedia.getId(), remoteMedia.getView());

       mediaTable.put(remoteMedia.getView(), remoteMedia);
       fragment.registerForContextMenu(remoteMedia.getView());
       remoteMedia.getView().setOnTouchListener(fragment);

       connection.addOnStateChange(new fm.icelink.IAction1<Connection>() {
           public void invoke(Connection c) {
               if (c.getState() == ConnectionState.Connected)
               {
                   textListener.onPeerJoined(peerName);
               }
               else if (c.getState() == ConnectionState.Closing ||
                       c.getState() == ConnectionState.Failing) {
                   // Remove the remote view from the layout.
                   if (layoutManager.getRemoteView(remoteMedia.getId()) != null) {
                       layoutManager.removeRemoteView(remoteMedia.getId());
                       mediaTable.remove(remoteMedia.getView());
                       remoteMedia.destroy();
                   }
               }
               else if (c.getState() == ConnectionState.Closed) {
                   textListener.onPeerLeft(peerName);
               }
               else if (c.getState() == ConnectionState.Failed) {
                   textListener.onPeerLeft(peerName);
                   if (!SIGNAL_MANUALLY)
                       signalling.reconnect(remoteClient, c);
               }
           }
       });

       return connection;
   }

    public fm.icelink.Future<Object> leaveAsync() {
        return signalling.leaveAsync();
    }

    private boolean usingFrontVideoDevice = true;

    public void useNextVideoDevice() {
        if (localMedia != null) {
            localMedia.changeVideoSourceInput(usingFrontVideoDevice ?
                    ((CameraSource) localMedia.getVideoSource()).getBackInput() :
                    ((CameraSource) localMedia.getVideoSource()).getFrontInput());

            usingFrontVideoDevice = !usingFrontVideoDevice;
        }
    }

    public Future<Object> pauseLocalVideo() {
        if (localMedia != null) {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null) {
                if (videoSource.getState() == MediaSourceState.Started) {
                    return videoSource.stop();
                }
            }
        }
        return Promise.resolveNow();
    }

    public Future<Object> resumeLocalVideo() {
        if (localMedia != null) {
            VideoSource videoSource = localMedia.getVideoSource();
            if (videoSource != null) {
                if (videoSource.getState() == MediaSourceState.Stopped) {
                    return videoSource.start();
                }
            }
        }
        return Promise.resolveNow();
    }

    public void setIsRecordingAudio(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingAudio() != record) {
                localMedia.toggleAudioRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingAudio() != record) {
                remote.toggleAudioRecording();
            }
        }
    }

    public boolean getIsRecordingAudio(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getIsRecordingAudio();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getIsRecordingAudio();
        }
        else return false;
    }

    public void setIsRecordingVideo(View v, boolean record)
    {
        if (localMedia.getView() == v) {
            if (localMedia.getIsRecordingVideo() != record) {
                localMedia.toggleVideoRecording();
            }
        } else {
            RemoteMedia remote = mediaTable.get(v);
            if (remote.getIsRecordingVideo() != record) {
                remote.toggleVideoRecording();
            }
        }
    }

    public boolean getIsRecordingVideo(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getIsRecordingVideo();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getIsRecordingVideo();
        }
        else return false;
    }

    public void setAudioMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setAudioMuted(mute);
        } else {
            mediaTable.get(v).setAudioMuted(mute);
        }
    }

    public boolean getAudioMuted(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getAudioMuted();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getAudioMuted();
        }
        else return false;
    }

    public void setVideoMuted(View v, boolean mute)
    {
        if (localMedia.getView() == v) {
            localMedia.setVideoMuted(mute);
        } else {
            mediaTable.get(v).setVideoMuted(mute);
        }
    }

    public boolean getVideoMuted(View v)
    {
        if (localMedia != null && localMedia.getView() != null && localMedia.getView() == v) {
            return localMedia.getVideoMuted();
        }
        else if (mediaTable.get(v) != null) {
            return mediaTable.get(v).getVideoMuted();
        }
        else return false;
    }

    public void writeLine(String message)
    {
        signalling.writeLine(message);
    }

    public interface OnReceivedTextListener {
        void onReceivedText(String name, String message);
        void onPeerJoined(String name);
        void onPeerLeft(String name);
    }
}
