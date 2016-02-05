package windylabs.com.hyphen.views;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;

/**
 * Created by g.anderson on 1/29/16.
 */
public class HyphenMapView extends MapView {
    public HyphenMapView(Context context) {
        super(context);
    }

    public HyphenMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HyphenMapView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public HyphenMapView(Context context, GoogleMapOptions options) {
        super(context, options);
    }

    @Override
    public void getMapAsync(OnMapReadyCallback callback) {
        super.getMapAsync(callback);
    }
}
