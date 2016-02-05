package windylabs.com.hyphen.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionApi;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pl.charmas.android.reactivelocation.ReactiveLocationProvider;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import windylabs.com.hyphen.utils.AddressToStringFunc;
import windylabs.com.hyphen.utils.DetectedActivityToString;
import windylabs.com.hyphen.utils.HyphenConstants;
import windylabs.com.hyphen.utils.LocationToStringFunc;
import windylabs.com.hyphen.utils.ToMostProbableActivity;

/**
 * Created by g.anderson on 2/1/16.
 */
public class LocationService extends Service {
    private static final String TAG = LocationService.class.getSimpleName();

    private static final int PERMISSIONS_REQUEST_FINE_LOCATION = 0;
    private static final int REQUEST_CHECK_SETTINGS = 1;

    private ReactiveLocationProvider locationProvider;

    private Observable<Location> lastKnownLocationObservable;
    private Observable<Location> locationUpdatesObservable;
    private Observable<ActivityRecognitionResult> activityObservable;

    private Subscription lastKnownLocationSubscription;
    private Subscription updatableLocationSubscription;
    private Subscription addressSubscription;
    private Subscription activitySubscription;
    private Observable<String> addressObservable;

    private LocationManager locationManager;
    private static final long LOCATION_REQUEST_INTERVAL_MS = 8 * 60 * 1000;  //8 minutes
    private static final long FASTEST_LOCATION_REQUEST_INTERVAL_MS = 4 * 60 * 1000; //4 minutes
    private static final float LOCATION_SMALLEST_DISPLACEMENT = 1 * 200f; //Average US city block is 200m long

    public LocationService() {}

    private static GoogleApiClient googleApiClient;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate -- START");
        super.onCreate();

        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {

                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                    }
                })
                .build();

        setupObservablesAndProviders();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand -- START");

        updatableLocationSubscription = locationUpdatesObservable
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

                        SharedPreferences prefs = getSharedPreferences(HyphenConstants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();

                        StringBuilder key = getKeyPrefix();

                        Set<String> latLon = new HashSet<String>();
                        latLon.add(Double.toString(location.getLatitude()));
                        latLon.add(Double.toString(location.getLongitude()));

                        editor.putStringSet(key.toString(), latLon);
                        editor.putString((key.toString() + "_accuracy"), String.valueOf(location.getAccuracy()));
                        editor.putString((key.toString() + "_timestamp"), String.valueOf(System.currentTimeMillis()));

                        editor.commit();
                    }
                });

        activitySubscription = activityObservable
                .map(new ToMostProbableActivity())
                .map(new DetectedActivityToString())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(String s) {
                        Log.d(TAG, "onNext -- activity -- " + s);

                        StringBuilder key = getKeyPrefix().append("_activity");

                        SharedPreferences prefs = getSharedPreferences(HyphenConstants.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();

                        editor.putString(key.toString(), s);
                        editor.commit();
                    }
                });

        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy -- START");

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged -- START");

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "onLowMemory -- START");

        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        Log.d(TAG, "onTrimMemory -- START");

        super.onTrimMemory(level);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind -- START");

        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind -- START");

        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind -- START");

        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved -- START");

        super.onTaskRemoved(rootIntent);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        Log.d(TAG, "dump -- START");
        super.dump(fd, writer, args);
    }

    private void setupObservablesAndProviders(){
        Log.d(TAG, "setupObservablesAndProviders -- START");
        locationProvider = new ReactiveLocationProvider(getApplicationContext());
        lastKnownLocationObservable = locationProvider.getLastKnownLocation();

        final LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setInterval(LOCATION_REQUEST_INTERVAL_MS)
                .setFastestInterval(FASTEST_LOCATION_REQUEST_INTERVAL_MS)
                .setSmallestDisplacement(LOCATION_SMALLEST_DISPLACEMENT);


        locationUpdatesObservable = locationProvider
                .checkLocationSettings(
                        new LocationSettingsRequest.Builder()
                                .addLocationRequest(locationRequest)
                                .setAlwaysShow(true)  //Refrence: http://stackoverflow.com/questions/29824408/google-play-services-locationservices-api-new-option-never
                                .build()
                )
                .doOnNext(new Action1<LocationSettingsResult>() {
                    @Override
                    public void call(LocationSettingsResult locationSettingsResult) {
                        Status status = locationSettingsResult.getStatus();
                        if (status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {

                        }
                    }
                })
                .flatMap(new Func1<LocationSettingsResult, Observable<Location>>() {
                    @Override
                    public Observable<Location> call(LocationSettingsResult locationSettingsResult) {
                        return locationProvider.getUpdatedLocation(locationRequest);
                    }
                });

        int milliseconds = (int)LOCATION_REQUEST_INTERVAL_MS;
        Log.d(TAG, "milliseconds - " + milliseconds);

        activityObservable = locationProvider.getDetectedActivity(milliseconds);
    }

    public static boolean isBetween(int x, int lower, int upper) {
        return lower <= x && x <= upper;
    }

    private StringBuilder getKeyPrefix(){
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int minute = Calendar.getInstance().get(Calendar.MINUTE);

        StringBuilder key = new StringBuilder().append(Integer.toString(hour));

        Log.d(TAG, "onNext -- hour -- " + hour + " -- " + minute);
        if (isBetween(minute, 0, 15)) {
            Log.d(TAG, "onNext -- isBetween -- 0 and 15");
            key.append("_0");
        }
        else if (isBetween(minute, 16, 30)) {
            Log.d(TAG, "onNext -- isBetween -- 16 and 30");
            key.append("_1");
        }
        else if (isBetween(minute, 31, 45)) {
            Log.d(TAG, "onNext -- isBetween -- 31 and 45");
            key.append("_2");
        }
        else if (isBetween(minute, 46, 59)) {
            Log.d(TAG, "onNext -- isBetween -- 46 and 59");
            key.append("_3");
        }

        return key;
    }

    private void clearSharedPrefs(){
        getSharedPreferences(HyphenConstants.SHARED_PREFS_NAME, 0).edit().clear().commit();
    }
}
