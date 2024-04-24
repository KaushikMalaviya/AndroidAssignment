package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private List<String> imageUrls = new ArrayList<>();
    private static LruCache<String, Bitmap> memoryCache;
    private DiskLruCache diskCache;

    ImageAdapter(Context context) {
        // Initialize memory cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Initialize disk cache
        try {
            File cacheDir = context.getCacheDir();
            int appVersion = 1;
            int valueCount = 1;
            long maxSize = 10 * 1024 * 1024; // 10MB
            diskCache = DiskLruCache.open(cacheDir, appVersion, valueCount, maxSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        holder.imageView.setImageResource(R.drawable.placeholder_image);

        // Load the actual image

        holder.loadImage(imageUrls.get(position));
    }

    @Override
    public int getItemCount() {
        return imageUrls.size();
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
        notifyDataSetChanged();
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageView;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_view);
        }

        void loadImage(String imageUrl) {
            // Check memory cache
            Bitmap bitmap = memoryCache.get(imageUrl);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                // Load from disk cache or network
                new LoadImageTask(imageView).execute(imageUrl);
            }
        }
    }

    private class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        private WeakReference<ImageView> imageViewReference;
        private String imageUrl;

        LoadImageTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... strings) {
            imageUrl = strings[0];
            Bitmap bitmap = null;

            // Check memory cache
            bitmap = memoryCache.get(imageUrl);
            if (bitmap != null) {
                return bitmap;
            }

            // Check disk cache
            try {
                String key = String.valueOf(imageUrl.hashCode());
                DiskLruCache.Snapshot snapshot = diskCache.get(key);
                if (snapshot != null) {
                    InputStream inputStream = snapshot.getInputStream(0);
                    bitmap = BitmapFactory.decodeStream(inputStream);
                    // Put bitmap into memory cache
                    if (bitmap != null) {
                        memoryCache.put(imageUrl, bitmap);
                    }
                    return bitmap;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            // If bitmap is still null, fetch from network
            if (bitmap == null) {
                try {
                    // Download image from URL
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();
                    bitmap = BitmapFactory.decodeStream(inputStream);
                    // Put bitmap into disk cache
                    if (bitmap != null) {
                        String key = String.valueOf(imageUrl.hashCode());
                        DiskLruCache.Editor editor = diskCache.edit(key);
                        if (editor != null) {
                            OutputStream outputStream = editor.newOutputStream(0);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                            editor.commit();
                        }
                    }
                    // Put bitmap into memory cache
                    if (bitmap != null) {
                        memoryCache.put(imageUrl, bitmap);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            ImageView imageView = imageViewReference.get();
            if (imageView != null && bitmap != null && imageUrl.equals(imageView.getTag())) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }
}