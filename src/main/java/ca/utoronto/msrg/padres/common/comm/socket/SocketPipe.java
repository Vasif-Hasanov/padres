package ca.utoronto.msrg.padres.common.comm.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import ca.utoronto.msrg.padres.common.comm.CommunicationException;
import ca.utoronto.msrg.padres.common.comm.socket.message.SocketMessage;

import de.tum.compression.gzip.GZIPDecoder;
import de.tum.compression.gzip.GZIPEncoder;
import de.tum.de.vasif.CompressionManager;
import de.tum.de.vasif.UtilCompression;

public class SocketPipe {

	private static final int BUFF_SIZE = 8 * 1024;

	private static final int RESET_COUNT = 1000;

	private Socket socket;

	private ObjectInputStream objInputStream;

	private ObjectOutputStream objOutputStream;

	private int writeCount = 0;

	public SocketPipe(Socket socket) throws CommunicationException {
		this.socket = socket;
		try {
			socket.setSendBufferSize(BUFF_SIZE);
			socket.setReceiveBufferSize(BUFF_SIZE);
			objOutputStream = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFF_SIZE));
			objOutputStream.flush();
			objInputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream(), BUFF_SIZE));
		} catch (IOException e) {
			throw new CommunicationException(e.getMessage());
		}
		writeCount = 0;
	}

	public SocketMessage read() throws CommunicationException {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		CompressionManager compressionManager = new CompressionManager();
		
		try {
			SocketMessage socketMessage = null;// =
												// (SocketMessage)objInputStream.readObject();
			byte c;
			byte compressionType = objInputStream.readByte();
			int length = objInputStream.readInt();
			for (int i = 1; i <= length; i++) {
				c = (byte) objInputStream.read();
				bout.write(c);
			}
            
			byte[] encodedData = bout.toByteArray();
			socketMessage = compressionManager.expand(compressionType, encodedData);
			System.out.println("-------------------brocker reads messages from socket ----------");

			System.out.println("SocketMessage read() = " + socketMessage.toString());
			System.out.println("-------------------broker finished to read messages -------------------");
			return socketMessage;// (SocketMessage) objInputStream.readObject();
		}  catch (IOException e) {
			throw new CommunicationException(e.getMessage());
		}  
	}

	public void write(SocketMessage msg, String... compressions) throws CommunicationException {
		System.out.println("----- SocketPipe.java; write(" + msg.toString() + ")");
		String compressionType = null;
		CompressionManager compressionManager = new CompressionManager();;
		boolean isCompressable = false;
		byte[] compressedData = null;
		byte[] serialBytes = UtilCompression.serialize(msg);
		if (compressions != null && compressions.length > 0) {
			compressionType = compressions[0];
		//	compressionManager = new CompressionManager();
			compressionManager.setCompressionType(compressionType);
			compressedData = compressionManager.compress(serialBytes);
			isCompressable = true;
		}
		// ICompressor gzip = new GZIPEncoder();

		// compressedData = gzip.encode(serialBytes);

		System.out.println("Length of serialBytes = " + serialBytes.length);
		if (compressedData != null)
			System.out.println("Length of encodedData = " + compressedData.length);

		try {
			byte compressionIndicator=compressionManager.compressionIndicator;
			objOutputStream.writeByte(compressionIndicator);
			if (isCompressable) {
				objOutputStream.writeInt(compressedData.length);
				objOutputStream.write(compressedData);
			} else {
				objOutputStream.writeInt(serialBytes.length);
				objOutputStream.write(serialBytes);
			}

			// objOutputStream.writeObject(msg);

			writeCount++;
			if (writeCount == RESET_COUNT) {
				objOutputStream.reset();
				writeCount = 0;
			}
			objOutputStream.flush();
		} catch (IOException e) {
			throw new CommunicationException(e.getMessage());
		}
	}

	public void close() throws CommunicationException {
		try {
			objInputStream.close();
			objOutputStream.close();
			if (!socket.isClosed())
				socket.close();
		} catch (IOException e) {
			throw new CommunicationException(e.getMessage());
		}
	}

	public String toString() {
		return String.format("%s:%d", socket.getInetAddress().getHostAddress(), socket.getPort());
	}

}
