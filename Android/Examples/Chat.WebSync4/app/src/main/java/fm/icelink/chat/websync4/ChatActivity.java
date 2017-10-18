package fm.icelink.chat.websync4;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import java.util.List;

import fm.icelink.*;
import fm.icelink.LocalMedia;
import layout.TextChatFragment;
import layout.VideoChatFragment;

public class ChatActivity extends AppCompatActivity implements VideoChatFragment.OnVideoReadyListener, TextChatFragment.OnTextReadyListener {

    private static boolean localMediaStarted = false;
    private static boolean conferenceStarted = false;
    private boolean videoReady = false;
    private boolean textReady = false;
    private App app;
    private ViewPager viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        app = App.getInstance(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewPager = (ViewPager) findViewById(R.id.pager);
        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), this);
        viewPager.setAdapter(adapter);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == PagerAdapter.TextTabIndex) {
                    UpdateBadge(false);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        // Iterate over all tabs and set the custom view
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            tab.setCustomView(adapter.getTabView(i));
        }
    }

    public void onNewMessage()
    {
        int i = viewPager.getCurrentItem();

        if (i == PagerAdapter.VideoTabIndex) {
            UpdateBadge(true);
        }
    }

    public void onVideoReady()
    {
        videoReady = true;

        if (videoReady && textReady)
        {
            start();
        }
    }

    public void onTextReady()
    {
        textReady = true;

        if (videoReady && textReady)
        {
            start();
        }
    }

    public void onBackPressed() { stop(); }

    private void start() {
        if (!localMediaStarted) {
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            final VideoChatFragment videoChatFragment = (VideoChatFragment)(fragments.get(0) instanceof VideoChatFragment ? fragments.get(0) : fragments.get(1));
            final TextChatFragment textChatFragment = (TextChatFragment)(fragments.get(0) instanceof TextChatFragment ? fragments.get(0) : fragments.get(1));

            app.startLocalMedia(videoChatFragment).then(new IFunction1<LocalMedia, Future<Object>>() {
                @Override
                public Future<Object> invoke(LocalMedia o) {

                    localMediaStarted = true;
                    return app.joinAsync(videoChatFragment, textChatFragment).then(new IAction1<Object>() {
                        public void invoke(Object o) {
                            conferenceStarted = true;
                        }
                    });
                }
            }, new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    Log.error("Could not start local media", e);
                    alert(e.getMessage());
                }
            }).fail(new IAction1<Exception>() {
                @Override
                public void invoke(Exception e) {
                    fm.icelink.Log.error("Could not join conference.", e);
                    alert(e.getMessage());
                }
            });
        }
    }

    private void stop() {
        if (conferenceStarted && localMediaStarted) {
            app.leaveAsync().then(
                    new IAction1<Object>() {
                        public void invoke(Object o) {
                            conferenceStarted = false;
                            stopLocalMediaAndFinish();
                        }
                    },
                    new IAction1<Exception>() {
                        @Override
                        public void invoke(Exception e) {
                            Log.error("Could not leave conference", e);
                            alert(e.getMessage());
                        }
                    }
            );
        } else if (localMediaStarted) {
            stopLocalMediaAndFinish();
        } else {
            finish();
        }
    }

    private void stopLocalMediaAndFinish() {
        app.stopLocalMedia().then(
                new IAction1<fm.icelink.LocalMedia>() {
                    @Override
                    public void invoke(LocalMedia localMedia) {
                        localMediaStarted = false;
                        finish();
                    }
                },
                new IAction1<Exception>() {
                    @Override
                    public void invoke(Exception e) {
                        fm.icelink.Log.error("Could not stop local media", e);
                        alert(e.getMessage());
                    }
                }
        );
    }

    public void alert(String format, Object... args) {
        final String text = String.format(format, args);
        final Activity activity = this;
        activity.runOnUiThread(new Runnable() {
            public void run() {
                AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                alert.setMessage(text);
                alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                alert.show();
            }
        });
    }

    private void UpdateBadge(boolean visible) {
        View view = tabLayout.getTabAt(PagerAdapter.TextTabIndex).getCustomView();
        TextView textView = (TextView) view.findViewById(R.id.badge);
        textView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);

        if (visible) {
            int newNumber = Integer.parseInt(textView.getText().toString()) + 1;
            textView.setText(Integer.toString(newNumber));
        } else {
            textView.setText("0");
        }
    }
}