package com.zcshou.gogogo;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.zcshou.database.DataBaseHistoryLocation;
import com.zcshou.database.DataBaseHistorySearch;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.GoUtils;
import com.zcshou.utils.ShareUtils;

import com.elvishew.xlog.XLog;

import io.noties.markwon.Markwon;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends BaseActivity {

    public static final String LAT_MSG_ID = "LAT_VALUE";
    public static final String LNG_MSG_ID = "LNG_VALUE";
    public static final String ALT_MSG_ID = "ALT_VALUE";
    public static final String POI_NAME = "POI_NAME";
    public static final String POI_ADDRESS = "POI_ADDRESS";
    public static final String POI_LONGITUDE = "POI_LONGITUDE";
    public static final String POI_LATITUDE = "POI_LATITUDE";

    private static final OnlineTileSourceBase ESRI_SATELLITE = new OnlineTileSourceBase(
            "ESRI Satellite", 2, 20, 256, ".jpg",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
        @Override
        public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex) + "/"
                    + MapTileIndex.getY(pMapTileIndex) + "/"
                    + MapTileIndex.getX(pMapTileIndex)
                    + mImageFilenameEnding;
        }
    };

    private OkHttpClient mOkHttpClient;
    private SharedPreferences sharedPreferences;

    // Map (OSMDroid)
    private MapView mMapView;
    private static MapView sMapView;
    private MyLocationNewOverlay mMyLocationOverlay;
    private static Marker sCurrentMarker;
    private static GeoPoint mMarkGeoPoint = new GeoPoint(36.547743718042415, 117.07018449827267);
    private static String mMarkName = null;

    // Location
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private double mCurrentLat = 0.0;
    private double mCurrentLon = 0.0;
    public static String mCurrentCity = null;
    private boolean isFirstLoc = true;

    // Mock service
    private boolean isMockServStart = false;
    private ServiceGo.ServiceGoBinder mServiceBinder;
    private ServiceConnection mConnection;
    private FloatingActionButton mButtonStart;

    // History DB
    private SQLiteDatabase mLocationHistoryDB;
    private SQLiteDatabase mSearchHistoryDB;

    // Search UI
    private SearchView searchView;
    private ListView mSearchList;
    private LinearLayout mSearchLayout;
    private ListView mSearchHistoryList;
    private LinearLayout mHistoryLayout;
    private MenuItem searchItem;

    // Update
    private DownloadManager mDownloadManager;
    private long mDownloadId;
    private BroadcastReceiver mDownloadBdRcv;
    private String mUpdateFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        XLog.i("MainActivity: onCreate");
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mOkHttpClient = new OkHttpClient();

        initNavigationView();
        initMap();
        initMapLocation();
        initMapButton();
        initGoBtn();

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceBinder = (ServiceGo.ServiceGoBinder) service;
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        initStoreHistory();
        initSearchView();
        initUpdateVersion();
        checkUpdateVersion(false);
    }

    @Override
    protected void onResume() {
        XLog.i("MainActivity: onResume");
        super.onResume();
        mMapView.onResume();
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        XLog.i("MainActivity: onPause");
        mMapView.onPause();
        stopLocationUpdates();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        XLog.i("MainActivity: onDestroy");
        if (isMockServStart) {
            unbindService(mConnection);
            stopService(new Intent(this, ServiceGo.class));
        }
        unregisterReceiver(mDownloadBdRcv);
        mMyLocationOverlay.disableMyLocation();
        mMapView.onDetach();
        mLocationHistoryDB.close();
        mSearchHistoryDB.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        searchItem = menu.findItem(R.id.action_search);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                mHistoryLayout.setVisibility(View.INVISIBLE);
                return true;
            }
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mSearchLayout.setVisibility(View.INVISIBLE);
                List<Map<String, Object>> data = getSearchHistory();
                if (!data.isEmpty()) {
                    mSearchHistoryList.setAdapter(new SimpleAdapter(
                            MainActivity.this, data, R.layout.search_item,
                            new String[]{DataBaseHistorySearch.DB_COLUMN_KEY, DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                    DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                    DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                            new int[]{R.id.search_key, R.id.search_description, R.id.search_timestamp,
                                    R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude}));
                    mHistoryLayout.setVisibility(View.VISIBLE);
                }
                return true;
            }
        });

        searchView = (SearchView) searchItem.getActionView();
        searchView.setIconified(false);
        searchView.onActionViewExpanded();
        searchView.setIconifiedByDefault(true);
        searchView.setSubmitButtonEnabled(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                ContentValues cv = new ContentValues();
                cv.put(DataBaseHistorySearch.DB_COLUMN_KEY, query);
                cv.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, "搜索关键字");
                cv.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_KEY);
                cv.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
                DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, cv);
                mSearchLayout.setVisibility(View.INVISIBLE);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                mHistoryLayout.setVisibility(View.INVISIBLE);
                if (newText != null && !newText.isEmpty()) performSearch(newText);
                return true;
            }
        });

        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setOnClickListener(v -> {
            EditText et = findViewById(androidx.appcompat.R.id.search_src_text);
            et.setText("");
            searchView.setQuery("", false);
            mSearchLayout.setVisibility(View.INVISIBLE);
            mHistoryLayout.setVisibility(View.VISIBLE);
        });
        return true;
    }

    private void initNavigationView() {
        NavigationView nav = findViewById(R.id.nav_view);
        nav.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_dev) {
                if (!GoUtils.isDeveloperOptionsEnabled(this)) {
                    GoUtils.DisplayToast(this, getString(R.string.app_error_dev));
                } else {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
                    catch (Exception e) { GoUtils.DisplayToast(this, getString(R.string.app_error_dev)); }
                }
            } else if (id == R.id.nav_update) {
                checkUpdateVersion(true);
            } else if (id == R.id.nav_feedback) {
                ShareUtils.shareFile(this, new File(getExternalFilesDir("Logs"), GoApplication.LOG_FILE_NAME), item.getTitle().toString());
            } else if (id == R.id.nav_contact) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://gitee.com/itexp/gogogo/issues")));
            }
            ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
            return true;
        });
        View headerView = nav.getHeaderView(0);
        ((TextView) headerView.findViewById(R.id.app_version)).setText(GoUtils.getVersionName(this));
    }

    private void initMap() {
        mMapView = findViewById(R.id.osmMapView);
        sMapView = mMapView;
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.setBuiltInZoomControls(false);

        mMyLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mMapView);
        mMyLocationOverlay.enableMyLocation();
        mMyLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
            GeoPoint loc = mMyLocationOverlay.getMyLocation();
            if (loc != null && isFirstLoc) {
                isFirstLoc = false;
                mCurrentLat = loc.getLatitude();
                mCurrentLon = loc.getLongitude();
                mMarkGeoPoint = loc;
                mMapView.getController().animateTo(loc);
                mMapView.getController().setZoom(18.0);
                fetchCurrentCity(mCurrentLat, mCurrentLon);
            }
        }));
        mMapView.getOverlays().add(mMyLocationOverlay);
        mMapView.getController().setZoom(14.0);
        mMapView.getController().setCenter(mMarkGeoPoint);

        MapEventsReceiver eventsReceiver = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                mMarkGeoPoint = p;
                mMarkName = null;
                markMap();
                return true;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                mMarkGeoPoint = p;
                markMap();
                doReverseGeocode(p);
                return true;
            }
        };
        mMapView.getOverlays().add(new MapEventsOverlay(eventsReceiver));
    }

    @SuppressLint("MissingPermission")
    private void initMapLocation() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                android.location.Location loc = result.getLastLocation();
                if (loc == null) return;
                mCurrentLat = loc.getLatitude();
                mCurrentLon = loc.getLongitude();
                if (isFirstLoc) {
                    isFirstLoc = false;
                    GeoPoint p = new GeoPoint(mCurrentLat, mCurrentLon);
                    mMarkGeoPoint = p;
                    mMapView.getController().animateTo(p);
                    mMapView.getController().setZoom(18.0);
                    fetchCurrentCity(mCurrentLat, mCurrentLon);
                }
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (mFusedLocationClient == null) return;
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500).build();
        mFusedLocationClient.requestLocationUpdates(req, mLocationCallback, getMainLooper());
    }

    private void stopLocationUpdates() {
        if (mFusedLocationClient != null && mLocationCallback != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    private void fetchCurrentCity(double lat, double lon) {
        new Thread(() -> {
            try {
                List<Address> list = new Geocoder(this, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) mCurrentCity = list.get(0).getLocality();
            } catch (IOException ignored) {}
        }).start();
    }

    private void initMapButton() {
        RadioGroup group = findViewById(R.id.RadioGroupMapType);
        group.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.mapNormal) mMapView.setTileSource(TileSourceFactory.MAPNIK);
            else if (id == R.id.mapSatellite) mMapView.setTileSource(ESRI_SATELLITE);
            mMapView.invalidate();
        });

        ((ImageButton) findViewById(R.id.cur_position)).setOnClickListener(v -> resetMap());
        ((ImageButton) findViewById(R.id.zoom_in)).setOnClickListener(v -> mMapView.getController().zoomIn());
        ((ImageButton) findViewById(R.id.zoom_out)).setOnClickListener(v -> mMapView.getController().zoomOut());

        ((ImageButton) findViewById(R.id.input_pos)).setOnClickListener(v -> {
            View dlgView = LayoutInflater.from(this).inflate(R.layout.location_input, null);
            AlertDialog dlg = new AlertDialog.Builder(this).setTitle("请输入经度和纬度").setView(dlgView).show();
            EditText eLng = dlgView.findViewById(R.id.joystick_longitude);
            EditText eLat = dlgView.findViewById(R.id.joystick_latitude);
            dlgView.findViewById(R.id.input_position_ok).setOnClickListener(v2 -> {
                String ls = eLng.getText().toString(), as2 = eLat.getText().toString();
                if (TextUtils.isEmpty(ls) || TextUtils.isEmpty(as2)) {
                    GoUtils.DisplayToast(this, getString(R.string.app_error_input));
                } else {
                    double lng = Double.parseDouble(ls), lat = Double.parseDouble(as2);
                    if (lng > 180 || lng < -180) GoUtils.DisplayToast(this, getString(R.string.app_error_longitude));
                    else if (lat > 90 || lat < -90) GoUtils.DisplayToast(this, getString(R.string.app_error_latitude));
                    else {
                        mMarkGeoPoint = new GeoPoint(lat, lng);
                        mMarkName = "手动输入的坐标";
                        markMap();
                        mMapView.getController().animateTo(mMarkGeoPoint);
                        dlg.dismiss();
                    }
                }
            });
            dlgView.findViewById(R.id.input_position_cancel).setOnClickListener(v1 -> dlg.dismiss());
        });
    }

    private void markMap() {
        if (mMarkGeoPoint == null) return;
        if (sCurrentMarker != null) mMapView.getOverlays().remove(sCurrentMarker);
        sCurrentMarker = new Marker(mMapView);
        sCurrentMarker.setPosition(mMarkGeoPoint);
        sCurrentMarker.setIcon(getResources().getDrawable(R.drawable.icon_gcoding, getTheme()));
        sCurrentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        sCurrentMarker.setTitle(mMarkName != null ? mMarkName : "");
        sCurrentMarker.setInfoWindowShown(false);
        mMapView.getOverlays().add(sCurrentMarker);
        mMapView.invalidate();
    }

    private void doReverseGeocode(GeoPoint p) {
        new Thread(() -> {
            try {
                List<Address> list = new Geocoder(this, Locale.getDefault())
                        .getFromLocation(p.getLatitude(), p.getLongitude(), 1);
                String addr = (list != null && !list.isEmpty()) ? list.get(0).getAddressLine(0) : "";
                mMarkName = addr;
                runOnUiThread(() -> showPoiDialog(p, addr));
            } catch (IOException e) {
                runOnUiThread(() -> showPoiDialog(p, ""));
            }
        }).start();
    }

    private void showPoiDialog(GeoPoint p, String address) {
        View v = LayoutInflater.from(this).inflate(R.layout.location_poi_info, null);
        ((TextView) v.findViewById(R.id.poi_address)).setText(address);
        ((TextView) v.findViewById(R.id.poi_longitude)).setText(String.valueOf(p.getLongitude()));
        ((TextView) v.findViewById(R.id.poi_latitude)).setText(String.valueOf(p.getLatitude()));
        AlertDialog dlg = new AlertDialog.Builder(this).setView(v).create();
        v.findViewById(R.id.poi_save).setOnClickListener(b -> {
            recordCurrentLocation(p.getLongitude(), p.getLatitude());
            GoUtils.DisplayToast(this, getString(R.string.app_location_save));
        });
        v.findViewById(R.id.poi_copy).setOnClickListener(b -> {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("Label", p.getLongitude() + "," + p.getLatitude()));
            GoUtils.DisplayToast(this, getString(R.string.app_location_copy));
        });
        v.findViewById(R.id.poi_share).setOnClickListener(b ->
                ShareUtils.shareText(this, "分享位置", p.getLongitude() + "," + p.getLatitude()));
        v.findViewById(R.id.poi_fly).setOnClickListener(b -> { dlg.dismiss(); doGoLocation(b); });
        dlg.show();
    }

    private void resetMap() {
        if (sCurrentMarker != null) { mMapView.getOverlays().remove(sCurrentMarker); sCurrentMarker = null; }
        mMarkGeoPoint = null;
        if (mCurrentLat != 0.0 || mCurrentLon != 0.0) {
            GeoPoint p = new GeoPoint(mCurrentLat, mCurrentLon);
            mMapView.getController().animateTo(p);
            mMapView.getController().setZoom(18.0);
        }
        mMapView.invalidate();
    }

    public static boolean showLocation(String name, String longitude, String latitude) {
        if (sMapView == null) return false;
        try {
            if (!longitude.isEmpty() && !latitude.isEmpty()) {
                mMarkName = name;
                mMarkGeoPoint = new GeoPoint(Double.parseDouble(latitude), Double.parseDouble(longitude));
                sMapView.post(() -> {
                    if (sCurrentMarker != null) sMapView.getOverlays().remove(sCurrentMarker);
                    sCurrentMarker = new Marker(sMapView);
                    sCurrentMarker.setPosition(mMarkGeoPoint);
                    sCurrentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    sCurrentMarker.setTitle(name != null ? name : "");
                    sCurrentMarker.setInfoWindowShown(false);
                    sMapView.getOverlays().add(sCurrentMarker);
                    sMapView.getController().animateTo(mMarkGeoPoint);
                    sMapView.getController().setZoom(18.0);
                    sMapView.invalidate();
                });
            }
            return true;
        } catch (Exception e) { XLog.e("ERROR: showLocation"); return false; }
    }

    private void initGoBtn() {
        mButtonStart = findViewById(R.id.faBtnStart);
        mButtonStart.setOnClickListener(this::doGoLocation);
    }

    private void startGoLocation() {
        Intent intent = new Intent(this, ServiceGo.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
        intent.putExtra(LNG_MSG_ID, mMarkGeoPoint.getLongitude());
        intent.putExtra(LAT_MSG_ID, mMarkGeoPoint.getLatitude());
        intent.putExtra(ALT_MSG_ID, Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0")));
        startForegroundService(intent);
        isMockServStart = true;
    }

    private void stopGoLocation() {
        unbindService(mConnection);
        stopService(new Intent(this, ServiceGo.class));
        isMockServStart = false;
    }

    private void doGoLocation(View v) {
        if (!GoUtils.isNetworkAvailable(this)) { GoUtils.DisplayToast(this, getString(R.string.app_error_network)); return; }
        if (!GoUtils.isGpsOpened(this)) { GoUtils.showEnableGpsDialog(this); return; }
        if (!Settings.canDrawOverlays(getApplicationContext())) { GoUtils.showEnableFloatWindowDialog(this); return; }

        if (isMockServStart) {
            if (mMarkGeoPoint == null) {
                stopGoLocation();
                Snackbar.make(v, "模拟位置已终止", Snackbar.LENGTH_LONG).show();
                mButtonStart.setImageResource(R.drawable.ic_position);
            } else {
                mServiceBinder.setPosition(mMarkGeoPoint.getLongitude(), mMarkGeoPoint.getLatitude(),
                        Double.parseDouble(sharedPreferences.getString("setting_altitude", "55.0")));
                Snackbar.make(v, "已传送到新位置", Snackbar.LENGTH_LONG).show();
                recordCurrentLocation(mMarkGeoPoint.getLongitude(), mMarkGeoPoint.getLatitude());
                if (sCurrentMarker != null) { mMapView.getOverlays().remove(sCurrentMarker); sCurrentMarker = null; }
                mMarkGeoPoint = null;
                mMapView.invalidate();
                if (GoUtils.isWifiEnabled(this)) GoUtils.showDisableWifiDialog(this);
            }
        } else {
            if (!GoUtils.isAllowMockLocation(this)) {
                GoUtils.showEnableMockLocationDialog(this);
            } else if (mMarkGeoPoint == null) {
                Snackbar.make(v, "请先点击地图位置或者搜索位置", Snackbar.LENGTH_LONG).show();
            } else {
                startGoLocation();
                mButtonStart.setImageResource(R.drawable.ic_fly);
                Snackbar.make(v, "模拟位置已启动", Snackbar.LENGTH_LONG).show();
                recordCurrentLocation(mMarkGeoPoint.getLongitude(), mMarkGeoPoint.getLatitude());
                if (sCurrentMarker != null) { mMapView.getOverlays().remove(sCurrentMarker); sCurrentMarker = null; }
                mMarkGeoPoint = null;
                mMapView.invalidate();
                if (GoUtils.isWifiEnabled(this)) GoUtils.showDisableWifiDialog(this);
            }
        }
    }

    private void initStoreHistory() {
        try {
            mLocationHistoryDB = new DataBaseHistoryLocation(getApplicationContext()).getWritableDatabase();
            mSearchHistoryDB = new DataBaseHistorySearch(getApplicationContext()).getWritableDatabase();
        } catch (Exception e) { XLog.e("ERROR: sqlite init error"); }
    }

    private List<Map<String, Object>> getSearchHistory() {
        List<Map<String, Object>> data = new ArrayList<>();
        try {
            Cursor c = mSearchHistoryDB.query(DataBaseHistorySearch.TABLE_NAME, null,
                    DataBaseHistorySearch.DB_COLUMN_ID + " > ?", new String[]{"0"},
                    null, null, DataBaseHistorySearch.DB_COLUMN_TIMESTAMP + " DESC", null);
            while (c.moveToNext()) {
                Map<String, Object> item = new HashMap<>();
                item.put(DataBaseHistorySearch.DB_COLUMN_KEY, c.getString(1));
                item.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, c.getString(2));
                item.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, "" + c.getInt(3));
                item.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, "" + c.getInt(4));
                item.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, c.getString(7));
                item.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, c.getString(8));
                data.add(item);
            }
            c.close();
        } catch (Exception e) { XLog.e("ERROR: getSearchHistory"); }
        return data;
    }

    private void recordCurrentLocation(double lng, double lat) {
        String url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lng + "&accept-language=zh";
        okhttp3.Request req = new okhttp3.Request.Builder().url(url).header("User-Agent", getPackageName()).build();
        mOkHttpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                saveLocationHistory(mMarkName, lng, lat);
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                String name = mMarkName;
                if (body != null) {
                    try { name = new JSONObject(body.string()).optString("display_name", mMarkName); }
                    catch (JSONException ignored) {}
                }
                saveLocationHistory(name, lng, lat);
            }
        });
    }

    private void saveLocationHistory(String name, double lng, double lat) {
        ContentValues cv = new ContentValues();
        cv.put(DataBaseHistoryLocation.DB_COLUMN_LOCATION, name != null ? name : getString(R.string.history_location_default_name));
        cv.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_WGS84, String.valueOf(lng));
        cv.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_WGS84, String.valueOf(lat));
        cv.put(DataBaseHistoryLocation.DB_COLUMN_LONGITUDE_CUSTOM, String.valueOf(lng));
        cv.put(DataBaseHistoryLocation.DB_COLUMN_LATITUDE_CUSTOM, String.valueOf(lat));
        cv.put(DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
        DataBaseHistoryLocation.saveHistoryLocation(mLocationHistoryDB, cv);
    }

    private void initSearchView() {
        mSearchLayout = findViewById(R.id.search_linear);
        mHistoryLayout = findViewById(R.id.search_history_linear);
        mSearchList = findViewById(R.id.search_list_view);
        mSearchList.setOnItemClickListener((parent, view, pos, id) -> {
            String lng = ((TextView) view.findViewById(R.id.poi_longitude)).getText().toString();
            String lat = ((TextView) view.findViewById(R.id.poi_latitude)).getText().toString();
            mMarkName = ((TextView) view.findViewById(R.id.poi_name)).getText().toString();
            mMarkGeoPoint = new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lng));
            mMapView.getController().animateTo(mMarkGeoPoint);
            markMap();
            ContentValues cv = new ContentValues();
            cv.put(DataBaseHistorySearch.DB_COLUMN_KEY, mMarkName);
            cv.put(DataBaseHistorySearch.DB_COLUMN_DESCRIPTION, ((TextView) view.findViewById(R.id.poi_address)).getText().toString());
            cv.put(DataBaseHistorySearch.DB_COLUMN_IS_LOCATION, DataBaseHistorySearch.DB_SEARCH_TYPE_RESULT);
            cv.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, lng);
            cv.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM, lat);
            cv.put(DataBaseHistorySearch.DB_COLUMN_LONGITUDE_WGS84, lng);
            cv.put(DataBaseHistorySearch.DB_COLUMN_LATITUDE_WGS84, lat);
            cv.put(DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, System.currentTimeMillis() / 1000);
            DataBaseHistorySearch.saveHistorySearch(mSearchHistoryDB, cv);
            mSearchLayout.setVisibility(View.INVISIBLE);
            searchItem.collapseActionView();
        });

        mSearchHistoryList = findViewById(R.id.search_history_list_view);
        mSearchHistoryList.setOnItemClickListener((parent, view, pos, id) -> {
            String isLoc = ((TextView) view.findViewById(R.id.search_isLoc)).getText().toString();
            if ("1".equals(isLoc)) {
                String lng = ((TextView) view.findViewById(R.id.search_longitude)).getText().toString();
                String lat = ((TextView) view.findViewById(R.id.search_latitude)).getText().toString();
                mMarkGeoPoint = new GeoPoint(Double.parseDouble(lat), Double.parseDouble(lng));
                mMapView.getController().animateTo(mMarkGeoPoint);
                markMap();
                mHistoryLayout.setVisibility(View.INVISIBLE);
                searchItem.collapseActionView();
            } else if ("0".equals(isLoc)) {
                try { searchView.setQuery(((TextView) view.findViewById(R.id.search_key)).getText(), true); }
                catch (Exception e) { GoUtils.DisplayToast(this, getString(R.string.app_error_search)); }
            }
        });

        mSearchHistoryList.setOnItemLongClickListener((parent, view, pos, id) -> {
            String key = ((TextView) view.findViewById(R.id.search_key)).getText().toString();
            new AlertDialog.Builder(this).setTitle("警告").setMessage("确定要删除该项搜索记录吗?")
                    .setPositiveButton("确定", (d, w) -> {
                        try {
                            mSearchHistoryDB.delete(DataBaseHistorySearch.TABLE_NAME,
                                    DataBaseHistorySearch.DB_COLUMN_KEY + " = ?", new String[]{key});
                            List<Map<String, Object>> data = getSearchHistory();
                            if (!data.isEmpty()) {
                                mSearchHistoryList.setAdapter(new SimpleAdapter(this, data, R.layout.search_item,
                                        new String[]{DataBaseHistorySearch.DB_COLUMN_KEY, DataBaseHistorySearch.DB_COLUMN_DESCRIPTION,
                                                DataBaseHistorySearch.DB_COLUMN_TIMESTAMP, DataBaseHistorySearch.DB_COLUMN_IS_LOCATION,
                                                DataBaseHistorySearch.DB_COLUMN_LONGITUDE_CUSTOM, DataBaseHistorySearch.DB_COLUMN_LATITUDE_CUSTOM},
                                        new int[]{R.id.search_key, R.id.search_description, R.id.search_timestamp,
                                                R.id.search_isLoc, R.id.search_longitude, R.id.search_latitude}));
                                mHistoryLayout.setVisibility(View.VISIBLE);
                            }
                        } catch (Exception e) { GoUtils.DisplayToast(this, getString(R.string.history_delete_error)); }
                    }).setNegativeButton("取消", null).show();
            return true;
        });
    }

    private void performSearch(String query) {
        String enc;
        try { enc = URLEncoder.encode(query, "UTF-8"); } catch (UnsupportedEncodingException e) { enc = query; }
        String url = "https://nominatim.openstreetmap.org/search?format=json&q=" + enc + "&limit=10&accept-language=zh";
        okhttp3.Request req = new okhttp3.Request.Builder().url(url).header("User-Agent", getPackageName()).build();
        mOkHttpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> GoUtils.DisplayToast(MainActivity.this, getString(R.string.app_error_search)));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) return;
                String resp = body.string();
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(resp);
                        if (arr.length() == 0) { GoUtils.DisplayToast(MainActivity.this, getString(R.string.app_search_null)); return; }
                        List<Map<String, Object>> data = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            Map<String, Object> poi = new HashMap<>();
                            poi.put(POI_NAME, o.optString("name", o.optString("display_name", "")));
                            poi.put(POI_ADDRESS, o.optString("display_name", ""));
                            poi.put(POI_LONGITUDE, o.optString("lon", ""));
                            poi.put(POI_LATITUDE, o.optString("lat", ""));
                            data.add(poi);
                        }
                        mSearchList.setAdapter(new SimpleAdapter(MainActivity.this, data, R.layout.search_poi_item,
                                new String[]{POI_NAME, POI_ADDRESS, POI_LONGITUDE, POI_LATITUDE},
                                new int[]{R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude}));
                        mSearchLayout.setVisibility(View.VISIBLE);
                    } catch (JSONException e) { XLog.e("Search JSON parse error"); }
                });
            }
        });
    }

    private void initUpdateVersion() {
        mDownloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        mDownloadBdRcv = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { installNewVersion(); }
        };
        registerReceiver(mDownloadBdRcv, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkUpdateVersion(boolean showToast) {
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url("https://api.github.com/repos/zcshou/gogogo/releases/latest").build();
        mOkHttpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { XLog.i("更新检测失败"); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) return;
                String resp = body.string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(resp);
                        String cur = GoUtils.getVersionName(MainActivity.this);
                        if (cur != null && (!json.getString("name").contains(cur) || !json.getString("tag_name").contains(cur))) {
                            android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(MainActivity.this).create();
                            alertDialog.show();
                            alertDialog.setCancelable(false);
                            Window win = alertDialog.getWindow();
                            if (win != null) {
                                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                                win.setContentView(R.layout.update);
                                win.setGravity(Gravity.CENTER);
                                win.setWindowAnimations(R.style.DialogAnimFadeInFadeOut);
                                ((TextView) win.findViewById(R.id.update_title)).setText(json.getString("name"));
                                ((TextView) win.findViewById(R.id.update_time)).setText(json.getString("created_at"));
                                ((TextView) win.findViewById(R.id.update_commit)).setText(json.getString("target_commitish"));
                                Markwon.create(MainActivity.this).setMarkdown(win.findViewById(R.id.update_content), json.getString("body"));
                                ((Button) win.findViewById(R.id.update_ignore)).setOnClickListener(v -> alertDialog.cancel());
                                JSONObject asset = new JSONArray(json.getString("assets")).getJSONObject(0);
                                mUpdateFilename = asset.getString("name");
                                ((Button) win.findViewById(R.id.update_agree)).setOnClickListener(v -> {
                                    alertDialog.cancel();
                                    GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_downloading));
                                    downloadNewVersion(asset.optString("browser_download_url"));
                                });
                            }
                        } else if (showToast) {
                            GoUtils.DisplayToast(MainActivity.this, getString(R.string.update_last));
                        }
                    } catch (JSONException e) { XLog.e("ERROR: resolve json"); }
                });
            }
        });
    }

    private void downloadNewVersion(String url) {
        if (mDownloadManager == null) return;
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setAllowedOverRoaming(false);
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setTitle(GoUtils.getAppName(this));
        req.setDescription("正在下载新版本...");
        req.setMimeType("application/vnd.android.package-archive");
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        if (file.exists() && !file.delete()) return;
        req.setDestinationUri(Uri.fromFile(file));
        mDownloadId = mDownloadManager.enqueue(req);
    }

    private void installNewVersion() {
        Uri downloadUri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
        File file = new File(getExternalFilesDir("Updates"), mUpdateFilename);
        Intent install = new Intent(Intent.ACTION_VIEW);
        if (downloadUri != null) {
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            install.addCategory("android.intent.category.DEFAULT");
            install.setDataAndType(ShareUtils.getUriFromFile(this, file), "application/vnd.android.package-archive");
        } else {
            install = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            install.addCategory("android.intent.category.DEFAULT");
        }
        startActivity(install);
    }
}
