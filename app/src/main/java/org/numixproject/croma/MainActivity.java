package org.numixproject.croma;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import me.croma.image.Color;
import me.croma.image.KMeansColorPicker;

import static android.webkit.WebSettings.LOAD_DEFAULT;

public class MainActivity extends Activity implements BillingProcessor.IBillingHandler {

    // Start page
    private final String INDEX = "file:///android_asset/www/index.html";
    // Arbitrary integer to check request code
    private final int SELECT_PHOTO = 1;
    public WebView webView;
    BillingProcessor bp;

    // Handle back button press
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    // Get bitmap from Uri
    private Bitmap getBitmap(Uri uri) {
        try {
            final InputStream stream = getContentResolver().openInputStream(uri);

            final Bitmap selectedImage = BitmapFactory.decodeStream(stream);

            return selectedImage;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Get color palette from image
    private String getColors(Bitmap bitmap) {
        String url = INDEX + "#/palette/show?palette=";

        BitMapImage b = new BitMapImage(bitmap);

        KMeansColorPicker k = new KMeansColorPicker();

        try {
            List<Color> l = k.getUsefulColors(b, 6);

            for (Color c : l) {
                url += c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "|";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return url;
    }

    // Show a progress dialog when processing image
    public void processImage(final Bitmap bitmap) {
        final ProgressDialog progress = ProgressDialog.show(
                MainActivity.this, null, getString(R.string.wait_label), true);

        // Process the image in a new thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String URL = getColors(bitmap);

                    // Return to the UI thread
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.loadUrl(URL);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }

                progress.dismiss();
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bp = new BillingProcessor(this, null, this);

        setContentView(R.layout.main);

        webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();

        String appCachePath = getApplicationContext().getCacheDir().getAbsolutePath();

        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportZoom(false);
        webSettings.setSaveFormData(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath(appCachePath);
        webSettings.setAllowFileAccess(true);
        webSettings.setCacheMode(LOAD_DEFAULT);

        webView.setWebViewClient(new webViewClient());

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // Expose Java methods as JavaScript interfaces
        webView.addJavascriptInterface(new Storage(this), "androidStorage");
        webView.addJavascriptInterface(new Utils(this), "androidUtils");
        webView.addJavascriptInterface(new ImageUtils(this), "imageUtils");
        webView.addJavascriptInterface(new InAppBilling(this), "inAppBilling");

        // Check if called from share menu
        Intent intent = getIntent();
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        Uri data = intent.getData();

        if (Intent.ACTION_SEND.equals(action) && extras.containsKey(Intent.EXTRA_STREAM)) {
            // Get resource path
            final Uri imageUri = extras.getParcelable(Intent.EXTRA_STREAM);
            final Bitmap bitmap = getBitmap(imageUri);

            processImage(bitmap);
        } else if (Intent.ACTION_VIEW.equals(action) && data != null) {
            final String URL = data.toString().replaceAll("(^https?://" + getString(R.string.app_host) + "/|^croma://)", INDEX);

            webView.loadUrl(URL);
        } else {
            // Load the start page
            webView.loadUrl(INDEX);
        }
    }

    // Android InAppBilling things
    // PLEASE SEE https://github.com/anjlab/android-inapp-billing-v3 FOR MORE INFORMATION.

    // IBillingHandler implementation
    @Override
    public void onBillingInitialized() {
        /*
         * Called then BillingProcessor was initialized and its ready to purchase
         */
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails details) {
        /*
         * Called then requested PRODUCT ID was successfully purchased
         */
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        /*
         * Called then some error occured. See Constants class for more details
         */
    }

    @Override
    public void onPurchaseHistoryRestored() {
        /*
         * Called then purchase history was restored and the list of all owned PRODUCT ID's
         * was loaded from Google Play
         */
    }

    // Unbind IInAppBilling on close
    @Override
    public void onDestroy() {
        if (bp != null)
            bp.release();

        super.onDestroy();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (!bp.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case SELECT_PHOTO:

                if (resultCode == RESULT_OK) {
                    Bitmap bitmap;

                    if (data.getData() != null) {
                        final Uri uri = data.getData();

                        bitmap = getBitmap(uri);

                        processImage(bitmap);

                    } else if (data.getExtras().get("data") != null) {
                        bitmap = (Bitmap) data.getExtras().get("data");

                        processImage(bitmap);
                    }
                }
        }

    }


    private class webViewClient extends WebViewClient {
        // Show a splash screen until the WebView is ready
        @Override
        public void onPageFinished(WebView view, String url) {
            findViewById(R.id.imageView1).setVisibility(View.GONE);
            findViewById(R.id.webview).setVisibility(View.VISIBLE);
        }
    }

    public class ImageUtils extends MainActivity {

        Context mContext;

        // Set application context.
        ImageUtils(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void getPalette() {
            // Camera.
            final List<Intent> cameraIntents = new ArrayList<Intent>();
            final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            final PackageManager packageManager = mContext.getPackageManager();
            final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);

            for (ResolveInfo res : listCam) {
                final String packageName = res.activityInfo.packageName;
                final Intent intent = new Intent(captureIntent);

                intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
                intent.setPackage(packageName);

                cameraIntents.add(intent);
            }

            // Filesystem.
            final Intent galleryIntent = new Intent(Intent.ACTION_PICK);

            galleryIntent.setType("image/*");

            // Chooser of filesystem options.
            final Intent chooserIntent = Intent.createChooser(galleryIntent,
                    mContext.getString(R.string.select_label));

            // Add the camera options.
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));

            ((Activity) mContext).startActivityForResult(chooserIntent, SELECT_PHOTO);
        }
    }

}