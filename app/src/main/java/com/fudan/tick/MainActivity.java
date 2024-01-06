package com.fudan.tick;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.fudan.tick.activity.InfoActivity;
import com.fudan.tick.activity.ScheduleActivity;
import com.google.android.material.navigation.NavigationView;
import com.fudan.tick.activity.SettingActivity;
import com.fudan.tick.util.TimeFormatUtil;
import com.fudan.tick.widget.RippleWrapper;
import com.fudan.tick.widget.TickProgressBar;
import com.google.android.material.snackbar.Snackbar;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private TickApplication mApplication;
    private DrawerLayout mDrawerLayout;
    private MenuItem mMenuItemIDLE;
    private Button mBtnStart;
    private Button mBtnPause;
    private Button mBtnResume;
    private Button mBtnStop;
    private Button mBtnSkip;
    private TextView mTextCountDown;
    private TextView mTextTimeTile;
    private TickProgressBar mProgressBar;
    private RippleWrapper mRippleWrapper;
    public String lon;
    public String lat;
    public String description;
    private long mLastClickTime = 0;
    private PopupWindow popupMenu;
    private String weather;
    private ConnectivityManager mConnectivityManager = null;
    private NetworkInfo mActiveNetInfo = null;

    public static Intent newIntent(Context context) {
        return new Intent(context, MainActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLocation();
        getWeatherData();
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu();
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);

                if (mMenuItemIDLE != null && newState == DrawerLayout.STATE_IDLE) {
                    runNavigationItemSelected(mMenuItemIDLE);
                    mMenuItemIDLE = null;
                }
            }
        };

        mDrawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mApplication = (TickApplication) getApplication();

        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnPause = (Button) findViewById(R.id.btn_pause);
        mBtnResume = (Button) findViewById(R.id.btn_resume);
        mBtnStop = (Button) findViewById(R.id.btn_stop);
        mBtnSkip = (Button) findViewById(R.id.btn_skip);
        mTextCountDown = (TextView) findViewById(R.id.text_count_down);
        mTextTimeTile = (TextView) findViewById(R.id.text_time_title);
        mProgressBar = (TickProgressBar) findViewById(R.id.tick_progress_bar);
        mRippleWrapper = (RippleWrapper) findViewById(R.id.ripple_wrapper);
        initActions();
    }

    private void initActions() {
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = TickService.newIntent(getApplicationContext());
                i.setAction(TickService.ACTION_START);
                startService(i);
                mApplication.start();
                updateButtons();
                updateTitle();
                updateRipple();
            }
        });

        mBtnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = TickService.newIntent(getApplicationContext());
                i.setAction(TickService.ACTION_PAUSE);
                i.putExtra("time_left", (String) mTextCountDown.getText());
                startService(i);
                mApplication.pause();
                updateButtons();
                updateRipple();
            }
        });

        mBtnResume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = TickService.newIntent(getApplicationContext());
                i.setAction(TickService.ACTION_RESUME);
                startService(i);
                mApplication.resume();
                updateButtons();
                updateRipple();
            }
        });

        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = TickService.newIntent(getApplicationContext());
                i.setAction(TickService.ACTION_STOP);
                startService(i);
                mApplication.stop();
                reload();
            }
        });

        mBtnSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = TickService.newIntent(getApplicationContext());
                i.setAction(TickService.ACTION_STOP);
                startService(i);

                mApplication.skip();
                reload();
            }
        });

        mRippleWrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                long clickTime = System.currentTimeMillis();
                if (clickTime - mLastClickTime < 500) {
                    boolean isSoundOn = getSharedPreferences()
                            .getBoolean("pref_key_tick_sound", true);

                    // 修改 SharedPreferences
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(getApplicationContext()).edit();

                    if (isSoundOn) {
                        editor.putBoolean("pref_key_tick_sound", false);

                        Intent i = TickService.newIntent(getApplicationContext());
                        i.setAction(TickService.ACTION_TICK_SOUND_OFF);
                        startService(i);

                        Snackbar.make(view, getResources().getString(R.string.toast_tick_sound_off),
                                Snackbar.LENGTH_SHORT).setAction("Action", null).show();
                    } else {
                        editor.putBoolean("pref_key_tick_sound", true);

                        Intent i = TickService.newIntent(getApplicationContext());
                        i.setAction(TickService.ACTION_TICK_SOUND_ON);
                        startService(i);

                        Snackbar.make(view, getResources().getString(R.string.toast_tick_sound_on),
                                Snackbar.LENGTH_SHORT).setAction("Action", null).show();
                    }
                    try {
                        editor.apply();
                    } catch (AbstractMethodError unused) {
                        editor.commit();
                    }

                    updateRipple();
                }

                mLastClickTime = clickTime;
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mMenuItemIDLE = item;
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }

        return true;
    }

    /**
     * DrawerLayout 关闭卡顿的综合解决方法
     *
     * @link https://stackoverflow.com/questions/18343018/optimizing-drawer-and-activity-launching-speed
     */
    private void runNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_setting) {
            startActivity(SettingActivity.newIntent(this));
        } else if (id == R.id.nav_schedule) {
            startActivity(ScheduleActivity.newIntent(this));
        } else if (id == R.id.nav_info) {
            startActivity(InfoActivity.newIntent(this));
        } else if (id == R.id.nav_exit) {
            exitApp();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TickService.ACTION_COUNTDOWN_TIMER);
        registerReceiver(mIntentReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void reload() {
        mApplication.reload();
        mProgressBar.setMaxProgress(mApplication.getMillisInTotal() / 1000);
        mProgressBar.setProgress(mApplication.getMillisUntilFinished() / 1000);
        updateText(mApplication.getMillisUntilFinished());
        updateTitle();
        updateButtons();
        updateScene();
        updateRipple();
        updateAmount();
        if (getSharedPreferences().getBoolean("pref_key_screen_on", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void updateText(long millisUntilFinished) {
        mTextCountDown.setText(TimeFormatUtil.formatTime(millisUntilFinished));
    }

    private void updateTitle() {
        if (mApplication.getState() == TickApplication.STATE_FINISH) {
            String title;

            if (mApplication.getScene() == TickApplication.SCENE_WORK) {
                title = getResources().getString(R.string.scene_title_work);
            } else {
                title = getResources().getString(R.string.scene_title_break);
            }

            mTextTimeTile.setText(title);
            mTextTimeTile.setVisibility(View.VISIBLE);
            mTextCountDown.setVisibility(View.GONE);
        } else {
            mTextTimeTile.setVisibility(View.GONE);
            mTextCountDown.setVisibility(View.VISIBLE);
        }
    }

    private void updateButtons() {
        int state = mApplication.getState();
        int scene = mApplication.getScene();
        boolean isPomodoroMode = getSharedPreferences()
                .getBoolean("pref_key_pomodoro_mode", true);

        // 在番茄模式下不能暂停定时器
        mBtnStart.setVisibility(
                state == TickApplication.STATE_WAIT || state == TickApplication.STATE_FINISH ?
                        View.VISIBLE : View.GONE);
        if (isPomodoroMode) {
            mBtnPause.setVisibility(View.GONE);
            mBtnResume.setVisibility(View.GONE);
        } else {
            mBtnPause.setVisibility(state == TickApplication.STATE_RUNNING ?
                    View.VISIBLE : View.GONE);
            mBtnResume.setVisibility(state == TickApplication.STATE_PAUSE ?
                    View.VISIBLE : View.GONE);
        }

        if (scene == TickApplication.SCENE_WORK) {
            mBtnSkip.setVisibility(View.GONE);
            if (isPomodoroMode) {
                mBtnStop.setVisibility(!(state == TickApplication.STATE_WAIT ||
                        state == TickApplication.STATE_FINISH) ?
                        View.VISIBLE : View.GONE);
            } else {
                mBtnStop.setVisibility(state == TickApplication.STATE_PAUSE ?
                        View.VISIBLE : View.GONE);
            }

        } else {
            mBtnStop.setVisibility(View.GONE);
            if (isPomodoroMode) {
                mBtnSkip.setVisibility(!(state == TickApplication.STATE_WAIT ||
                        state == TickApplication.STATE_FINISH) ?
                        View.VISIBLE : View.GONE);
            } else {
                mBtnSkip.setVisibility(state == TickApplication.STATE_PAUSE ?
                        View.VISIBLE : View.GONE);
            }

        }
    }

    public void updateScene() {
        int scene = mApplication.getScene();

        int workLength = getSharedPreferences()
                .getInt("pref_key_work_length", TickApplication.DEFAULT_WORK_LENGTH);
        int shortBreak = getSharedPreferences()
                .getInt("pref_key_short_break", TickApplication.DEFAULT_SHORT_BREAK);
        int longBreak = getSharedPreferences()
                .getInt("pref_key_long_break", TickApplication.DEFAULT_LONG_BREAK);

        ((TextView) findViewById(R.id.stage_work_value))
                .setText(getResources().getString(R.string.stage_time_unit, workLength));
        ((TextView) findViewById(R.id.stage_short_break_value))
                .setText(getResources().getString(R.string.stage_time_unit, shortBreak));
        ((TextView) findViewById(R.id.stage_long_break_value))
                .setText(getResources().getString(R.string.stage_time_unit, longBreak));

        findViewById(R.id.stage_work).setAlpha(
                scene == TickApplication.SCENE_WORK ? 0.9f : 0.5f);
        findViewById(R.id.stage_short_break).setAlpha(
                scene == TickApplication.SCENE_SHORT_BREAK ? 0.9f : 0.5f);
        findViewById(R.id.stage_long_break).setAlpha(
                scene == TickApplication.SCENE_LONG_BREAK ? 0.9f : 0.5f);
    }

    private void updateRipple() {
        boolean isPlayOn = getSharedPreferences().getBoolean("pref_key_tick_sound", true);
        if (isPlayOn) {
            if (mApplication.getState() == TickApplication.STATE_RUNNING) {
                mRippleWrapper.start();
                return;
            }
        }
        mRippleWrapper.stop();
    }

    private void updateAmount() {
        long amount = getSharedPreferences().getLong("pref_key_amount_durations", 0);
        TextView textView = (TextView) findViewById(R.id.amount_durations);
        textView.setText(getResources().getString(R.string.amount_durations, amount));
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(TickService.ACTION_COUNTDOWN_TIMER)) {
                String requestAction = intent.getStringExtra(TickService.REQUEST_ACTION);
                switch (requestAction) {
                    case TickService.ACTION_TICK:
                        long millisUntilFinished = intent.getLongExtra(
                                TickService.MILLIS_UNTIL_FINISHED, 0);
                        mProgressBar.setProgress(millisUntilFinished / 1000);
                        updateText(millisUntilFinished);
                        break;
                    case TickService.ACTION_FINISH:
                    case TickService.ACTION_AUTO_START:
                        reload();
                        break;
                }
            }
        }
    };

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }

    private void exitApp() {
        stopService(TickService.newIntent(getApplicationContext()));
        mApplication.exit();
        finish();
    }


    public void getWeatherData() {
        //创建一个请求队列
        RequestQueue requestQueue = Volley.newRequestQueue(MainActivity.this);
        //创建一个请求
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=6ca98e3926672af362ab94f8caaefa08";//请求地址
        StringRequest stringRequest = new StringRequest(url, new com.android.volley.Response.Listener<String>() {
            //正确接受数据之后的回调
            @Override
            public void onResponse(String response) {
                analyzeWeatherJSON(response);
            }
        }, new com.android.volley.Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("error", "volley error");
            }
        });
        //将创建的请求添加到请求队列当中
        requestQueue.add(stringRequest);
    }

    public void analyzeWeatherJSON(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray weatherArray = jsonObject.getJSONArray("weather");
            JSONObject weatherObject = weatherArray.getJSONObject(0);
            weather = weatherObject.getString("main");
            showWeatherNotification(weather);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public String getLocation() {
        LocationListener locationListener = new LocationListener() {
            // Provider的状态在可用、暂时不可用和无服务三个状态直接切换时触发此函数
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            // Provider被enable时触发此函数，比如GPS被打开
            @Override
            public void onProviderEnabled(String provider) {
            }

            // Provider被disable时触发此函数，比如GPS被关闭
            @Override
            public void onProviderDisabled(String provider) {
            }

            // 当坐标改变时触发此函数，如果Provider传进相同的坐标，它就不会被触发
            @Override
            public void onLocationChanged(Location location) {
//                Log.d("location", "经纬度：" + location.getLatitude() + "，" + location.getLongitude());
            }
        };
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        Location location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        while (location == null) {
//            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 1, locationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 1, locationListener);
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (location != null) {
            // 获取经纬度
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            // 使用地理编码器获取城市信息
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses.size() > 0) {
                    // 获取城市信息
                    String city = addresses.get(0).getLocality();
                    lat = String.valueOf(addresses.get(0).getLatitude());
                    lon = String.valueOf(addresses.get(0).getLongitude());
                    return city;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Toast.makeText(this, "无法获取位置信息", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    public void showWeatherNotification(String weather) {

        // 创建通知渠道（仅适用于Android 8.0及以上版本）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("weather_channel", "Weather Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        switch(weather){
            case "clear":
                weather = "晴朗 ";
                description = "晴空万里，心情也格外晴朗，愿你拥有美好的一天！";
                break;
            case "Rain":
                weather = "雨 ";
                description = "雨水洗净尘埃，带来生机和美丽，愿你的一天充满好运！";
                break;
            case "Clouds":
                weather = "阴";
                description = "相信阳光很快就会再次出现。";
                break;
            default:
                weather = "";
                description = "愿你拥有美好的一天！";
                break;
        }

        // 创建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "weather_channel")
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_logo))
                .setContentTitle("天气" + weather)
                .setContentText(description )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // 显示通知
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(1, builder.build());
    }

}
