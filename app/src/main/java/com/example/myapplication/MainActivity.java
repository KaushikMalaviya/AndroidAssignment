package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.jakewharton.disklrucache.DiskLruCache;
import android.util.LruCache;


public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ImageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ImageAdapter(MainActivity.this);
        recyclerView.setAdapter(adapter);

        // Fetch and display images
        new FetchImagesTask(this).execute();
    }

    private static class FetchImagesTask extends AsyncTask<Void, Void, List<String>> {
        private WeakReference<MainActivity> activityReference;

        FetchImagesTask(MainActivity activity) {
            activityReference = new WeakReference<>(activity);
        }

        @Override
        protected List<String> doInBackground(Void... voids) {
            MainActivity activity = activityReference.get();
            if (activity == null) return null;

            List<String> imageUrls = new ArrayList<>();
            DiskLruCache diskCache = getDiskCache(activity);

            // Check disk cache
            if (diskCache != null) {
                for (int i = 0; i < 100; i++) {
                    String imageUrl = getFromDiskCache(diskCache, String.valueOf(i));
                    if (imageUrl != null) {
                        imageUrls.add(imageUrl);
                    }
                }
            }

            // Fetch data from the provided URL for missing images
            if (imageUrls.size() < 100) {
                try {
                    URL url = new URL("https://picsum.photos/v2/list?page=1&limit=100");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    // Read response data
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        response.append(line);
                    }
                    bufferedReader.close();
                    inputStream.close();

                    // Parse JSON response and construct image URLs
                    JSONArray jsonArray = new JSONArray(response.toString());
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String id = jsonObject.getString("id");
                        String imageUrl = "https://picsum.photos/id/" + id + "/200/300";
                        imageUrls.add(imageUrl);

                        // Save to disk cache
                        putToDiskCache(diskCache, String.valueOf(i), imageUrl);
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }

            return imageUrls;
        }

        @Override
        protected void onPostExecute(List<String> imageUrls) {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    activity.adapter.setImageUrls(imageUrls);
                } else {
                    Toast.makeText(activity, "Failed to fetch image URLs", Toast.LENGTH_SHORT).show();
                }
            }
        }

        private DiskLruCache getDiskCache(MainActivity activity) {
            try {
                File cacheDir = activity.getCacheDir();
                int appVersion = 1;
                int valueCount = 1;
                long maxSize = 10 * 1024 * 1024; // 10MB
                return DiskLruCache.open(cacheDir, appVersion, valueCount, maxSize);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        private String getFromDiskCache(DiskLruCache diskCache, String key) {
            try {
                DiskLruCache.Snapshot snapshot = diskCache.get(key);
                if (snapshot != null) {
                    return snapshot.getString(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void putToDiskCache(DiskLruCache diskCache, String key, String value) {
            try {
                DiskLruCache.Editor editor = diskCache.edit(key);
                if (editor != null) {
                    editor.set(0, value);
                    editor.commit();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}