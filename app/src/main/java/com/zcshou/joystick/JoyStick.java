package com.zcshou.joystick;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.gogogo.HistoryActivity;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.utils.GoUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class JoyStick extends View {
    private static final int DivGo = 1000;
    private static final int WINDOW_TYPE_JOYSTICK = 0;
    private static final int WINDOW_TYPE_MAP = 1;
    private static final int WINDOW_TYPE_HISTORY = 2;

    private final Context mContext;
    private WindowManager.LayoutParams mWindowParamCurrent;
    private WindowManager mWindowManager;
    private int mCurWin = WINDOW_TYPE_JOYSTICK;
    private final LayoutInflater inflater;
    private boolean isWalk;
    private ImageButton btnWalk;
    private boolean isRun;
    private ImageButton btnRun;
    private boolean isBike;
    private ImageButton btnBike;
    private JoyStickClickListener mListener;

    // 移动
    private View mJoystickLayout;
    private GoUtils.TimeCount mTimer;
    private boolean isMove;
    private double mSpeed = 1.2;
    private double mAltitude = 55.0;
    private double mAngle = 0;
    private double mR = 0;
    private double disLng = 0;
    private double disLat = 0;
    private final SharedPreferences sharedPreferences;

    // 历史记录悬浮窗
    private FrameLayout mHistoryLayout;
    private final List<Map<String, Object>> mAllRecord = new ArrayList<>();
    private TextView noRecordText;
    private ListView mRecordListView;

    // 地图悬浮窗 (OSMDroid)
    private FrameLayout mMapLayout;
    private MapView mMapView;
    private MyLocationNewOverlay mMyLocOverlay;
    private Marker mJoystickMarker;
    private GeoPoint mCurMapGeoPoint;
    private GeoPoint mMarkMapGeoPoint;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private final OkHttpClient mOkHttpClient = new OkHttpClient();

    public JoyStick(Context context) {
        super(context);
        this.mContext = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        initWindowManager();
        inflater = LayoutInflater.from(mContext);
        if (inflater != null) {
            initJoyStickView();
            initJoyStickMapView();
            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        initWindowManager();
        inflater = LayoutInflater.from(mContext);
        if (inflater != null) {
            initJoyStickView();
            initJoyStickMapView();
            initHistoryView();
        }
    }

    public JoyStick(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        initWindowManager();
        inflater = LayoutInflater.from(mContext);
        if (inflater != null) {
            initJoyStickView();
            initJoyStickMapView();
            initHistoryView();
        }
    }

    public void setCurrentPosition(double lng, double lat, double alt) {
        // OSMDroid uses WGS84 natively - no coordinate conversion needed
        mCurMapGeoPoint = new GeoPoint(lat, lng);
        mAltitude = alt;
        resetOsmMap();
    }

    public void show() {
        switch (mCurWin) {
            case WINDOW_TYPE_MAP:
                if (mJoystickLayout.getParent() != null) mWindowManager.removeView(mJoystickLayout);
                if (mHistoryLayout.getParent() != null) mWindowManager.removeView(mHistoryLayout);
                if (mMapLayout.getParent() == null) {
                    resetOsmMap();
                    mWindowManager.addView(mMapLayout, mWindowParamCurrent);
                }
                break;
            case WINDOW_TYPE_HISTORY:
                if (mMapLayout.getParent() != null) mWindowManager.removeView(mMapLayout);
                if (mJoystickLayout.getParent() != null) mWindowManager.removeView(mJoystickLayout);
                if (mHistoryLayout.getParent() == null) mWindowManager.addView(mHistoryLayout, mWindowParamCurrent);
                break;
            case WINDOW_TYPE_JOYSTICK:
                if (mMapLayout.getParent() != null) mWindowManager.removeView(mMapLayout);
                if (mHistoryLayout.getParent() != null) mWindowManager.removeView(mHistoryLayout);
                if (mJoystickLayout.getParent() == null) mWindowManager.addView(mJoystickLayout, mWindowParamCurrent);
                break;
        }
    }

    public void hide() {
        if (mMapLayout.getParent() != null) mWindowManager.removeViewImmediate(mMapLayout);
        if (mJoystickLayout.getParent() != null) mWindowManager.removeViewImmediate(mJoystickLayout);
        if (mHistoryLayout.getParent() != null) mWindowManager.removeViewImmediate(mHistoryLayout);
    }

    public void destroy() {
        if (mMapLayout.getParent() != null) mWindowManager.removeViewImmediate(mMapLayout);
        if (mJoystickLayout.getParent() != null) mWindowManager.removeViewImmediate(mJoystickLayout);
        if (mHistoryLayout.getParent() != null) mWindowManager.removeViewImmediate(mHistoryLayout);
        if (mMyLocOverlay != null) mMyLocOverlay.disableMyLocation();
        mMapView.onDetach();
    }

    public void setListener(JoyStickClickListener mListener) {
        this.mListener = mListener;
    }

    private void initWindowManager() {
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowParamCurrent = new WindowManager.LayoutParams();
        mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mWindowParamCurrent.format = PixelFormat.RGBA_8888;
        mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mWindowParamCurrent.gravity = Gravity.START | Gravity.TOP;
        mWindowParamCurrent.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParamCurrent.x = 300;
        mWindowParamCurrent.y = 300;
    }

    @SuppressLint("InflateParams")
    private void initJoyStickView() {
        mTimer = new GoUtils.TimeCount(DivGo, DivGo);
        mTimer.setListener(new GoUtils.TimeCount.TimeCountListener() {
            @Override public void onTick(long millisUntilFinished) {}
            @Override public void onFinish() {
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F - mAngle);
                mTimer.start();
            }
        });
        try {
            mSpeed = Double.parseDouble(sharedPreferences.getString("setting_walk", getResources().getString(R.string.setting_walk_default)));
        } catch (NumberFormatException e) { mSpeed = 1.2; }

        mJoystickLayout = inflater.inflate(R.layout.joystick, null);
        mJoystickLayout.setOnTouchListener(new JoyStickOnTouchListener());

        ((ImageButton) mJoystickLayout.findViewById(R.id.joystick_position)).setOnClickListener(v -> {
            if (mMapLayout.getParent() == null) { mCurWin = WINDOW_TYPE_MAP; show(); }
        });
        ((ImageButton) mJoystickLayout.findViewById(R.id.joystick_history)).setOnClickListener(v -> {
            if (mHistoryLayout.getParent() == null) { mCurWin = WINDOW_TYPE_HISTORY; show(); }
        });

        btnWalk = mJoystickLayout.findViewById(R.id.joystick_walk);
        btnWalk.setOnClickListener(v -> {
            if (!isWalk) {
                btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme())); isWalk = true;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isRun = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isBike = false;
                try { mSpeed = Double.parseDouble(sharedPreferences.getString("setting_walk", getResources().getString(R.string.setting_walk_default))); }
                catch (NumberFormatException e) { mSpeed = 1.2; }
            }
        });
        isWalk = true;
        btnWalk.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        isRun = false;
        btnRun = mJoystickLayout.findViewById(R.id.joystick_run);
        btnRun.setOnClickListener(v -> {
            if (!isRun) {
                btnRun.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme())); isRun = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isWalk = false;
                btnBike.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isBike = false;
                try { mSpeed = Double.parseDouble(sharedPreferences.getString("setting_run", getResources().getString(R.string.setting_run_default))); }
                catch (NumberFormatException e) { mSpeed = 3.6; }
            }
        });

        isBike = false;
        btnBike = mJoystickLayout.findViewById(R.id.joystick_bike);
        btnBike.setOnClickListener(v -> {
            if (!isBike) {
                btnBike.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme())); isBike = true;
                btnWalk.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isWalk = false;
                btnRun.setColorFilter(getResources().getColor(R.color.black, mContext.getTheme())); isRun = false;
                try { mSpeed = Double.parseDouble(sharedPreferences.getString("setting_bike", getResources().getString(R.string.setting_bike_default))); }
                catch (NumberFormatException e) { mSpeed = 10.0; }
            }
        });

        RockerView rckView = mJoystickLayout.findViewById(R.id.joystick_rocker);
        rckView.setListener(this::processDirection);
        ButtonView btnView = mJoystickLayout.findViewById(R.id.joystick_button);
        btnView.setListener(this::processDirection);

        if (sharedPreferences.getString("setting_joystick_type", "0").equals("0")) {
            rckView.setVisibility(VISIBLE); btnView.setVisibility(GONE);
        } else {
            rckView.setVisibility(GONE); btnView.setVisibility(VISIBLE);
        }
    }

    private void processDirection(boolean auto, double angle, double r) {
        if (r <= 0) {
            mTimer.cancel(); isMove = false;
        } else {
            mAngle = angle; mR = r;
            if (auto) {
                if (!isMove) { mTimer.start(); isMove = true; }
            } else {
                mTimer.cancel(); isMove = false;
                disLng = mSpeed * (double)(DivGo / 1000) * mR * Math.cos(mAngle * 2 * Math.PI / 360) / 1000;
                disLat = mSpeed * (double)(DivGo / 1000) * mR * Math.sin(mAngle * 2 * Math.PI / 360) / 1000;
                mListener.onMoveInfo(mSpeed, disLng, disLat, 90.0F - mAngle);
            }
        }
    }

    private class JoyStickOnTouchListener implements OnTouchListener {
        private int x, y;
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: x = (int) event.getRawX(); y = (int) event.getRawY(); break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX(), nowY = (int) event.getRawY();
                    mWindowParamCurrent.x += nowX - x; mWindowParamCurrent.y += nowY - y;
                    x = nowX; y = nowY;
                    mWindowManager.updateViewLayout(view, mWindowParamCurrent);
                    break;
                case MotionEvent.ACTION_UP: view.performClick(); break;
            }
            return false;
        }
    }

    public interface JoyStickClickListener {
        void onMoveInfo(double speed, double disLng, double disLat, double angle);
        void onPositionInfo(double lng, double lat, double alt);
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initJoyStickMapView() {
        mMapLayout = (FrameLayout) inflater.inflate(R.layout.joystick_map, null);
        mMapLayout.setOnTouchListener(new JoyStickOnTouchListener());

        mSearchList = mMapLayout.findViewById(R.id.map_search_list_view);
        mSearchLayout = mMapLayout.findViewById(R.id.map_search_linear);

        mSearchList.setOnItemClickListener((parent, view, position, id) -> {
            mSearchLayout.setVisibility(View.GONE);
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            markOsmMap(new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lng)));
        });

        TextView tips = mMapLayout.findViewById(R.id.joystick_map_tips);
        SearchView mSearchView = mMapLayout.findViewById(R.id.joystick_map_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(View.GONE);
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);
            return false;
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                if (newText != null && newText.length() > 0) performJoystickSearch(newText);
                else mSearchLayout.setVisibility(View.GONE);
                return true;
            }
        });

        ImageButton btnGo = mMapLayout.findViewById(R.id.btnGo);
        btnGo.setOnClickListener(v -> {
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent);
            tips.setVisibility(VISIBLE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            if (mMarkMapGeoPoint == null) {
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_error_location));
            } else {
                mCurMapGeoPoint = mMarkMapGeoPoint;
                mMarkMapGeoPoint = null;
                // OSMDroid uses WGS84 - pass directly, no conversion needed
                mListener.onPositionInfo(mCurMapGeoPoint.getLongitude(), mCurMapGeoPoint.getLatitude(), mAltitude);
                resetOsmMap();
                GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
            }
        });
        btnGo.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        ImageButton btnClose = mMapLayout.findViewById(R.id.map_close);
        btnClose.setOnClickListener(v -> {
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            tips.setVisibility(VISIBLE);
            mSearchLayout.setVisibility(View.GONE);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });

        ImageButton btnBack = mMapLayout.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> resetOsmMap());
        btnBack.setColorFilter(getResources().getColor(R.color.colorAccent, mContext.getTheme()));

        initOsmMap();
    }

    private void initOsmMap() {
        mMapView = mMapLayout.findViewById(R.id.map_joystick);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);

        mMyLocOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(mContext), mMapView);
        mMyLocOverlay.enableMyLocation();
        mMapView.getOverlays().add(mMyLocOverlay);

        GeoPoint startPoint = new GeoPoint(36.547743718042415, 117.07018449827267);
        mMapView.getController().setZoom(14.0);
        mMapView.getController().setCenter(startPoint);

        MapEventsReceiver eventsReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                markOsmMap(p);
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                markOsmMap(p);
                return true;
            }
        };
        mMapView.getOverlays().add(new MapEventsOverlay(eventsReceiver));
    }

    private void resetOsmMap() {
        if (mJoystickMarker != null) { mMapView.getOverlays().remove(mJoystickMarker); mJoystickMarker = null; }
        if (mCurMapGeoPoint != null) {
            mMapView.getController().animateTo(mCurMapGeoPoint);
            mMapView.getController().setZoom(18.0);
        }
        mMapView.invalidate();
    }

    private void markOsmMap(GeoPoint point) {
        mMarkMapGeoPoint = point;
        if (mJoystickMarker != null) mMapView.getOverlays().remove(mJoystickMarker);
        mJoystickMarker = new Marker(mMapView);
        mJoystickMarker.setPosition(point);
        mJoystickMarker.setIcon(getResources().getDrawable(R.drawable.icon_gcoding, mContext.getTheme()));
        mJoystickMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mJoystickMarker.setInfoWindowShown(false);
        mMapView.getOverlays().add(mJoystickMarker);
        mMapView.getController().animateTo(point);
        mMapView.getController().setZoom(18.0);
        mMapView.invalidate();
    }

    private void performJoystickSearch(String query) {
        String enc;
        try { enc = URLEncoder.encode(query, "UTF-8"); } catch (UnsupportedEncodingException e) { enc = query; }
        String url = "https://nominatim.openstreetmap.org/search?format=json&q=" + enc + "&limit=10&accept-language=zh";
        okhttp3.Request req = new okhttp3.Request.Builder().url(url).header("User-Agent", mContext.getPackageName()).build();
        mOkHttpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) return;
                String resp = body.string();
                // Post to main thread via MapView
                mMapView.post(() -> {
                    try {
                        JSONArray arr = new JSONArray(resp);
                        if (arr.length() == 0) return;
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            Map<String, Object> poi = new HashMap<>();
                            poi.put(MainActivity.POI_NAME, o.optString("name", o.optString("display_name", "")));
                            poi.put(MainActivity.POI_ADDRESS, o.optString("display_name", ""));
                            poi.put(MainActivity.POI_LONGITUDE, o.optString("lon", ""));
                            poi.put(MainActivity.POI_LATITUDE, o.optString("lat", ""));
                            data.add(poi);
                        }
                        mSearchList.setAdapter(new SimpleAdapter(mContext, data, R.layout.search_poi_item,
                                new String[]{MainActivity.POI_NAME, MainActivity.POI_ADDRESS, MainActivity.POI_LONGITUDE, MainActivity.POI_LATITUDE},
                                new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude}));
                        mSearchLayout.setVisibility(View.VISIBLE);
                    } catch (JSONException ignored) {}
                });
            }
        });
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void initHistoryView() {
        mHistoryLayout = (FrameLayout) inflater.inflate(R.layout.joystick_history, null);
        mHistoryLayout.setOnTouchListener(new JoyStickOnTouchListener());

        TextView tips = mHistoryLayout.findViewById(R.id.joystick_his_tips);
        SearchView mSearchView = mHistoryLayout.findViewById(R.id.joystick_his_searchView);
        mSearchView.setOnSearchClickListener(v -> {
            tips.setVisibility(GONE);
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);
        });
        mSearchView.setOnCloseListener(() -> {
            tips.setVisibility(VISIBLE);
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);
            return false;
        });
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    showHistory(mAllRecord);
                } else {
                    List<Map<String, Object>> searchRet = new ArrayList<>();
                    for (Map<String, Object> record : mAllRecord) {
                        if (record.toString().indexOf(newText) > 0) searchRet.add(record);
                    }
                    if (searchRet.size() > 0) showHistory(searchRet);
                    else { GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_search_null)); showHistory(mAllRecord); }
                }
                return false;
            }
        });

        noRecordText = mHistoryLayout.findViewById(R.id.joystick_his_record_no_textview);
        mRecordListView = mHistoryLayout.findViewById(R.id.joystick_his_record_list_view);
        mRecordListView.setOnItemClickListener((adapterView, view, i, l) -> {
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent);
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);

            String wgs84LatLng = (String) ((TextView) view.findViewById(R.id.WGSLatLngText)).getText();
            wgs84LatLng = wgs84LatLng.substring(wgs84LatLng.indexOf('[') + 1, wgs84LatLng.indexOf(']'));
            String[] parts = wgs84LatLng.split(" ");
            String wgs84Lng = parts[0].substring(parts[0].indexOf(':') + 1);
            String wgs84Lat = parts[1].substring(parts[1].indexOf(':') + 1);

            mListener.onPositionInfo(Double.parseDouble(wgs84Lng), Double.parseDouble(wgs84Lat), mAltitude);

            // Update current map position (WGS84)
            mCurMapGeoPoint = new GeoPoint(Double.parseDouble(wgs84Lat), Double.parseDouble(wgs84Lng));
            GoUtils.DisplayToast(mContext, getResources().getString(R.string.app_location_ok));
        });

        fetchAllRecord();
        showHistory(mAllRecord);

        ((ImageButton) mHistoryLayout.findViewById(R.id.joystick_his_close)).setOnClickListener(v -> {
            mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            mSearchView.clearFocus();
            mSearchView.onActionViewCollapsed();
            tips.setVisibility(VISIBLE);
            mCurWin = WINDOW_TYPE_JOYSTICK;
            show();
        });
    }

    private void fetchAllRecord() {
        try {
            SQLiteDatabase db = new DataBaseHistoryLocation(mContext.getApplicationContext()).getWritableDatabase();
            Cursor cursor = db.query(DataBaseHistoryLocation.TABLE_NAME, null,
                    DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null);
            while (cursor.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                int ID = cursor.getInt(0);
                String Location = cursor.getString(1);
                String Longitude = cursor.getString(2);
                String Latitude = cursor.getString(3);
                long TimeStamp = cursor.getInt(4);
                String CustomLng = cursor.getString(5);
                String CustomLat = cursor.getString(6);
                Log.d("TB", ID + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp);
                double doubleLng = new BigDecimal(Double.parseDouble(Longitude)).setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleLat = new BigDecimal(Double.parseDouble(Latitude)).setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleCustomLng = new BigDecimal(Double.parseDouble(CustomLng)).setScale(11, RoundingMode.HALF_UP).doubleValue();
                double doubleCustomLat = new BigDecimal(Double.parseDouble(CustomLat)).setScale(11, RoundingMode.HALF_UP).doubleValue();
                item.put(HistoryActivity.KEY_ID, Integer.toString(ID));
                item.put(HistoryActivity.KEY_LOCATION, Location);
                item.put(HistoryActivity.KEY_TIME, GoUtils.timeStamp2Date(Long.toString(TimeStamp)));
                item.put(HistoryActivity.KEY_LNG_LAT_WGS, "[经度:" + doubleLng + " 纬度:" + doubleLat + "]");
                item.put(HistoryActivity.KEY_LNG_LAT_CUSTOM, "[经度:" + doubleCustomLng + " 纬度:" + doubleCustomLat + "]");
                mAllRecord.add(item);
            }
            cursor.close();
            db.close();
        } catch (Exception e) {
            Log.e("JOYSTICK", "ERROR - fetchAllRecord");
        }
    }

    private void showHistory(List<Map<String, Object>> list) {
        if (list.size() == 0) {
            mRecordListView.setVisibility(View.GONE);
            noRecordText.setVisibility(View.VISIBLE);
        } else {
            noRecordText.setVisibility(View.GONE);
            mRecordListView.setVisibility(View.VISIBLE);
            try {
                mRecordListView.setAdapter(new SimpleAdapter(mContext, list, R.layout.history_item,
                        new String[]{HistoryActivity.KEY_ID, HistoryActivity.KEY_LOCATION, HistoryActivity.KEY_TIME, HistoryActivity.KEY_LNG_LAT_WGS, HistoryActivity.KEY_LNG_LAT_CUSTOM},
                        new int[]{R.id.LocationID, R.id.LocationText, R.id.TimeText, R.id.WGSLatLngText, R.id.BDLatLngText}));
            } catch (Exception e) {
                Log.e("JOYSTICK", "ERROR - showHistory");
            }
        }
    }
}
