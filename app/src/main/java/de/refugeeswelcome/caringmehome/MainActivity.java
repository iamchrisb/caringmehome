package de.refugeeswelcome.caringmehome;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import de.refugeeswelcome.caringmehome.model.api.crisisnet.CrisisResponse;
import de.refugeeswelcome.caringmehome.model.api.opencage.Annotation;
import de.refugeeswelcome.caringmehome.model.api.opencage.Response;
import de.refugeeswelcome.caringmehome.model.api.planetlabs.Scenes;
import de.refugeeswelcome.caringmehome.util.CrisisNetApi;
import de.refugeeswelcome.caringmehome.util.GeocoderAPI;
import de.refugeeswelcome.caringmehome.util.LocationSuggestion;
import de.refugeeswelcome.caringmehome.util.OpenCageGeocoder;
import de.refugeeswelcome.caringmehome.util.PlanetLabApi;
import okhttp3.Call;
import okhttp3.Callback;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    FloatingSearchView mSearchView;

    ObjectMapper mapper = new ObjectMapper();

    private CrisisNetApi mCrisisApi = new CrisisNetApi();

    private boolean doubleBackToExitPressedOnce;
    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            doubleBackToExitPressedOnce = false;
        }
    };
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSearchView = (FloatingSearchView) findViewById(R.id.floating_search_view);

        if (isNetworkAvailable()) {
            Log.d(TAG, "Call planet labs api");
            callPlanetApi();
            mSearchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
                @Override
                public void onSearchTextChanged(String oldQuery, final String newQuery) {
                    //get suggestions based on newQuery
                    search(newQuery);
                    //pass them on to the search view
                }
            });

            mSearchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
                @Override
                public void onSuggestionClicked(SearchSuggestion searchSuggestion) {
                    mSearchView.hideProgress();
                    Log.d(TAG, "Click on a found item in list...");
                    LocationSuggestion locationSuggestion = (LocationSuggestion) searchSuggestion;
                    Log.d(TAG, "LocationSuggestion= " + locationSuggestion.toString());
                    String searchResult = locationSuggestion.getmLocation();
                    Log.d(TAG, "SearchResult= " + searchResult.toString());
                    Log.d(TAG, "Call tame API with= " + searchResult.split(",")[0]);
                    Intent intent = new Intent(getApplicationContext(), MyHomeActivity.class);
                    intent.putExtra("city", searchResult.split(",")[0]);
                    startActivity(intent);

                    mCrisisApi.feeds(locationSuggestion.getmLat(), locationSuggestion.getmLng(), new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                        }
                        @Override
                        public void onResponse(Call call, okhttp3.Response response) throws IOException {
                            String res = response.body().string();
                            CrisisResponse resp = mapper.readValue(res, CrisisResponse.class);
                        }
                    });
                }

                @Override
                public void onSearchAction(String currentQuery) {

                }
            });
        } else {
            Toast.makeText(this, R.string.network_notavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void callPlanetApi() {
        final PlanetLabApi planetApi = new PlanetLabApi();
        planetApi.scenes(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, e.getMessage());
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                String res = response.body().string();
                Log.d(TAG, res);
                Scenes scenes = mapper.readValue(res, Scenes.class);
                String picUrl = scenes.getFeatures().get(0).getProperties().getLinks().get("full");
                Log.d(TAG, picUrl);

                downloadPic(planetApi, picUrl);
            }


        });

    }

    private void downloadPic(PlanetLabApi planetApi, String picUrl) {
        planetApi.data(picUrl, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, e.getMessage());
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                Log.d(TAG,response.body().string());
                InputStream picStream = response.body().byteStream();
                //Log.d(TAG, picStream.);
            }
        });
    }


    /**
     * Searches the give search text in {@link GeocoderAPI}.
     *
     * @param searchText The text that should be searched. Most commonly a city or another place in the world.
     */
    public void search(String searchText) {
        GeocoderAPI geocoder = new OpenCageGeocoder();
        geocoder.location(searchText, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, e.getMessage());
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                try {
                    String jsonData = response.body().string();
                    Response res = mapper.readValue(jsonData, Response.class);

                    List<SearchSuggestion> suggestions = new LinkedList<>();

                    for (Annotation annotations : res.getResults()) {
                        suggestions.add(new LocationSuggestion(annotations.getFormatted(), annotations.getGeometry().getLat(), annotations.getGeometry().getLng()));
                    }

                    showSuggestions(suggestions);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void showSuggestions(final List<SearchSuggestion> suggestions) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSearchView.swapSuggestions(suggestions);
                mSearchView.showProgress();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    @Override
    public void onBackPressed() {

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.clickBackToExit, Toast.LENGTH_SHORT).show();

        mHandler.postDelayed(mRunnable, 2000);
    }

    /**
     * Checks if the network is available and returns the result as a boolean.
     *
     * @return The result value for network checking as boolean.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        boolean isAvailable = false;
        if (networkInfo != null && networkInfo.isConnected()) {
            isAvailable = true;
        }
        return isAvailable;
    }
}
