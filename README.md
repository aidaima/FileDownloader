FileDownloader
===================

#### A file downloader on android, with breakpoint continuingly and multithreading

----------


Features
-------------

####   </i>breakpoint continuingly
####   </i>multithreading
####   </i>lightly
----------


Usage:
-------------------
	private FileDownloader mFileDownloader;
	// Create file downloader
	mFileDownloader = new FileDownloader(3);
	
	// Create file downloader listener
	FileDownloader.Listener listener = mFileDownloader.new Listener() {
            @Override
            public void onStart() {
	            // download start
            }

            public void onFinish() {
	            // download finish
            }

            public void onError(String msg) {
	            // download error
	    }

            public void onProgressChange(long fileSize, long downloadedSize) {
	            // download progress change
            }
        };

	mFileDownloader.add("/sdcard/TencentVideo9.9.970.0.exe", "http://dldir1.qq.com/qqtv/TencentVideo9.9.970.0.exe", listener);


