package sandbox.java.camera.mjpeg;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lti.civil.CaptureSystem;
import com.lti.civil.DefaultCaptureSystemFactorySingleton;
import com.lti.civil.Image;
import com.lti.civil.awt.AWTImageConverter;

/*
 * streaming the camera images as mjpeg over http
 * 
 * Start server, then from browser go to http://serverip to view the HTML page
 * 
 */
public class Main {
	private final static Logger Logger = LoggerFactory.getLogger(Main.class);
	
	public static void main(String[] args) throws IOException {
        AtomicBoolean running = new AtomicBoolean(true);
		Executors.newSingleThreadExecutor().execute(() -> {
			try (ServerSocket server = new ServerSocket(80)) {
				while (running.get()) {
					try(Socket socket = server.accept(); BufferedOutputStream os = new BufferedOutputStream(socket.getOutputStream())) {
						os.write(("HTTP/1.0 200 OK\r\n" +
								"Server: iRecon\r\n" +
								"Connection: close\r\n" +
								"Max-Age: 0\r\n" +
								"Expires: 0\r\n" +
								"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
								"Pragma: no-cache\r\n" +
								"Content-Type: text/html;").getBytes());
						String html = "<html><body><img src=\"http://127.0.0.1:8080\" align=\"middle\" style=\"position: absolute; margin: auto; left: 0; right: 0; top: 0; bottom: 0;\"></body></html>";
						os.write(("Content-type: image/jpeg\r\n" +
								"Content-Length: " + html.length() + "\r\n" +
								"X-Timestamp:" + new Timestamp(new Date().getTime()) + "\r\n" +
								"\r\n").getBytes());
						os.write(html.getBytes());
					}
				}
			} catch(IOException ex) {
				Logger.debug("Uncaught IOException in getBestCaptureStream()", ex);
            }
        });
		
		ArrayBlockingQueue<Image> queue = new ArrayBlockingQueue<>(1);
		try {
			CaptureSystem captureSystem = DefaultCaptureSystemFactorySingleton.instance().createCaptureSystem();
			captureSystem.init();
			CameraServer cameraServer = new CameraServer(queue, captureSystem);
			cameraServer.run();
		}
		catch(Throwable th) {
			Logger.debug("Uncaught throwable in getBestCaptureStream()", th);
		}

		try(ServerSocket server = new ServerSocket(8080)) {
            Logger.info("Server started, listening at port " + server.getLocalPort());
            while(running.get()) {
                try (Socket socket = server.accept(); BufferedOutputStream os = new BufferedOutputStream(socket.getOutputStream())) {
                    Logger.info("Client " + socket.getRemoteSocketAddress() + " connected");
                    String boundary = "randomstring";
                    os.write(("HTTP/1.0 200 OK\r\n" +
                            "Server: iRecon\r\n" +
                            "Connection: close\r\n" +
                            "Max-Age: 0\r\n" +
                            "Expires: 0\r\n" +
                            "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Content-Type: multipart/x-mixed-replace; " +
                            "boundary=" + boundary + "\r\n" +
                            "\r\n" +
                            "--" + boundary + "\r\n").getBytes());

                    while (!socket.isClosed()) {
                        try {
                            Image image = queue.poll(100, TimeUnit.MILLISECONDS);
                            if (image != null) {
                                BufferedImage bufferedImage = AWTImageConverter.toBufferedImage(image);
                                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                ImageIO.write(bufferedImage, "JPEG", buffer);
                                os.write(("Content-type: image/jpeg\r\n" +
                                        "Content-Length: " + buffer.size() + "\r\n" +
                                        "X-Timestamp:" + new Timestamp(new Date().getTime()) + "\r\n" +
                                        "\r\n").getBytes());
                                os.write(buffer.toByteArray());
                                os.write(("\r\n--" + boundary + "\r\n").getBytes());
                            }
                        }
                        catch(InterruptedException ex) {
                        }
                    }
                } catch (IOException ex) {
                    Logger.debug("Uncaught IOException in getBestCaptureStream()", ex);
                }
            }
        }
	}
}