package layout;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;


import fm.Log;
import fm.icelink.chat.websync4.App;
import fm.icelink.chat.websync4.R;

public class VideoChatFragment extends Fragment implements View.OnTouchListener {
    private App app;
    public static RelativeLayout container;
    private FrameLayout layout;
    boolean disableAudio = false;
    boolean disableVideo = false;

    private GestureDetector gestureDetector;

    private View openContextMenuView;

    private OnVideoReadyListener listener;

    public VideoChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = App.getInstance(null);
    }

    @Override
    public boolean onContextItemSelected (MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.mute_audio) {
            app.setAudioMuted(openContextMenuView, !item.isChecked());
        } else if (id == R.id.mute_video) {
            app.setVideoMuted(openContextMenuView, !item.isChecked());
        } else if (id == R.id.record_video) {
            app.setIsRecordingVideo(openContextMenuView, !item.isChecked());
        } else if (id == R.id.record_audio) {
            app.setIsRecordingAudio(openContextMenuView, !item.isChecked());
        }

        return true;
    }

    @Override
    public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        if (v == null)
        {
            Log.debug("view is null");
        }
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getActivity() != null) {
            MenuInflater inflater = this.getActivity().getMenuInflater();
            inflater.inflate(R.menu.menu_context, menu);
            openContextMenuView = v;

            menu.getItem(0).setChecked(app.getVideoMuted(v));
            menu.getItem(1).setChecked(app.getAudioMuted(v));
            menu.getItem(2).setChecked(disableVideo);
            menu.getItem(3).setChecked(disableAudio);
            menu.getItem(4).setChecked(app.getIsRecordingVideo(v));
            menu.getItem(5).setChecked(app.getIsRecordingAudio(v));
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState)
    {
        try {
            if (getActivity() != null) {
                // For demonstration purposes, use the double-tap gesture
                // to switch between the front and rear camera.
                gestureDetector = new GestureDetector(this.getActivity(), new GestureDetector.SimpleOnGestureListener() {
                    public boolean onDoubleTap(MotionEvent e) {
                        app.useNextVideoDevice();
                        return true;
                    }

                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                });

                layout = (FrameLayout) view.findViewById(R.id.layout);
                getView().setOnTouchListener(this);

                // Preserve a static container across
                // activity destruction/recreation.
                RelativeLayout c = (RelativeLayout) view.findViewById(R.id.container);
                if (container == null) {
                    container = c;

                    Toast.makeText(getActivity(), "Double-tap to switch camera.", Toast.LENGTH_SHORT).show();
                }
                layout.removeView(c);

                listener.onVideoReady();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void onPause() {
        // Android requires us to pause the local
        // video feed when pausing the activity.
        // Not doing this can cause unexpected side
        // effects and crashes.
        app.pauseLocalVideo().waitForResult();

        // Remove the static container from the current layout.
        if (container != null) {
            layout.removeView(container);
        }

        super.onPause();
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        // Handle the double-tap event.
        if (gestureDetector == null || !gestureDetector.onTouchEvent(motionEvent)) {
            return view.onTouchEvent(motionEvent);
        }
        return false;
    }

    public void onResume() {
        super.onResume();

        // Add the static container to the current layout.
        if (container != null) {
            layout.addView(container);
        }

        // Resume the local video feed.
        app.resumeLocalVideo().waitForResult();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View root = inflater.inflate(R.layout.fragment_video_chat, container, false);

        if (app != null) {
            app.registerAvailableViewsForContextMenu(this);
        }

        return root;
    }

    public interface OnVideoReadyListener {
        void onVideoReady();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnVideoReadyListener) {
            listener = (OnVideoReadyListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement VideoChatFragment.OnVideoReadyListener");
        }
    }
}
