import java.net.*;
import java.io.*;
import java.util.BitSet;

public class Klient implements Runnable {

    private Serwer ser;
    private DatagramPacket pakiet = new DatagramPacket(new byte[256], 256);
    private DatagramSocket socket;

    Klient(DatagramSocket socket, Serwer ser) {
        this.socket = socket;
        this.ser = ser;
        try{
            this.socket.setSoTimeout(10);
        }
        catch(IOException e){
            System.err.println(e.getMessage());
        }
    }

    public void run() {
        while (ser.warunek){
            try{
                socket.receive(pakiet);
                if(pakiet.getLength() > 0){
                   ser.decode(pakiet);
                }
                if(Thread.currentThread().isInterrupted()){
                    return;
                }
            }
            catch (IOException e){
                if(e.getMessage().equals("Receive timed out"))
                    continue;
                System.err.println(e.getMessage());
            }
        }
    }

}
