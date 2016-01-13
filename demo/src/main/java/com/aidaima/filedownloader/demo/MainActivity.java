package com.aidaima.filedownloader.demo;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.aidaima.filedownloader.FileDownloader;
import com.aidaima.filedownloader.NetworkException;

public class MainActivity extends AppCompatActivity {
//    private static final String TAG = "DOWNLOAD";
//
//    private FileDownloader mFileDownloader;
//
//    private Request.Listener<Void> mListener = new Request.Listener<Void>() {
//        @Override
//        public void onPreExecute() {
//            Log.d(TAG, "in onPreExecute");
//        }
//
//        @Override
//        public void onSuccess(Void response) {
//            Log.d(TAG, "in onSuccess");
//        }
//
//        @Override
//        public void onError(NetworkException error) {
//            Log.d(TAG,
//                    "in onError, error = "
//                            + error.getLocalizedMessage());
//        }
//
//        @Override
//        public void onFinish() {
//            Log.d(TAG, "in onFinish");
//        }
//
//        @Override
//        public void onProgressChange(long fileSize,
//                                     long downloadedSize) {
//            Log.d(TAG, "fileSize: " + fileSize + ", downloadedSize: " + downloadedSize);
//        }
//    };
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        mFileDownloader = new FileDownloader(1);
//        mFileDownloader.add("/sdcard/TencentVideo9.9.970.0.exe", "http://dldir1.qq.com/qqtv/TencentVideo9.9.970.0.exe", mListener);
//        mFileDownloader.add("/sdcard/TencentVideo9.9.970.1.exe", "http://dldir1.qq.com/qqtv/TencentVideo9.9.970.0.exe", mListener);
//
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                mFileDownloader.clearAll();
//            }
//        }, 1000);
//    }
//
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
}