package it.uniroma3.streamer;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

@SuppressWarnings("serial")
public class GoProController extends JFrame {

	private static final String CAMERA_IP = "10.5.5.9";
	private static int PORT = 8080;
	private static DatagramSocket mOutgoingUdpSocket;
	private Process streamingProcess;
	private Process writeVideoProcess;
	private KeepAliveThread mKeepAliveThread;

	private JPanel contentPane;
	int i = 0;

	public GoProController() {
		/* Interfaccia grafica */
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(800, 10, 525, 300);

		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);

		JButton btnStop = new JButton("Stop stream");
		JButton btnStart = new JButton("Start stream");
		JButton btnRec = new JButton("Rec");
		JButton btnStopRec = new JButton("Stop Rec");

		btnStop.setEnabled(false);
		btnRec.setEnabled(false);
		btnStopRec.setEnabled(false);

		JPanel panel = new JPanel();

		panel.add(btnStart);
		panel.add(btnStop);
		panel.add(btnRec);
		panel.add(btnStopRec);

		contentPane.add(panel, BorderLayout.SOUTH);

		btnStart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				startStreamService();
				keepAlive();
				startStreaming();

				btnStart.setEnabled(false);
				btnStop.setEnabled(true);
				btnRec.setEnabled(true);
				btnStopRec.setEnabled(false);
			}
		});

		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				stopStreaming();

				btnStart.setEnabled(true);
				btnStop.setEnabled(false);
				btnRec.setEnabled(false);
				btnStopRec.setEnabled(false);
			}
		});

		btnRec.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				startRec();

				btnStart.setEnabled(false);
				btnStop.setEnabled(false);
				btnRec.setEnabled(false);
				btnStopRec.setEnabled(true);
			}
		});

		btnStopRec.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				stopRec();

				btnStart.setEnabled(false);
				btnStop.setEnabled(true);
				btnRec.setEnabled(true);
				btnStopRec.setEnabled(false);
			}
		});
	}

	/* Instaura la connessione con la GoPro */
	private void startStreamService() {
		HttpURLConnection localConnection = null;
		try {
			String str = "http://" + CAMERA_IP + "/gp/gpExec?p1=gpStreamA9&c1=restart";
			localConnection = (HttpURLConnection) new URL(str).openConnection();
			localConnection.addRequestProperty("Cache-Control", "no-cache");
			localConnection.setConnectTimeout(5000);
			localConnection.setReadTimeout(5000);
			int i = localConnection.getResponseCode();
			if (i >= 400) {
				throw new IOException("sendGET HTTP error " + i);
			}
		}
		catch (Exception e) {

		}
		if (localConnection != null) {
			localConnection.disconnect();
		}
	}

	/* Permette lo scambio di pacchetti */
	@SuppressWarnings("static-access")
	private void sendUdpCommand(int paramInt) throws SocketException, IOException {
		Locale localLocale = Locale.US;
		Object[] array = new Object[4];
		array[0] = Integer.valueOf(0);
		array[1] = Integer.valueOf(0);
		array[2] = Integer.valueOf(paramInt);
		array[3] = Double.valueOf(0.0D);
		byte[] arrayOfByte = String.format(localLocale, "_GPHD_:%d:%d:%d:%1f\n", array).getBytes();
		String str = CAMERA_IP;
		int i = PORT;
		DatagramPacket localDatagramPacket = new DatagramPacket(arrayOfByte, arrayOfByte.length, new InetSocketAddress(str, i));
		this.mOutgoingUdpSocket.send(localDatagramPacket);
	}

	/* Crea il thread per il live streaming */
	private void startStreaming() {
		Thread threadStream = new Thread() {
			@Override
			public void run() {
				try {
					streamingProcess = Runtime.getRuntime().exec("ffmpeg-20150318-git-0f16dfd-win64-static\\bin\\ffplay -i http://10.5.5.9:8080/live/amba.m3u8");
					InputStream errorStream = streamingProcess.getErrorStream();
					byte[] data = new byte[1024];
					int length = 0;
					while ((length = errorStream.read(data, 0, data.length)) > 0) {
						System.out.println(new String(data, 0, length));
						System.out.println(System.currentTimeMillis());
					}

				} catch (IOException e) {

				}
			}
		};
		threadStream.start();
	}

	/* Crea il thread per la registrazione video */
	private void startRec() {
		Thread threadRec = new Thread() {
			@Override
			public void run() {
				try {
					writeVideoProcess = Runtime.getRuntime().exec("ffmpeg-20150318-git-0f16dfd-win64-static\\bin\\ffmpeg -re -i http://10.5.5.9:8080/live/amba.m3u8 -c copy -an Video_GoPro_" 
																	+ i + "_" + Math.random() + ".avi");
					i++;
					InputStream errorRec = writeVideoProcess.getErrorStream();
					byte[] dataRec = new byte[1024];
					int lengthRec = 0;
					while ((lengthRec = errorRec.read(dataRec, 0, dataRec.length)) > 0) {
						System.out.println(new String(dataRec, 0, lengthRec));
						System.out.println(System.currentTimeMillis());
					}
				} catch (IOException e) {

				}
			}
		};
		threadRec.start();
	}

	/* Crea ed inizializza un nuovo KeepAliveThread */
	private void keepAlive() {
		mKeepAliveThread = new KeepAliveThread();
		mKeepAliveThread.start();
	}

	/* Instaura lo scambio di pacchetti fra PC e GoPro */
	class KeepAliveThread extends Thread {
		public void run() {
			try {
				Thread.currentThread().setName("gopro");
				if (mOutgoingUdpSocket == null) {
					mOutgoingUdpSocket = new DatagramSocket();
				}
				while ((!Thread.currentThread().isInterrupted()) && (mOutgoingUdpSocket != null)) {
					sendUdpCommand(2);
					Thread.sleep(2500L);
				}
			}
			catch (SocketException e) {

			}
			catch (InterruptedException e) {

			}
			catch (Exception e) {

			}
		}
	}

	/* Metodo per chiudere il live streaming */
	private void stopStreaming() {
		if (streamingProcess != null) { //chiude il processo
			streamingProcess.destroy();
			streamingProcess = null;
		}
		stopKeepalive(); //chiude connessione e scambio di pacchetti
		mOutgoingUdpSocket.disconnect();
		mOutgoingUdpSocket.close();
	}

	/* Metodo per chiudere il processo di registrazione video */
	private void stopRec() {
		writeVideoProcess.destroy();
		writeVideoProcess = null;
	}

	/* Metodo per terminare il thread corrente */
	private void stopKeepalive() {
		if (mKeepAliveThread != null) {
			mKeepAliveThread.interrupt();
			try {
				mKeepAliveThread.join(10L); //assicura il termine del thread
				mKeepAliveThread = null;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	/* Crea il pannello di controllo */
	public static void main(String[] args) {
		GoProController stream = new GoProController();
		stream.setVisible(true);
		stream.setTitle("Pannello di controllo");
	}
}