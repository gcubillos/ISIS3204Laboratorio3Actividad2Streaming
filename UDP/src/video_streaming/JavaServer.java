package video_streaming;

import java.io.*;
import java.net.*;
import java.util.Scanner;

import javax.imageio.ImageIO;

import javax.swing.*;
import java.awt.image.BufferedImage;

import com.sun.jna.NativeLibrary;
import java.awt.*;

import uk.co.caprica.vlcj.player.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CanvasVideoSurface;
import java.awt.event.*;

public class JavaServer {

	static final String servidor="localhost";
	public static InetAddress[] inet;
	public static int clientes, MAX_SIZE=62000;
	public static int[] port;
	public static int i;
	static int count = 0;
	public static BufferedReader[] inFromClient;
	public static DataOutputStream[] outToClient;
	static final String rutaVLC = "C:\\Program Files (x86)\\VideoLAN\\VLC"; 
	

	public static void main(String[] args) throws Exception {

		
		Scanner sc = new Scanner(System.in);
		System.out.println("Por favor indique la cantidad máxima de clientes");
		
		JavaServer jv = new JavaServer(sc.nextInt());
	}

	public JavaServer(int cantClientes) throws Exception {
		JavaServer.clientes=cantClientes;
		NativeLibrary.addSearchPath("libvlc", rutaVLC );

		JavaServer.inet = new InetAddress[clientes];
		port = new int[clientes];

		@SuppressWarnings("resource")
		ServerSocket welcomeSocket = new ServerSocket(5624);
		System.out.println("Conexion " + ((welcomeSocket.isClosed()) ? "cerrada!" : "abierta"));
		Socket connectionSocket[] = new Socket[clientes];
		inFromClient = new BufferedReader[clientes];
		outToClient = new DataOutputStream[clientes];

		/*
		 * sockets UDP _________________________________________________________________
		 */
		DatagramSocket serv = new DatagramSocket(4321);
		byte[] buf = new byte[62000];
		// sockets de conexión
		DatagramPacket dp = new DatagramPacket(buf, buf.length);

		@SuppressWarnings("unused")
		SubPlayer p = new SubPlayer();

		i = 0;

		TextThread[] st = new TextThread[clientes];

		while (true) {
			//			System.out.println("Puerto del servidor: " + serv.getPort());
			serv.receive(dp);
			//			System.out.println("Data recivida: " + new String(dp.getData()));
			buf = "starts".getBytes();

			inet[i] = dp.getAddress();
			port[i] = dp.getPort();

			DatagramPacket dsend = new DatagramPacket(buf, buf.length, inet[i], port[i]);
			serv.send(dsend);

			VideoSenderThread sender = new VideoSenderThread(serv);

			System.out.println("waiting\n ");
			connectionSocket[i] = welcomeSocket.accept();
			System.out.println("connected " + i);

			inFromClient[i] = new BufferedReader(new InputStreamReader(connectionSocket[i].getInputStream()));
			outToClient[i] = new DataOutputStream(connectionSocket[i].getOutputStream());
			outToClient[i].writeBytes("Connected: from Server\n");

			st[i] = new TextThread(i);
			st[i].start();
			sender.start();

			if (i++ == clientes)
				break;
		}
	}
}

class VideoSenderThread extends Thread {

	int clientno;

	JFrame jf = new JFrame("video en transmisión");
	JLabel jleb = new JLabel();

	DatagramSocket soc;

	Robot rb = new Robot();

	byte[] outbuff = new byte[62000];

	BufferedImage mybuf;
	ImageIcon img;
	Rectangle rc;

	int bord = SubPlayer.reproductor.getY() - SubPlayer.frame.getY();

	public VideoSenderThread(DatagramSocket ds) throws Exception {
		soc = ds;

		System.out.println(soc.getPort());
		jf.setSize(500, 400);
		jf.setLocation(500, 400);
		jf.setVisible(true);
	}

	public void run() {
		while (true) {
			try {

				int num = JavaServer.i;

				rc = new Rectangle(new Point(SubPlayer.frame.getX() + 8, SubPlayer.frame.getY() + 27),
						new Dimension(SubPlayer.reproductor.getWidth(), SubPlayer.frame.getHeight() / 2));

				mybuf = rb.createScreenCapture(rc);

				img = new ImageIcon(mybuf);

				jleb.setIcon(img);
				jf.add(jleb);
				jf.repaint();
				jf.revalidate();

				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				ImageIO.write(mybuf, "jpg", baos);

				outbuff = baos.toByteArray();

				for (int j = 0; j < num; j++) {
					DatagramPacket dp = new DatagramPacket(outbuff, outbuff.length, JavaServer.inet[j],
							JavaServer.port[j]);
					soc.send(dp);
					baos.flush();
				}
				Thread.sleep(15);
			} catch (Exception e) {
			}
		}
	}
}

class SubPlayer {

	// Create a media player factory
	private MediaPlayerFactory mediaPlayerFactory;

	// Create a new media player instance for the run-time platform
	private EmbeddedMediaPlayer mediaPlayer;

	public static JPanel reproductor;
	public static JPanel myjp;
	private Canvas canvas;
	public static JFrame frame;
	public static JTextArea ta;
	public static int xpos = 0, ypos = 0;
	

	public SubPlayer() {

		reproductor = new JPanel();
		reproductor.setLayout(new BorderLayout());

		JPanel mypanel = new JPanel();
		mypanel.setLayout(new GridLayout(2, 1));

		// Creating the canvas and adding it to the panel :
		canvas = new Canvas();
		canvas.setBackground(Color.BLACK);

		reproductor.add(canvas, BorderLayout.CENTER);

		// Creation a media player :
		mediaPlayerFactory = new MediaPlayerFactory();
		mediaPlayer = mediaPlayerFactory.newEmbeddedMediaPlayer();
		CanvasVideoSurface videoSurface = mediaPlayerFactory.newVideoSurface(canvas);
		mediaPlayer.setVideoSurface(videoSurface);

		// Construction of the jframe :
		frame = new JFrame("Servidor de streaming ");
		// frame.setLayout(null);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLocation(200, 0);
		frame.setSize	 (640, 960);
		frame.setAlwaysOnTop(true);

		Button chooser = new Button("Elegir video");
		mypanel.add(reproductor);
		frame.add(mypanel);
		frame.setVisible(true);
		xpos = frame.getX();
		ypos = frame.getY();

		// Playing the video
		myjp = new JPanel(new GridLayout(2, 1));
		myjp.add(chooser);

		JScrollPane jpane = new JScrollPane();
		jpane.setSize(300, 200);

		ta = new JTextArea();
		jpane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		jpane.add(ta);
		jpane.setViewportView(ta);
		myjp.add(jpane);
		ta.setText("Listo para recibir conexiones...\n");
		ta.setCaretPosition(ta.getDocument().getLength());

		mypanel.add(myjp);
		mypanel.revalidate();
		mypanel.repaint();

		chooser.addActionListener(new ActionListener() {
			@Override	public void actionPerformed(ActionEvent e) {
				JFileChooser jf = new JFileChooser();jf.showOpenDialog(frame);
				File f= jf.getSelectedFile();
				ta.append(f.getName()+"\n");
				mediaPlayer.playMedia(f.getPath());
			}
		});
	}
}

class TextThread extends Thread {

	public static String clientSentence;
	int threadId;
	BufferedReader   inFromClient = JavaServer.inFromClient[threadId];
	DataOutputStream outToClient[] = JavaServer.outToClient;

	public TextThread(int a) {threadId = a;}

	public void run() {
		while (true) {
			try {
				clientSentence = inFromClient.readLine();

				SubPlayer.ta.append("From Client " + threadId + ": " + clientSentence + "\n");

				for (int i = 0; i < JavaServer.i; i++)
					if (i != threadId)
						outToClient[i].writeBytes("Client " + threadId + ": " + clientSentence + '\n'); // '\n' es necesario 

				SubPlayer.myjp.revalidate();
				SubPlayer.myjp.repaint();
			} catch (Exception e) {}
		}
	}

}
