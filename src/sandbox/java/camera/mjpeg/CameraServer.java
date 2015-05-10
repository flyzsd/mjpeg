package sandbox.java.camera.mjpeg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lti.civil.CaptureException;
import com.lti.civil.CaptureObserver;
import com.lti.civil.CaptureStream;
import com.lti.civil.CaptureSystem;
import com.lti.civil.Image;
import com.lti.civil.VideoFormat;
import com.lti.civil.impl.jni.NativeCaptureDeviceInfo;

public class CameraServer implements Runnable {
	private final static Logger Logger = LoggerFactory.getLogger(CameraServer.class);
	private final ArrayBlockingQueue<Image> queue;
	private final CaptureSystem captureSystem;
	
	public CameraServer( ArrayBlockingQueue<Image> queue, CaptureSystem captureSystem){
		this.queue = queue;
		this.captureSystem = captureSystem;
	}
	@Override
	public void run() {
		CaptureStream captureStream = null;
		try {
			captureStream = getBestCaptureStream(captureSystem);
			if(captureStream == null) {
				return;
			}
				
			captureStream.setObserver(new CaptureObserver() {			
				@Override
				public void onNewImage(final CaptureStream stream, final Image image) {
					//each callback will be in different new native thread
					Logger.debug("+CaptureObserver.onNewImage()");
					queue.offer(image);
					Logger.debug("-CaptureObserver.onNewImage()");
				}
				
				@Override
				public void onError(final CaptureStream stream, final CaptureException ex) {
					//each callback will be in different new native thread
					Logger.error("+CaptureObserver.onError()", ex);
					try {
						stream.stop();
						stream.dispose();
					} catch (CaptureException e) {}
					Logger.error("-CaptureObserver.onError()");
				}
			});
			captureStream.start();
		}
		catch(Throwable th) {
			if(captureStream != null) {
				try {
					captureStream.dispose();
				} catch (CaptureException e) {}
			}
		}
	}

	private static String formatTypeToString(int type) {
		switch(type){
			case VideoFormat.RGB24:
				return "RGB24";
			case VideoFormat.RGB32:
				return "RGB32";
			default:
				return "" + type + " (unknown)";
		}
	}
	
	private static CaptureStream getBestCaptureStream(CaptureSystem captureSystem) {
		try {
			List list = captureSystem.getCaptureDeviceInfoList();
			if(list == null || list.size() == 0)
				return null;
			
			List<CaptureStream> captureStreamList = new ArrayList<CaptureStream>();
			for(int i = 0; i<list.size(); i++) {
				NativeCaptureDeviceInfo nativeCaptureDeviceInfo = (NativeCaptureDeviceInfo)list.get(i);
				Logger.debug(nativeCaptureDeviceInfo.getDeviceID() + " " + nativeCaptureDeviceInfo.getDescription());
				CaptureStream captureStream = null;
				try {
					captureStream = captureSystem.openCaptureDeviceStream(nativeCaptureDeviceInfo.getDeviceID());
					VideoFormat format = captureStream.getVideoFormat();
					if(format != null){
						Logger.debug("Default format - Type=" + formatTypeToString(format.getFormatType()) + " Width=" + format.getWidth() + " Height=" + format.getHeight() + " FPS=" + format.getFPS());
					}
					List<VideoFormat> videoFormatList = captureStream.enumVideoFormats();
					if(videoFormatList != null && videoFormatList.size() != 0) {
						for (VideoFormat f : videoFormatList){
							Logger.debug("Available format - Type=" + formatTypeToString(f.getFormatType()) + " Width=" + f.getWidth() + " Height=" + f.getHeight() + " FPS=" + f.getFPS());
						}
					}
					captureStreamList.add(captureStream);
				}
				catch(Throwable th) {
					Logger.debug("Uncaught throwable in getBestCaptureStream()", th);
					if(captureStream != null) {
						try {
							captureStream.dispose();
						}
						catch(Throwable th2){}
						captureStream = null;
					}
				}
			}
			
			if(captureStreamList.isEmpty()) {
				return null;
			}
			
			Collections.sort(captureStreamList, new Comparator<CaptureStream>() {
				@Override
				public int compare(CaptureStream cs1, 	CaptureStream cs2) {
					try {
						VideoFormat vf1 = cs1.getVideoFormat();
						VideoFormat vf2 = cs2.getVideoFormat();
						if(vf1.getFormatType() == vf2.getFormatType()) {
							if(vf1.getFPS() == vf2.getFPS()) {
								int resolution1 = vf1.getWidth() * vf1.getWidth();
								int resolution2 = vf2.getWidth() * vf2.getWidth();
								if(resolution1 == resolution2)
									return 0;
								else 
									return (resolution1 > resolution2) ? -1 : 1;
							}
							else {
								return (vf1.getFPS() > vf2.getFPS()) ? -1 : 1;
							}
						}
						else {
							return (vf1.getFormatType() > vf2.getFormatType()) ? -1 : 1;
						}
					}
					catch(Throwable th) {
						return 0;
					}
				}
			});
			
			return captureStreamList.get(0);
		}
		catch(Throwable th) {
			Logger.debug("Uncaught throwable in getBestCaptureStream()", th);
		}
		return null;
	}
}