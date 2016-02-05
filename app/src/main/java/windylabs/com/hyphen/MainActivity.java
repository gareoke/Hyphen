package windylabs.com.hyphen;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import butterknife.InjectView;
import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import windylabs.com.hyphen.services.LocationService;
import windylabs.com.hyphen.utils.AddressToStringFunc;
import windylabs.com.hyphen.utils.LocationToStringFunc;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 0;
    private static final int REQUEST_CHECK_SETTINGS = 1;
    private static final long TWENTY_FOUR_HOURS_IN_MS = 1000 * 60 * 60 * 24;

    private static final String TAG = MainActivity.class.getSimpleName();

    private ReactiveLocationProvider locationProvider;

    private Observable<Location> lastKnownLocationObservable;
    private Observable<Location> locationUpdatesObservable;
    private Observable<ActivityRecognitionResult> activityObservable;

    private Subscription lastKnownLocationSubscription;
    private Subscription updatableLocationSubscription;
    private Subscription addressSubscription;
    private Subscription activitySubscription;
    private Observable<String> addressObservable;

    private static BitmapDescriptor markerIcon;

    @InjectView(R.id.map_view) protected MapView hyphenMapView;
    protected static GoogleMap googleMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        setupMap(savedInstanceState);
        setupObservablesAndProviders();

        markerIcon = BitmapDescriptorFactory.fromResource(R.drawable.circle_marker_resized);

        Intent locationServiceIntent = new Intent(MainActivity.this, LocationService.class);
        startService(locationServiceIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case (PERMISSIONS_REQUEST_FINE_LOCATION):
                try {
                    if (grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "PERMISSIONS_REQUEST_FINE_LOCATION GRANTED -- START");
                    } else {
                        Log.d(TAG, "PERMISSIONS_REQUEST_FINE_LOCATION NOT GRANTED! -- START");
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, e.toString());
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart -- START");

        lastKnownLocationSubscription = lastKnownLocationObservable
                .subscribe(new Subscriber<Location>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Location location) {
                        Log.d(TAG, "onNext -- START -- " + location.getLatitude() + " -- " + location.getLongitude());
                        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(), location.getLongitude()), 12);
                        googleMap.animateCamera(cameraUpdate);
                    }
                });

        updatableLocationSubscription = locationUpdatesObservable
                .map(new LocationToStringFunc())
                .map(new Func1<String, String>() {
                    int count = 0;

                    @Override
                    public String call(String s) {
                        return s + " " + count++;
                    }
                })
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(String s) {

                    }
                });

        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.hyphenMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        this.hyphenMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.hyphenMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        this.hyphenMapView.onLowMemory();
    }

    private void setupMap(Bundle savedInstanceState){
        this.hyphenMapView = (MapView) findViewById(R.id.map_view);
        this.hyphenMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                try {
                    Log.d(TAG, "onConnected -- onMapReady -- START");

                    MainActivity.this.googleMap = map;

                    map.getUiSettings().setMyLocationButtonEnabled(true);
                    map.setMyLocationEnabled(true);
                    map.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                    SharedPreferences sharedPreferences = getSharedPreferences("windylabs.com.hyphen", MODE_PRIVATE);
                    Map<String, ?> allEntries = sharedPreferences.getAll();

                    Log.d(TAG, "map values size: " + allEntries.size());

                    long currentTimestampInUTC = System.currentTimeMillis();

                    for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                        Log.d(TAG, "map values: " + entry.getKey() + ": " + entry.getValue().toString());

                        if (!entry.getKey().contains("accuracy") && !entry.getKey().contains("activity") && !entry.getKey().contains("timestamp")){
                            Object[] latLngArray = ((HashSet<String>) entry.getValue()).toArray();
                            LatLng latLng = new LatLng(Double.valueOf(latLngArray[0].toString()), Double.valueOf(latLngArray[1].toString()));

                            Log.d(TAG, "map values -- lat: " + latLng.latitude);
                            Log.d(TAG, "map values -- lon: " + latLng.longitude);

                            String accuracyKey = entry.getKey() + "_accuracy";
                            String activityKey = entry.getKey() + "_activity";
                            String timestampKey = entry.getKey() + "_timestamp";

                            String accuracy = allEntries.get(accuracyKey) !=null ? allEntries.get(accuracyKey).toString() : "No";
                            String activity = allEntries.get(activityKey) !=null ? allEntries.get(activityKey).toString() : "No Activity Recorded";
                            long timestamp = allEntries.get(timestampKey) !=null ? Long.valueOf(allEntries.get(timestampKey).toString()) : 0l;

                            Log.d(TAG, "currentTimestampInUTC -- " + currentTimestampInUTC + " -- " + timestamp + " -- " + (currentTimestampInUTC-timestamp));

                            if(currentTimestampInUTC - timestamp < TWENTY_FOUR_HOURS_IN_MS) {
                                map.addMarker(new MarkerOptions().position(latLng).icon(markerIcon).title(accuracy + "meter Accuracy -- " + activity));
                            }
                        }
                    }
                } catch ( SecurityException e){
                    Log.e(TAG, e.toString());
                }
            }
        });

        this.hyphenMapView.onCreate(savedInstanceState);
    }

    private void setupObservablesAndProviders(){
        locationProvider = new ReactiveLocationProvider(getApplicationContext());
        lastKnownLocationObservable = locationProvider.getLastKnownLocation();

        final LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(5)
                .setInterval(100);

        locationUpdatesObservable = locationProvider
                .checkLocationSettings(
                        new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest)
                                .setAlwaysShow(true)  //Reference: http://stackoverflow.com/questions/29824408/google-play-services-locationservices-api-new-option-never
                                .build()
                )
                .doOnNext(new Action1<LocationSettingsResult>() {
                    @Override
                    public void call(LocationSettingsResult locationSettingsResult) {
                        Status status = locationSettingsResult.getStatus();
                        if (status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                            try {
                                status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                            } catch (IntentSender.SendIntentException th) {
                                Log.e("MainActivity", "Error opening settings activity.", th);
                            }
                        }
                    }
                })
                .flatMap(new Func1<LocationSettingsResult, Observable<Location>>() {
                    @Override
                    public Observable<Location> call(LocationSettingsResult locationSettingsResult) {
                        return locationProvider.getUpdatedLocation(locationRequest);
                    }
                });

        addressObservable = locationProvider.getUpdatedLocation(locationRequest)
                .flatMap(new Func1<Location, Observable<List<Address>>>() {
                    @Override
                    public Observable<List<Address>> call(Location location) {
                        return locationProvider.getReverseGeocodeObservable(location.getLatitude(), location.getLongitude(), 1);
                    }
                })
                .map(new Func1<List<Address>, Address>() {
                    @Override
                    public Address call(List<Address> addresses) {
                        return addresses != null && !addresses.isEmpty() ? addresses.get(0) : null;
                    }
                })
                .map(new AddressToStringFunc())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());

        activityObservable = locationProvider.getDetectedActivity(50);
    }
}
